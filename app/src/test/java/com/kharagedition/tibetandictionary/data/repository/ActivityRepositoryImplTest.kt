package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.ActivityDayEntity
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import com.kharagedition.tibetandictionary.fakes.FakeClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityRepositoryImplTest {

    private val clock = FakeClock(instant = Instant.parse("2026-10-19T09:00:00Z"))
    private lateinit var db: UserDatabase
    private lateinit var repo: ActivityRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        repo = ActivityRepositoryImpl(db, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markSearchAndMarkEntryOpenedIncrementTodaysRow() = runTest {
        repo.markSearch()
        repo.markSearch()
        repo.markEntryOpened()

        assertThat(db.activityDayDao().get("2026-10-19"))
            .isEqualTo(ActivityDayEntity(date = "2026-10-19", searches = 2, entriesOpened = 1))
    }

    @Test
    fun observeActiveDaysReturnsToday() = runTest {
        repo.observeActiveDays().test {
            assertThat(awaitItem()).isEmpty()

            repo.markSearch()
            assertThat(awaitItem()).containsExactly(LocalDate.of(2026, 10, 19))
        }
    }

    @Test
    fun anOpenedEntryAloneMakesTheDayActive() = runTest {
        repo.markEntryOpened()

        assertThat(repo.observeActiveDays().first()).containsExactly(LocalDate.of(2026, 10, 19))
    }

    @Test
    fun twoDaysAreNewestFirst() = runTest {
        repo.markSearch()
        clock.instant = clock.instant.plus(Duration.ofDays(1))
        repo.markEntryOpened()

        assertThat(repo.observeActiveDays().first())
            .containsExactly(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 19)).inOrder()
        assertThat(db.activityDayDao().get("2026-10-19")?.searches).isEqualTo(1)
        assertThat(db.activityDayDao().get("2026-10-20")?.entriesOpened).isEqualTo(1)
    }

    @Test
    fun theLimitIsRespected() = runTest {
        repeat(5) {
            repo.markSearch()
            clock.instant = clock.instant.plus(Duration.ofDays(1))
        }

        assertThat(repo.observeActiveDays(limit = 3).first())
            .containsExactly(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 21)).inOrder()
        assertThat(repo.observeActiveDays().first()).hasSize(5)
    }

    @Test
    fun theDayIsTheClocksLocalDate() = runTest {
        // 20:00 UTC on 19 Oct is 01:45 on 20 Oct in Kathmandu (UTC+5:45).
        clock.zone = ZoneId.of("Asia/Kathmandu")
        clock.instant = Instant.parse("2026-10-19T20:00:00Z")

        repo.markSearch()

        assertThat(db.activityDayDao().get("2026-10-20")?.searches).isEqualTo(1)
        assertThat(db.activityDayDao().get("2026-10-19")).isNull()
        assertThat(repo.observeActiveDays().first()).containsExactly(LocalDate.of(2026, 10, 20))
    }
}
