package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.RecentTranslationEntity
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.data.translation.pack
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [TranslationHistoryRepository] over the user.db translation DAO, which keeps the newest 20 rows per pack. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TranslationHistoryRepositoryImpl @Inject constructor(
    db: UserDatabase,
    private val databases: DictionaryDatabases,
    private val clock: AppClock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : TranslationHistoryRepository {
    private val dao = db.translationDao()

    /** The active pack's id; a write before the first activation is a programming error. */
    private val packId: String get() = checkNotNull(databases.active.value) { "No active dictionary" }.id

    override fun observe(limit: Int): Flow<List<TranslationRecord>> =
        databases.active.filterNotNull()
            .flatMapLatest { dao.observeRecent(it.id, limit) }
            .map { rows -> rows.mapNotNull { it.toRecord() } }
            .flowOn(io)

    override suspend fun record(pair: LangPair, source: String, text: TranslatedText) {
        withContext(io) {
            dao.recordRecent(
                RecentTranslationEntity(
                    packId = pair.pack()?.id ?: packId,
                    sourceLang = pair.source.code,
                    targetLang = pair.target.code,
                    sourceText = source,
                    translation = text.translation,
                    provider = text.provider.code,
                    usedAt = clock.now().toEpochMilli(),
                ),
            )
        }
    }

    override suspend fun clear() {
        withContext(io) { dao.clearRecent(packId) }
    }
}

/** Null for a row whose language codes this version doesn't know. */
private fun RecentTranslationEntity.toRecord(): TranslationRecord? {
    val pair = LangPair(Lang.fromCode(sourceLang) ?: return null, Lang.fromCode(targetLang) ?: return null)
    val at = Instant.ofEpochMilli(usedAt)
    return TranslationRecord(id, pair, sourceText, translation, Provider.fromCode(provider), at)
}
