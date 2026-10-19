package com.kharagedition.tibetandictionary.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Source of the current time, so date-dependent logic (word of the day, streaks) is testable. */
interface AppClock {
    fun now(): Instant
    fun today(): LocalDate
    fun zone(): ZoneId
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now(zone())
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
