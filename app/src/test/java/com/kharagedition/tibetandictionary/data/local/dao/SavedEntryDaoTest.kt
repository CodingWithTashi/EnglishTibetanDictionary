package com.kharagedition.tibetandictionary.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedEntryDaoTest {

    private lateinit var db: UserDatabase
    private lateinit var dao: SavedEntryDao

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        dao = db.savedEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertObserveAndDelete() = runTest {
        dao.observeIsSaved(1_000_010).test {
            assertThat(awaitItem()).isFalse()

            dao.insert(saved(1_000_010, "བཀྲ་ཤིས་", savedAt = 100))
            assertThat(awaitItem()).isTrue()

            dao.delete(1_000_010)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun insertIgnoresAnAlreadySavedEntry() = runTest {
        dao.insert(saved(1_000_010, "བཀྲ་ཤིས་", savedAt = 100))
        dao.setNote(1_000_010, "my note", now = 150)

        val rowId = dao.insert(saved(1_000_010, "བཀྲ་ཤིས་", savedAt = 200))

        assertThat(rowId).isEqualTo(-1L)
        assertThat(dao.get(1_000_010)?.note).isEqualTo("my note")
        assertThat(dao.get(1_000_010)?.savedAt).isEqualTo(100L)
        assertThat(dao.get(1_000_010)?.updatedAt).isEqualTo(150L)
        assertThat(dao.observeCount().first()).isEqualTo(1)
    }

    @Test
    fun listsAreNewestFirstAndCountsFollow() = runTest {
        dao.insert(saved(1, "a", savedAt = 100))
        dao.insert(saved(2, "b", savedAt = 300))
        dao.insert(saved(3, "c", savedAt = 200))

        assertThat(dao.observeAll().first().map { it.entryId }).containsExactly(2L, 3L, 1L).inOrder()
        assertThat(dao.observeIds().first()).containsExactly(1L, 2L, 3L)
        assertThat(dao.observeCount().first()).isEqualTo(3)
    }

    @Test
    fun setFolderMovesBetweenFolderAndUnfiled() = runTest {
        val folderId = db.folderDao().insert(folder("Verbs"))
        dao.insert(saved(1, "a", savedAt = 100))
        dao.insert(saved(2, "b", savedAt = 200))

        dao.setFolder(1, folderId, now = 500)

        assertThat(dao.observeInFolder(folderId).first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.observeUnfiled().first().map { it.entryId }).containsExactly(2L)
        assertThat(dao.get(1)?.updatedAt).isEqualTo(500L)

        dao.setFolder(1, null, now = 600)

        assertThat(dao.observeInFolder(folderId).first()).isEmpty()
        assertThat(dao.observeUnfiled().first().map { it.entryId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun deletingFolderKeepsEntriesUnfiled() = runTest {
        val folderId = db.folderDao().insert(folder("Verbs"))
        dao.insert(saved(1, "a", savedAt = 100, folderId = folderId))

        db.folderDao().delete(folderId)

        assertThat(dao.get(1)?.folderId).isNull()
        assertThat(dao.observeUnfiled().first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.observeCount().first()).isEqualTo(1)
    }

    @Test
    fun searchMatchesHeadwordWylieAndGlossIgnoringCase() = runTest {
        dao.insert(saved(1, "བཀྲ་ཤིས་", savedAt = 100, wylie = "bkra shis", gloss = "auspiciousness"))
        dao.insert(saved(2, "house", savedAt = 200, gloss = "ཁང་པ་"))
        dao.insert(saved(3, "100%", savedAt = 300, gloss = "entirely"))
        dao.insert(saved(4, "a_b", savedAt = 400, gloss = "underscore"))

        assertThat(dao.search("BKRA").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("ཤིས").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("Auspicious").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("ཁང").first().map { it.entryId }).containsExactly(2L)
        assertThat(dao.search("OUS").first().map { it.entryId }).containsExactly(2L, 1L).inOrder()
        // LIKE wildcards in the query are literal characters.
        assertThat(dao.search("%").first().map { it.entryId }).containsExactly(3L)
        assertThat(dao.search("_").first().map { it.entryId }).containsExactly(4L)
    }

    @Test
    fun setNoteStoresAndClears() = runTest {
        dao.insert(saved(1, "a", savedAt = 100))

        dao.setNote(1, "remember", now = 200)
        assertThat(dao.get(1)?.note).isEqualTo("remember")

        dao.setNote(1, null, now = 300)
        assertThat(dao.get(1)?.note).isNull()
        assertThat(dao.get(1)?.updatedAt).isEqualTo(300L)
    }

    private fun saved(
        entryId: Long,
        headword: String,
        savedAt: Long,
        folderId: Long? = null,
        wylie: String? = null,
        gloss: String = "gloss",
    ) = SavedEntryEntity(
        entryId = entryId,
        directionId = "bo-en",
        headword = headword,
        wylie = wylie,
        glossPreview = gloss,
        folderId = folderId,
        note = null,
        savedAt = savedAt,
        updatedAt = savedAt,
    )

    private fun folder(name: String) = FolderEntity(name = name, position = 0, createdAt = 1, updatedAt = 1)
}
