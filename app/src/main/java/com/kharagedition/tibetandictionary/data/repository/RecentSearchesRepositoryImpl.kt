package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [RecentSearchesRepository] over the user.db recent-searches DAO, which keeps the newest 50 rows. */
@Singleton
class RecentSearchesRepositoryImpl @Inject constructor(
    db: UserDatabase,
    private val clock: AppClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RecentSearchesRepository {
    private val dao = db.recentSearchDao()

    override fun observe(limit: Int): Flow<List<RecentSearch>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    /** Blank and one-character queries (after trimming) are not worth remembering and are ignored. */
    override suspend fun record(query: String, directionId: String, opened: EntrySummary?) {
        val trimmed = query.trim()
        if (trimmed.codePointCount(0, trimmed.length) < MIN_QUERY_CHARS) return
        withContext(io) {
            dao.record(
                RecentSearchEntity(
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
        withContext(io) { dao.clear() }
    }

    private companion object {
        const val MIN_QUERY_CHARS = 2
    }
}
