package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 10, 19)

    /** Days relative to [today]: 0 = today, −1 = yesterday. */
    private fun days(vararg offsets: Long): Set<LocalDate> = offsets.map { today.plusDays(it) }.toSet()

    private fun streak(vararg offsets: Long) = StreakCalculator.streak(days(*offsets), today)

    // Pinned cases (§7.5)

    @Test
    fun runEndingTodayCountsToday() {
        assertThat(streak(0, -1, -2)).isEqualTo(3)
    }

    @Test
    fun runEndingYesterdayStillCounts() {
        assertThat(streak(-1, -2)).isEqualTo(2)
    }

    @Test
    fun runEndingBeforeYesterdayIsBroken() {
        assertThat(streak(-2)).isEqualTo(0)
    }

    // More

    @Test
    fun noActiveDaysIsZero() {
        assertThat(streak()).isEqualTo(0)
    }

    @Test
    fun onlyTodayIsOne() {
        assertThat(streak(0)).isEqualTo(1)
    }

    @Test
    fun aGapEndsTheRun() {
        assertThat(streak(0, -2, -3, -4)).isEqualTo(1)
        assertThat(streak(-1, -2, -4, -5, -6)).isEqualTo(2)
    }

    @Test
    fun futureDaysAreIgnored() {
        assertThat(streak(1, 0, -1)).isEqualTo(2)
        assertThat(streak(1, 2)).isEqualTo(0)
    }

    @Test
    fun runsCrossMonthAndYearBoundaries() {
        val newYear = LocalDate.of(2027, 1, 1)
        val active = (0L..40L).map { newYear.minusDays(it) }.toSet()

        assertThat(StreakCalculator.streak(active, newYear)).isEqualTo(41)
    }
}
