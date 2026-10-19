package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.Entry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The word of the day: the same entry for the same date; null when the data has no eligible words. */
interface WordOfTheDayRepository {
    fun observe(date: LocalDate): Flow<Entry?>

    suspend fun forDate(date: LocalDate): Entry?
}
