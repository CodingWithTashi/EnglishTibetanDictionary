package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stub so the Hilt graph compiles; L1 (T1.7) replaces the bodies. */
@Singleton
class RecentSearchesRepositoryImpl @Inject constructor() : RecentSearchesRepository {
    override fun observe(limit: Int): Flow<List<RecentSearch>> = TODO("L1")

    override suspend fun record(query: String, directionId: String, opened: EntrySummary?): Unit = TODO("L1")

    override suspend fun remove(id: Long): Unit = TODO("L1")

    override suspend fun clear(): Unit = TODO("L1")
}
