package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.core.time.AppClock

/** Which greeting Home shows for the time of day. */
object Greeting {

    private val MorningHours = 5..11
    private val AfternoonHours = 12..16
    private val DayHours = 0..23

    /**
     * [TimeOfDay] for a local [hour] (0–23): **morning** 05:00–11:59, **afternoon** 12:00–16:59, **evening**
     * 17:00–04:59 (late night and small hours included).
     *
     * @throws IllegalArgumentException when [hour] is outside 0–23.
     */
    fun timeOfDay(hour: Int): TimeOfDay {
        require(hour in DayHours) { "hour must be in 0..23, was $hour" }
        return when (hour) {
            in MorningHours -> TimeOfDay.MORNING
            in AfternoonHours -> TimeOfDay.AFTERNOON
            else -> TimeOfDay.EVENING
        }
    }

    /** [TimeOfDay] for the current hour of [clock] in its zone. */
    fun timeOfDay(clock: AppClock): TimeOfDay = timeOfDay(clock.now().atZone(clock.zone()).hour)
}
