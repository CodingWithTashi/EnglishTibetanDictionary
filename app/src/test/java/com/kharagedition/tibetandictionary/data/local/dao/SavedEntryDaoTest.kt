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
        assertThat(dao.observeCount("bo").first()).isEqualTo(1)
    }

    @Test
    fun listsAreNewestFirstAndCountsFollow() = runTest {
        dao.insert(saved(1, "a", savedAt = 100))
        dao.insert(saved(2, "b", savedAt = 300))
        dao.insert(saved(3, "c", savedAt = 200))

        assertThat(dao.observeAll("bo").first().map { it.entryId }).containsExactly(2L, 3L, 1L).inOrder()
        assertThat(dao.observeIds("bo").first()).containsExactly(1L, 2L, 3L)
        assertThat(dao.observeCount("bo").first()).isEqualTo(3)
    }

    @Test
    fun listsCountsAndIdsAreScopedToPack() = runTest {
        val verbs = db.folderDao().insert(folder("Verbs"))
        val animals = db.folderDao().insert(folder("Animals", packId = "dz"))
        dao.insert(saved(1, "a", savedAt = 100))
        dao.insert(saved(2, "b", savedAt = 200, folderId = verbs))
        dao.insert(saved(3, "c", savedAt = 300, packId = "dz"))
        dao.insert(saved(4, "d", savedAt = 400, folderId = animals, packId = "dz"))

        assertThat(dao.observeAll("bo").first().map { it.entryId }).containsExactly(2L, 1L).inOrder()
        assertThat(dao.observeAll("dz").first().map { it.entryId }).containsExactly(4L, 3L).inOrder()
        assertThat(dao.observeUnfiled("bo").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.observeUnfiled("dz").first().map { it.entryId }).containsExactly(3L)
        assertThat(dao.observeInFolder("bo", verbs).first().map { it.entryId }).containsExactly(2L)
        assertThat(dao.observeInFolder("bo", animals).first()).isEmpty()
        assertThat(dao.observeIds("bo").first()).containsExactly(1L, 2L)
        assertThat(dao.observeIds("dz").first()).containsExactly(3L, 4L)
        assertThat(dao.observeCount("bo").first()).isEqualTo(2)
        assertThat(dao.observeCount("dz").first()).isEqualTo(2)
    }

    @Test
    fun setFolderMovesBetweenFolderAndUnfiled() = runTest {
        val folderId = db.folderDao().insert(folder("Verbs"))
        dao.insert(saved(1, "a", savedAt = 100))
        dao.insert(saved(2, "b", savedAt = 200))

        dao.setFolder(1, folderId, now = 500)

        assertThat(dao.observeInFolder("bo", folderId).first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.observeUnfiled("bo").first().map { it.entryId }).containsExactly(2L)
        assertThat(dao.get(1)?.updatedAt).isEqualTo(500L)

        dao.setFolder(1, null, now = 600)

        assertThat(dao.observeInFolder("bo", folderId).first()).isEmpty()
        assertThat(dao.observeUnfiled("bo").first().map { it.entryId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun deletingFolderKeepsEntriesUnfiled() = runTest {
        val folderId = db.folderDao().insert(folder("Verbs"))
        dao.insert(saved(1, "a", savedAt = 100, folderId = folderId))

        db.folderDao().delete(folderId)

        assertThat(dao.get(1)?.folderId).isNull()
        assertThat(dao.observeUnfiled("bo").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.observeCount("bo").first()).isEqualTo(1)
    }

    @Test
    fun searchMatchesHeadwordWylieAndGlossIgnoringCase() = runTest {
        dao.insert(saved(1, "བཀྲ་ཤིས་", savedAt = 100, wylie = "bkra shis", gloss = "auspiciousness"))
        dao.insert(saved(2, "house", savedAt = 200, gloss = "ཁང་པ་"))
        dao.insert(saved(3, "100%", savedAt = 300, gloss = "entirely"))
        dao.insert(saved(4, "a_b", savedAt = 400, gloss = "underscore"))

        assertThat(dao.search("bo", "BKRA").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("bo", "ཤིས").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("bo", "Auspicious").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("bo", "ཁང").first().map { it.entryId }).containsExactly(2L)
        assertThat(dao.search("bo", "OUS").first().map { it.entryId }).containsExactly(2L, 1L).inOrder()
        // LIKE wildcards in the query are literal characters.
        assertThat(dao.search("bo", "%").first().map { it.entryId }).containsExactly(3L)
        assertThat(dao.search("bo", "_").first().map { it.entryId }).containsExactly(4L)
    }

    @Test
    fun searchIsScopedToPack() = runTest {
        dao.insert(saved(1, "བཀྲ་ཤིས་", savedAt = 100, wylie = "bkra shis", gloss = "auspicious"))
        dao.insert(saved(2, "བཀྲ་ཤིས་", savedAt = 200, wylie = "bkra shis", gloss = "auspicious", packId = "dz"))

        assertThat(dao.search("bo", "ཤིས").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("bo", "bkra").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("bo", "auspicious").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("dz", "auspicious").first().map { it.entryId }).containsExactly(2L)
    }

    @Test
    fun searchComparesStoredTypographicApostrophesAsAscii() = runTest {
        dao.insert(saved(1, "bird’s nest", savedAt = 100, gloss = "བྱའི་ཚང་།"))
        dao.insert(saved(2, "སྐྱེས་བུ་", savedAt = 200, wylie = "skyes bu", gloss = "one‘s own ʼway’"))
        dao.insert(saved(3, "འཇམ་དཔལ་", savedAt = 300, wylie = "'jam dpal", gloss = "Manjushri"))

        assertThat(dao.search("bo", "bird's").first().map { it.entryId }).containsExactly(1L)
        assertThat(dao.search("bo", "one's own 'way'").first().map { it.entryId }).containsExactly(2L)
        assertThat(dao.search("bo", "'jam").first().map { it.entryId }).containsExactly(3L)
        assertThat(dao.search("bo", "'").first().map { it.entryId }).containsExactly(3L, 2L, 1L).inOrder()
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
        packId: String = "bo",
    ) = SavedEntryEntity(
        entryId = entryId,
        packId = packId,
        directionId = "bo-en",
        headword = headword,
        wylie = wylie,
        glossPreview = gloss,
        folderId = folderId,
        note = null,
        savedAt = savedAt,
        updatedAt = savedAt,
    )

    private fun folder(name: String, packId: String = "bo") =
        FolderEntity(packId = packId, name = name, position = 0, createdAt = 1, updatedAt = 1)
}
