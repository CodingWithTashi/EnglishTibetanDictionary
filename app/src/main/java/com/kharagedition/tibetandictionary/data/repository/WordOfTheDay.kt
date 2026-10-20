package com.kharagedition.tibetandictionary.data.repository

import java.time.LocalDate

/**
 * Word-of-the-day selection. The build pipeline gives each eligible entry of one direction (`wotd_direction_id`) a
 * `wotd_rank` from 0 until `wotd_count`, its index in a seeded shuffle; a date's rank is
 * `floorMod(epochDay + wotd_seed, wotd_count)`. The same date therefore always gives the same entry, and consecutive
 * dates walk the shuffle, so they differ (when there are at least two words).
 */
internal data class WotdParams(val directionId: String, val seed: Long, val count: Int) {

    fun dayIndex(date: LocalDate): Int = wotdDayIndex(date, seed, count)

    companion object {
        /** The parameters from `meta`; null unless `wotd_count` is a positive number and a direction is named. */
        fun from(meta: Map<String, String?>): WotdParams? {
            val count = meta[MetaKeys.WOTD_COUNT]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val directionId = meta[MetaKeys.WOTD_DIRECTION_ID]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return WotdParams(directionId, parseSeed(meta[MetaKeys.WOTD_SEED]), count)
        }
    }
}

/** `wotd_seed`; 0 when absent or not a number. */
internal fun parseSeed(value: String?): Long = value?.trim()?.toLongOrNull() ?: 0L

/** The rank (0 until [count]) of [date]: `floorMod(epochDay + seed, count)`. [count] must be positive. */
internal fun wotdDayIndex(date: LocalDate, seed: Long, count: Int): Int =
    (date.toEpochDay() + seed).mod(count.toLong()).toInt()

/**
 * The ranks after [dayIndex], wrapping after the last of [ranked] ranks, as `[from, to)` ranges for
 * `EntryDao.wotdRange`: at most [count] ranks and never more than [ranked] (so the day's own word only comes back when
 * every other rank is already included).
 */
internal fun followingRankRanges(dayIndex: Int, count: Int, ranked: Int): List<IntRange> {
    val n = minOf(count, ranked)
    if (n <= 0) return emptyList()
    val start = (dayIndex + 1).mod(ranked)
    val end = start + n
    return if (end <= ranked) listOf(start until end) else listOf(start until ranked, 0 until end - ranked)
}
