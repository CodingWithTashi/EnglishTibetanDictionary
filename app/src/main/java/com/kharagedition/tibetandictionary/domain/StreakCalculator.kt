package com.kharagedition.tibetandictionary.domain

import java.time.LocalDate

/** Counts the user's run of consecutive active days for Home's streak pill. */
object StreakCalculator {

    /**
     * Length of the run of consecutive days in [activeDays] that ends **today or yesterday** ([today] − 1), so a
     * streak survives until the user has had a whole day to keep it. A run ending earlier is broken (0). Days after
     * [today] are ignored.
     */
    fun streak(activeDays: Set<LocalDate>, today: LocalDate): Int {
        val yesterday = today.minusDays(1)
        var day = when {
            today in activeDays -> today
            yesterday in activeDays -> yesterday
            else -> return 0
        }
        var count = 0
        while (day in activeDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
