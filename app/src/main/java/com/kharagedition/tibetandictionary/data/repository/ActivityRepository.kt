package com.kharagedition.tibetandictionary.data.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Per-day usage (user.db) that feeds the streak. */
interface ActivityRepository {
    /** Counts a search for today. */
    suspend fun markSearch()

    /** Counts an opened entry for today. */
    suspend fun markEntryOpened()

    /** Days with any activity, newest first, at most [limit]. */
    fun observeActiveDays(limit: Int = 60): Flow<List<LocalDate>>
}
