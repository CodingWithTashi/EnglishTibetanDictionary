package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.domain.TimeOfDay.AFTERNOON
import com.kharagedition.tibetandictionary.domain.TimeOfDay.EVENING
import com.kharagedition.tibetandictionary.domain.TimeOfDay.MORNING
import com.kharagedition.tibetandictionary.fakes.FakeClock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

class GreetingTest {

    @Test
    fun pinnedHours() {
        assertThat(Greeting.timeOfDay(9)).isEqualTo(MORNING)
        assertThat(Greeting.timeOfDay(14)).isEqualTo(AFTERNOON)
        assertThat(Greeting.timeOfDay(20)).isEqualTo(EVENING)
        assertThat(Greeting.timeOfDay(2)).isEqualTo(EVENING)
    }

    @Test
    fun everyHourOfTheDay() {
        val expected = (0..23).associateWith { hour ->
            when (hour) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                else -> EVENING
            }
        }

        expected.forEach { (hour, timeOfDay) ->
            assertWithMessage("hour %s", hour).that(Greeting.timeOfDay(hour)).isEqualTo(timeOfDay)
        }
        // Boundaries spelled out.
        assertThat(listOf(4, 5, 11, 12, 16, 17, 23, 0).map(Greeting::timeOfDay))
            .containsExactly(EVENING, MORNING, MORNING, AFTERNOON, AFTERNOON, EVENING, EVENING, EVENING)
            .inOrder()
    }

    @Test
    fun hoursOutsideTheDayAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Greeting.timeOfDay(24) }
        assertThrows(IllegalArgumentException::class.java) { Greeting.timeOfDay(-1) }
    }

    @Test
    fun clockUsesTheLocalHourOfItsZone() {
        val clock = FakeClock(instant = Instant.parse("2026-10-19T09:00:00Z"), zone = ZoneId.of("UTC"))
        assertThat(Greeting.timeOfDay(clock)).isEqualTo(MORNING)

        clock.zone = ZoneId.of("Asia/Kolkata") // 14:30
        assertThat(Greeting.timeOfDay(clock)).isEqualTo(AFTERNOON)

        clock.zone = ZoneId.of("America/Los_Angeles") // 02:00
        assertThat(Greeting.timeOfDay(clock)).isEqualTo(EVENING)
    }
}
