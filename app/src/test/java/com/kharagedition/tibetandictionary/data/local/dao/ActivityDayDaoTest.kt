package com.kharagedition.tibetandictionary.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.ActivityDayEntity
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityDayDaoTest {

    private lateinit var db: UserDatabase
    private lateinit var dao: ActivityDayDao

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        dao = db.activityDayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun incrementsCreateTheDayThenCount() = runTest {
        dao.incrementSearches("2026-10-19")
        dao.incrementSearches("2026-10-19")
        dao.incrementEntriesOpened("2026-10-19")
        dao.incrementEntriesOpened("2026-10-18")

        assertThat(dao.get("2026-10-19")).isEqualTo(ActivityDayEntity("2026-10-19", searches = 2, entriesOpened = 1))
        assertThat(dao.get("2026-10-18")).isEqualTo(ActivityDayEntity("2026-10-18", searches = 0, entriesOpened = 1))
        assertThat(dao.get("2026-10-17")).isNull()
    }

    @Test
    fun recentDatesNewestFirst() = runTest {
        listOf("2026-10-17", "2026-10-19", "2026-09-30", "2026-10-18").forEach { dao.incrementSearches(it) }

        assertThat(dao.observeRecentDates(limit = 10).first())
            .containsExactly("2026-10-19", "2026-10-18", "2026-10-17", "2026-09-30").inOrder()
        assertThat(dao.observeRecentDates(limit = 2).first()).containsExactly("2026-10-19", "2026-10-18").inOrder()
    }
}
