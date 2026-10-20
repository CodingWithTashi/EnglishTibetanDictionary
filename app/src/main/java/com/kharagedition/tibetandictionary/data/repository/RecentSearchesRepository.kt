package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import kotlinx.coroutines.flow.Flow

/** Recent searches (user.db), newest first, one row per query and direction. */
interface RecentSearchesRepository {
    fun observe(limit: Int = 10): Flow<List<RecentSearch>>

    /** Records [query] (bumping it to the top if present); [opened] is the result the user opened, if any. */
    suspend fun record(query: String, directionId: String, opened: EntrySummary? = null)

    suspend fun remove(id: Long)

    suspend fun clear()
}
