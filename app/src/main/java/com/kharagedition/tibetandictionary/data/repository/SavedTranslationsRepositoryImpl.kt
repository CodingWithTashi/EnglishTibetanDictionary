package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.SavedTranslationEntity
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [SavedTranslationsRepository] over the user.db translation DAO, scoped to the active pack. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SavedTranslationsRepositoryImpl @Inject constructor(
    db: UserDatabase,
    private val databases: DictionaryDatabases,
    private val clock: AppClock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : SavedTranslationsRepository {
    private val dao = db.translationDao()

    /** The active pack's id; a write before the first activation is a programming error. */
    private val packId: String get() = checkNotNull(databases.active.value) { "No active dictionary" }.id

    override fun observe(): Flow<List<TranslationRecord>> =
        databases.active.filterNotNull()
            .flatMapLatest { dao.observeSaved(it.id) }
            .map { rows -> rows.mapNotNull { it.toRecord() } }
            .flowOn(io)

    override fun observeIsSaved(pair: LangPair, source: String): Flow<Boolean> =
        databases.active.filterNotNull()
            .flatMapLatest { dao.observeIsSaved(it.id, pair.source.code, pair.target.code, source) }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun save(pair: LangPair, source: String, text: TranslatedText): Long = withContext(io) {
        dao.save(
            SavedTranslationEntity(
                packId = packId,
                sourceLang = pair.source.code,
                targetLang = pair.target.code,
                sourceText = source,
                translation = text.translation,
                provider = text.provider.code,
                savedAt = clock.now().toEpochMilli(),
            ),
        )
    }

    override suspend fun remove(id: Long): TranslationRecord? = withContext(io) { dao.removeSaved(id)?.toRecord() }

    override suspend fun restore(record: TranslationRecord) {
        withContext(io) {
            dao.insertSaved(
                SavedTranslationEntity(
                    id = record.id,
                    packId = packId,
                    sourceLang = record.pair.source.code,
                    targetLang = record.pair.target.code,
                    sourceText = record.source,
                    translation = record.translation,
                    provider = record.provider.code,
                    savedAt = record.at.toEpochMilli(),
                ),
            )
        }
    }
}

/** Null for a row whose language codes this version doesn't know. */
private fun SavedTranslationEntity.toRecord(): TranslationRecord? {
    val pair = LangPair(Lang.fromCode(sourceLang) ?: return null, Lang.fromCode(targetLang) ?: return null)
    val at = Instant.ofEpochMilli(savedAt)
    return TranslationRecord(id, pair, sourceText, translation, Provider.fromCode(provider), at)
}
