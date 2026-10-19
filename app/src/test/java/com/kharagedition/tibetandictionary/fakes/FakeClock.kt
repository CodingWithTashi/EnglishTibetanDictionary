package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** [AppClock] fixed at [instant] in [zone]; tests move it by assigning [instant]. */
class FakeClock(
    var instant: Instant = Instant.parse("2026-10-19T09:00:00Z"),
    var zone: ZoneId = ZoneOffset.UTC,
) : AppClock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = instant.atZone(zone).toLocalDate()
    override fun zone(): ZoneId = zone
}
