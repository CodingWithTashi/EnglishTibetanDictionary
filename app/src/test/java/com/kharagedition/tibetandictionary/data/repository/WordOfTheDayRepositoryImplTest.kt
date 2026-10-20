package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.MiniDictionary.BO_EN
import com.kharagedition.tibetandictionary.data.MiniDictionary.KAWA
import com.kharagedition.tibetandictionary.data.MiniDictionary.TASHI
import com.kharagedition.tibetandictionary.data.MiniDictionary.TASHI_DELEK
import com.kharagedition.tibetandictionary.data.MiniDictionary.TRA
import com.kharagedition.tibetandictionary.data.MiniDictionary.TRAG
import com.kharagedition.tibetandictionary.data.TestDb
import com.kharagedition.tibetandictionary.data.TestDb.directionRow
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.entry
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import java.time.LocalDate
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordOfTheDayRepositoryImplTest {

    private val date = LocalDate.of(2026, 10, 19)
    private val seed = 20_261_019L

    /** The mini dictionary's bo-en entries by word-of-the-day rank. */
    private val byRank = listOf(TRA, TASHI_DELEK, TASHI, TRAG, KAWA)

    private lateinit var db: DictionaryDatabase

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private fun TestScope.repository() =
        WordOfTheDayRepositoryImpl(db.entryDao(), db.metaDao(), StandardTestDispatcher(testScheduler))

    private fun expectedId(day: LocalDate): Long =
        byRank[Math.floorMod(day.toEpochDay() + seed, TestDb.WOTD_COUNT.toLong()).toInt()]

    @Test
    fun sameDateSameEntryAcrossInstances() = runTest {
        db = TestDb.miniDictionary(wotdSeed = seed)

        val first = repository().forDate(date)
        val second = repository().forDate(date)

        assertThat(first).isNotNull()
        assertThat(second).isEqualTo(first)
        assertThat(first?.summary?.id).isEqualTo(expectedId(date))
        assertThat(first?.summary?.directionId).isEqualTo(BO_EN)
        // A full entry, senses included.
        assertThat(first?.senses).isNotEmpty()
    }

    @Test
    fun consecutiveDatesDiffer() = runTest {
        db = TestDb.miniDictionary(wotdSeed = seed)
        val repo = repository()

        val week = (0L..5L).map { checkNotNull(repo.forDate(date.plusDays(it))).summary.id }

        assertThat(week[1]).isNotEqualTo(week[0])
        // Consecutive days walk the shuffled ranks: every eligible word once, then around again.
        assertThat(week.take(TestDb.WOTD_COUNT)).containsExactlyElementsIn(byRank)
        assertThat(week[5]).isEqualTo(week[0])
        assertThat(week).isEqualTo((0L..5L).map { expectedId(date.plusDays(it)) })
    }

    @Test
    fun observeEmitsTheDaysEntry() = runTest {
        db = TestDb.miniDictionary(wotdSeed = seed)
        val repo = repository()

        repo.observe(date).test {
            assertThat(awaitItem()?.summary?.id).isEqualTo(expectedId(date))
            cancelAndIgnoreRemainingEvents()
        }
        repo.observe(date.plusDays(1)).test {
            assertThat(awaitItem()?.summary?.id).isEqualTo(expectedId(date.plusDays(1)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noWordWhenCountIsZero() = runTest {
        db = TestDb.miniDictionary(wotdSeed = seed, wotdCount = 0)
        val repo = repository()

        assertThat(repo.forDate(date)).isNull()
        repo.observe(date).test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noWordWithoutMeta() = runTest {
        db = inMemoryDictionaryDb()
        db.testWriteDao().insertDirections(listOf(directionRow(BO_EN, 0, "tibt", entryCount = 1, wotdCount = 1)))
        db.testWriteDao().insertEntries(listOf(entry(TRA, BO_EN, "བཀྲ་", 10, wotdRank = 0)))
        val repo = repository()

        assertThat(repo.forDate(date)).isNull()
        repo.observe(date).test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
