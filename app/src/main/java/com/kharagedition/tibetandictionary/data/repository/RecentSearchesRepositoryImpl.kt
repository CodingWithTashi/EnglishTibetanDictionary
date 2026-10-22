package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [RecentSearchesRepository] over the user.db recent-searches DAO, which keeps the newest 50 rows per pack. */
@Singleton
class RecentSearchesRepositoryImpl @Inject constructor(
    db: UserDatabase,
    private val databases: DictionaryDatabases,
    private val clock: AppClock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : RecentSearchesRepository {
    private val dao = db.recentSearchDao()

    /** The active pack's id; a write before the first activation is a programming error. */
    private val packId: String get() = checkNotNull(databases.active.value) { "No active dictionary" }.id

    /** The active pack's recent searches; re-queried on a pack switch, silent while no pack is active. */
    override fun observe(limit: Int): Flow<List<RecentSearch>> =
        databases.active.filterNotNull()
            .flatMapLatest { dao.observeRecent(it.id, limit) }
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(io)

    /** Blank and one-character queries (after trimming) are not worth remembering and are ignored. */
    override suspend fun record(query: String, directionId: String, opened: EntrySummary?) {
        val trimmed = query.trim()
        if (trimmed.codePointCount(0, trimmed.length) < MIN_QUERY_CHARS) return
        withContext(io) {
            dao.record(
                RecentSearchEntity(
                    packId = packId,
                    query = trimmed,
                    directionId = directionId,
                    entryId = opened?.id,
                    headword = opened?.headword,
                    searchedAt = clock.now().toEpochMilli(),
                ),
            )
        }
    }

    override suspend fun remove(id: Long) {
        withContext(io) { dao.delete(id) }
    }

    override suspend fun clear() {
        withContext(io) { dao.clear(packId) }
    }

    private companion object {
        const val MIN_QUERY_CHARS = 2
    }
}
