package com.kharagedition.tibetandictionary.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentSearchDaoTest {

    private lateinit var db: UserDatabase
    private lateinit var dao: RecentSearchDao

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        dao = db.recentSearchDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordUpsertsAndTrims() = runTest {
        dao.record(recent("bkra", "bo-en", at = 1))
        val firstId = dao.observeRecent("bo", limit = 10).first().single().id

        dao.record(recent("bkra", "bo-en", at = 2))
        dao.record(recent("bkra", "en-bo", at = 3))

        val afterUpsert = dao.observeRecent("bo", limit = 10).first()
        assertThat(afterUpsert.map { it.query to it.directionId })
            .containsExactly("bkra" to "en-bo", "bkra" to "bo-en").inOrder()
        assertThat(afterUpsert.single { it.directionId == "bo-en" }.searchedAt).isEqualTo(2L)
        assertThat(afterUpsert.single { it.directionId == "bo-en" }.id).isEqualTo(firstId)

        (1..60).forEach { n -> dao.record(recent("q$n", "bo-en", at = 100L + n)) }

        val kept = dao.observeRecent("bo", limit = 100).first()
        assertThat(kept).hasSize(50)
        assertThat(kept.first().query).isEqualTo("q60")
        assertThat(kept.last().query).isEqualTo("q11")
        assertThat(dao.observeRecent("bo", limit = 3).first().map { it.query }).containsExactly("q60", "q59", "q58").inOrder()

        dao.record(recent("x", "bo-en", at = 1_000), keep = 5)
        assertThat(dao.observeRecent("bo", limit = 100).first().map { it.query })
            .containsExactly("x", "q60", "q59", "q58", "q57").inOrder()
    }

    @Test
    fun recordKeepsTheOpenedEntryUntilANewOneIsOpened() = runTest {
        dao.record(recent("bkra", "bo-en", at = 1, entryId = 1_000_010, headword = "བཀྲ་ཤིས་"))

        dao.record(recent("bkra", "bo-en", at = 2))
        val kept = dao.observeRecent("bo", limit = 10).first().single()

        dao.record(recent("bkra", "bo-en", at = 3, entryId = 1_000_020, headword = "བཀྲ་"))
        val replaced = dao.observeRecent("bo", limit = 10).first().single()

        assertThat(kept.entryId).isEqualTo(1_000_010L)
        assertThat(kept.headword).isEqualTo("བཀྲ་ཤིས་")
        assertThat(kept.searchedAt).isEqualTo(2L)
        assertThat(replaced.entryId).isEqualTo(1_000_020L)
        assertThat(replaced.headword).isEqualTo("བཀྲ་")
    }

    @Test
    fun deleteAndClear() = runTest {
        dao.record(recent("a", "bo-en", at = 1))
        dao.record(recent("b", "bo-en", at = 2))
        val a = dao.observeRecent("bo", limit = 10).first().single { it.query == "a" }

        dao.delete(a.id)
        assertThat(dao.observeRecent("bo", limit = 10).first().map { it.query }).containsExactly("b")

        dao.clear("bo")
        assertThat(dao.observeRecent("bo", limit = 10).first()).isEmpty()
    }

    @Test
    fun observeRecentIsScopedToPack() = runTest {
        dao.record(recent("bkra", "bo-en", at = 1))
        dao.record(recent("tashi", "dz-en", at = 2, packId = "dz"))

        assertThat(dao.observeRecent("bo", limit = 10).first().map { it.query }).containsExactly("bkra")
        assertThat(dao.observeRecent("dz", limit = 10).first().map { it.query }).containsExactly("tashi")
    }

    @Test
    fun trimKeepsOtherPacksRows() = runTest {
        dao.record(recent("tashi", "dz-en", at = 0, packId = "dz"))

        (1..51).forEach { n -> dao.record(recent("q$n", "bo-en", at = n.toLong())) }

        val bo = dao.observeRecent("bo", limit = 100).first()
        assertThat(bo).hasSize(50)
        assertThat(bo.last().query).isEqualTo("q2")
        assertThat(dao.observeRecent("dz", limit = 100).first().map { it.query }).containsExactly("tashi")
    }

    @Test
    fun clearIsScopedToPack() = runTest {
        dao.record(recent("bkra", "bo-en", at = 1))
        dao.record(recent("tashi", "dz-en", at = 2, packId = "dz"))

        dao.clear("dz")

        assertThat(dao.observeRecent("dz", limit = 10).first()).isEmpty()
        assertThat(dao.observeRecent("bo", limit = 10).first().map { it.query }).containsExactly("bkra")
    }

    @Test
    fun sameQueryAndDirectionInBothPacksAreTwoRows() = runTest {
        dao.record(recent("bkra", "bo-en", at = 1, entryId = 1_000_010, headword = "བཀྲ་ཤིས་"))

        dao.record(recent("bkra", "bo-en", at = 2, packId = "dz"))

        val bo = dao.observeRecent("bo", limit = 10).first().single()
        val dz = dao.observeRecent("dz", limit = 10).first().single()
        assertThat(dz.id).isNotEqualTo(bo.id)
        assertThat(bo.searchedAt).isEqualTo(1L)
        assertThat(bo.entryId).isEqualTo(1_000_010L)
        assertThat(dz.searchedAt).isEqualTo(2L)
        assertThat(dz.entryId).isNull()
    }

    private fun recent(
        query: String,
        directionId: String,
        at: Long,
        entryId: Long? = null,
        headword: String? = null,
        packId: String = "bo",
    ) = RecentSearchEntity(
        packId = packId,
        query = query,
        directionId = directionId,
        entryId = entryId,
        headword = headword,
        searchedAt = at,
    )
}
