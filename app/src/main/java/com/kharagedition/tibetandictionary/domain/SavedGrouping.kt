package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.model.SavedEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** When a word was saved, relative to today: the section headers of the Saved list. */
enum class SavedPeriod { TODAY, THIS_WEEK, THIS_MONTH, EARLIER }

/** The saved words of one [period], newest first. */
data class SavedGroup(val period: SavedPeriod, val items: List<SavedEntry>)

/**
 * Sorts saved words into the Saved list's sections by the day they were saved in the user's zone: today (a date
 * after today, from a clock change, counts as today), this week (1–7 days ago), this month (earlier in today's
 * calendar month) and earlier. Sections come in that order, empty ones are left out, and each lists its words
 * newest first.
 */
object SavedGrouping {
    private const val WEEK_DAYS = 7L

    fun group(items: List<SavedEntry>, today: LocalDate, zone: ZoneId): List<SavedGroup> {
        val byPeriod = items
            .sortedByDescending { it.savedAt }
            .groupBy { periodOf(Instant.ofEpochMilli(it.savedAt).atZone(zone).toLocalDate(), today) }
        return SavedPeriod.entries.mapNotNull { period -> byPeriod[period]?.let { SavedGroup(period, it) } }
    }

    private fun periodOf(day: LocalDate, today: LocalDate): SavedPeriod {
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            daysAgo <= 0 -> SavedPeriod.TODAY
            daysAgo <= WEEK_DAYS -> SavedPeriod.THIS_WEEK
            YearMonth.from(day) == YearMonth.from(today) -> SavedPeriod.THIS_MONTH
            else -> SavedPeriod.EARLIER
        }
    }
}
