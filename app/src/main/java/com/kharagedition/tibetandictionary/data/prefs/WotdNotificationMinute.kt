package com.kharagedition.tibetandictionary.data.prefs

/** Single home of the word-of-the-day notification time: minutes after local midnight. */
object WotdNotificationMinute {
    const val MIN = 0
    const val MAX = 24 * 60 - 1

    /** 09:00. */
    const val DEFAULT = 9 * 60

    fun clamp(minuteOfDay: Int): Int = minuteOfDay.coerceIn(MIN, MAX)
}
