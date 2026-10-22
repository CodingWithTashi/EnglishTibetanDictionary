package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.databasesOf
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import com.kharagedition.tibetandictionary.fakes.FakeClock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentSearchesRepositoryImplTest {

    private val clock = FakeClock()
    private val databases = databasesOf()
    private lateinit var db: UserDatabase
    private lateinit var repo: RecentSearchesRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        databases.activate(DictionaryPack.TIBETAN)
        repo = RecentSearchesRepositoryImpl(db, databases, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordStoresTheQueryDirectionAndTime() = runTest {
        repo.record("bkra shis", "bo-en")

        val row = repo.observe().first().single()
        assertThat(row.copy(id = 0)).isEqualTo(
            RecentSearch(
                id = 0,
                query = "bkra shis",
                directionId = "bo-en",
                entryId = null,
                headword = null,
                searchedAt = clock.now().toEpochMilli(),
            ),
        )
    }

    @Test
    fun theSameQueryAndDirectionIsUpsertedKeepingItsId() = runTest {
        repo.record("bkra", "bo-en")
        val first = repo.observe().first().single()
        tick()

        repo.record("bkra", "bo-en")

        val after = repo.observe().first().single()
        assertThat(after.id).isEqualTo(first.id)
        assertThat(after.searchedAt).isGreaterThan(first.searchedAt)
        assertThat(after.searchedAt).isEqualTo(clock.now().toEpochMilli())
    }

    @Test
    fun theSameQueryInAnotherDirectionIsAnotherRow() = runTest {
        repo.record("bkra", "bo-en")
        tick()
        repo.record("bkra", "en-bo")

        assertThat(repo.observe().first().map { it.directionId }).containsExactly("en-bo", "bo-en").inOrder()
    }

    @Test
    fun recordingAnOlderQueryAgainBumpsItToTheTop() = runTest {
        repo.record("house", "en-bo")
        tick()
        repo.record("bkra", "bo-en")
        tick()

        repo.record("house", "en-bo")

        assertThat(repo.observe().first().map { it.query }).containsExactly("house", "bkra").inOrder()
    }

    @Test
    fun blankAndOneCharacterQueriesAreIgnored() = runTest {
        repo.record("", "bo-en")
        repo.record("   ", "bo-en")
        repo.record("a", "en-bo")
        repo.record("  a  ", "en-bo")
        repo.record("ཀ", "bo-en")
        repo.record(" ཀ ", "bo-en")

        assertThat(repo.observe().first()).isEmpty()
    }

    @Test
    fun twoCharacterQueriesAreRecordedTrimmed() = runTest {
        repo.record("ཀ་", "bo-en")
        repo.record("  ab  ", "en-bo")

        assertThat(repo.observe().first().map { it.query }).containsExactly("ab", "ཀ་")
    }

    @Test
    fun aQueryWithOuterSpacesUpsertsTheTrimmedRow() = runTest {
        repo.record("house", "en-bo")
        tick()

        repo.record("  house ", "en-bo")

        assertThat(repo.observe().first().map { it.query }).containsExactly("house")
    }

    @Test
    fun onlyTheNewestFiftyAreKept() = runTest {
        (1..55).forEach { n ->
            repo.record("query %02d".format(n), "bo-en")
            tick()
        }

        val kept = repo.observe(limit = 100).first()

        assertThat(kept).hasSize(50)
        assertThat(kept.first().query).isEqualTo("query 55")
        assertThat(kept.last().query).isEqualTo("query 06")
    }

    @Test
    fun observeReturnsTheNewestRowsUpToTheLimit() = runTest {
        listOf("one one", "two two", "three three", "four four").forEach {
            repo.record(it, "en-bo")
            tick()
        }

        assertThat(repo.observe(limit = 2).first().map { it.query }).containsExactly("four four", "three three").inOrder()
        assertThat(repo.observe().first()).hasSize(4)
    }

    @Test
    fun openedEntryIsStoredWithTheSearch() = runTest {
        repo.record("bkra", "bo-en", opened = SampleData.tashi)

        val row = repo.observe().first().single()
        assertThat(row.entryId).isEqualTo(SampleData.tashi.id)
        assertThat(row.headword).isEqualTo(SampleData.tashi.headword)
    }

    @Test
    fun aLaterRecordWithoutAnOpenedEntryKeepsThePreviousOne() = runTest {
        repo.record("bkra", "bo-en", opened = SampleData.tashi)
        tick()

        repo.record("bkra", "bo-en")

        val row = repo.observe().first().single()
        assertThat(row.entryId).isEqualTo(SampleData.tashi.id)
        assertThat(row.headword).isEqualTo(SampleData.tashi.headword)
        assertThat(row.searchedAt).isEqualTo(clock.now().toEpochMilli())
    }

    @Test
    fun aLaterOpenedEntryReplacesThePreviousOne() = runTest {
        repo.record("bkra", "bo-en", opened = SampleData.tashi)
        tick()

        repo.record("bkra", "bo-en", opened = SampleData.tashiDelek)

        val row = repo.observe().first().single()
        assertThat(row.entryId).isEqualTo(SampleData.tashiDelek.id)
        assertThat(row.headword).isEqualTo(SampleData.tashiDelek.headword)
    }

    @Test
    fun removeDeletesOneRow() = runTest {
        repo.record("bkra", "bo-en")
        tick()
        repo.record("house", "en-bo")
        val bkra = repo.observe().first().single { it.query == "bkra" }

        repo.remove(bkra.id)

        assertThat(repo.observe().first().map { it.query }).containsExactly("house")
    }

    @Test
    fun clearDeletesEveryRow() = runTest {
        repo.record("bkra", "bo-en")
        repo.record("house", "en-bo")

        repo.clear()

        assertThat(repo.observe().first()).isEmpty()
    }

    @Test
    fun observeFollowsLaterChanges() = runTest {
        repo.observe().test {
            assertThat(awaitItem()).isEmpty()

            repo.record("bkra", "bo-en")
            assertThat(awaitItem().map { it.query }).containsExactly("bkra")

            repo.clear()
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun recentsFollowTheActivePack() = runTest {
        repo.record("bkra", "bo-en")

        repo.observe().test {
            assertThat(awaitItem().map { it.query }).containsExactly("bkra")
            databases.activate(DictionaryPack.DZONGKHA)
            assertThat(awaitItem()).isEmpty()
            repo.record("dzong", "dz-en")
            assertThat(awaitItem().map { it.query }).containsExactly("dzong")
            databases.activate(DictionaryPack.TIBETAN)
            assertThat(awaitItem().map { it.query }).containsExactly("bkra")
        }
    }

    @Test
    fun clearOnlyClearsActivePack() = runTest {
        repo.record("bkra", "bo-en")
        databases.activate(DictionaryPack.DZONGKHA)
        repo.record("dzong", "dz-en")

        repo.clear()

        assertThat(repo.observe().first()).isEmpty()
        databases.activate(DictionaryPack.TIBETAN)
        assertThat(repo.observe().first().map { it.query }).containsExactly("bkra")
    }

    private fun tick() {
        clock.instant = clock.instant.plus(Duration.ofSeconds(1))
    }
}
