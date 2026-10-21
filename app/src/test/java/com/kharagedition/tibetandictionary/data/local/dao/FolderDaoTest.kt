package com.kharagedition.tibetandictionary.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import com.kharagedition.tibetandictionary.data.local.relation.FolderCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {

    private lateinit var db: UserDatabase
    private lateinit var dao: FolderDao

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        dao = db.folderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun foldersOrderByPositionThenName() = runTest {
        dao.insert(folder("Verbs", position = 1))
        dao.insert(folder("Animals", position = 1))
        dao.insert(folder("Prayers", position = 0))

        assertThat(dao.observeAll("bo").first().map { it.name }).containsExactly("Prayers", "Animals", "Verbs").inOrder()
    }

    @Test
    fun observeAllIsScopedToPack() = runTest {
        dao.insert(folder("Verbs", packId = "bo"))
        dao.insert(folder("Animals", packId = "dz"))

        assertThat(dao.observeAll("bo").first().map { it.name }).containsExactly("Verbs")
        assertThat(dao.observeAll("dz").first().map { it.name }).containsExactly("Animals")
    }

    @Test
    fun nextPositionGoesAfterTheHighest() = runTest {
        assertThat(dao.nextPosition("bo")).isEqualTo(0)

        dao.insert(folder("Verbs", position = 4))
        dao.insert(folder("Animals", position = 2))

        assertThat(dao.nextPosition("bo")).isEqualTo(5)
    }

    @Test
    fun nextPositionIsPerPack() = runTest {
        dao.insert(folder("Verbs", position = 4, packId = "bo"))

        assertThat(dao.nextPosition("dz")).isEqualTo(0)

        dao.insert(folder("Animals", position = 1, packId = "dz"))

        assertThat(dao.nextPosition("dz")).isEqualTo(2)
        assertThat(dao.nextPosition("bo")).isEqualTo(5)
    }

    @Test
    fun namesAreUnique() = runTest {
        dao.insert(folder("Verbs"))

        val duplicate = runCatching { dao.insert(folder("Verbs")) }.exceptionOrNull()

        assertThat(duplicate).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun nameUniquePerPackOnly() = runTest {
        dao.insert(folder("Verbs", packId = "bo"))

        assertThat(dao.existsByNameIgnoreCase("dz", "verbs", exceptId = null)).isFalse()
        dao.insert(folder("Verbs", packId = "dz"))
        val duplicate = runCatching { dao.insert(folder("Verbs", packId = "bo")) }.exceptionOrNull()

        assertThat(duplicate).isInstanceOf(SQLiteConstraintException::class.java)
        assertThat(dao.observeAll("bo").first().map { it.name }).containsExactly("Verbs")
        assertThat(dao.observeAll("dz").first().map { it.name }).containsExactly("Verbs")
    }

    @Test
    fun existsByNameIgnoresCaseAndSurroundingSpaces() = runTest {
        val verbs = dao.insert(folder("Verbs"))
        dao.insert(folder("ཚིག་"))

        assertThat(dao.existsByNameIgnoreCase("bo", "verbs", exceptId = null)).isTrue()
        assertThat(dao.existsByNameIgnoreCase("bo", " VERBS ", exceptId = null)).isTrue()
        assertThat(dao.existsByNameIgnoreCase("bo", "ཚིག་", exceptId = null)).isTrue()
        assertThat(dao.existsByNameIgnoreCase("bo", "Nouns", exceptId = null)).isFalse()
        assertThat(dao.existsByNameIgnoreCase("bo", "verbs", exceptId = verbs)).isFalse()
    }

    @Test
    fun existsByIdIsTrueOnlyForExistingFolders() = runTest {
        val verbs = dao.insert(folder("Verbs"))

        assertThat(dao.exists(verbs, "bo")).isTrue()
        assertThat(dao.exists(verbs + 1, "bo")).isFalse()

        dao.delete(verbs)
        assertThat(dao.exists(verbs, "bo")).isFalse()
    }

    @Test
    fun existsIsScopedToPack() = runTest {
        val animals = dao.insert(folder("Animals", packId = "dz"))

        assertThat(dao.exists(animals, "bo")).isFalse()
        assertThat(dao.exists(animals, "dz")).isTrue()
    }

    @Test
    fun renameUpdatesNameAndTimestamp() = runTest {
        val id = dao.insert(folder("Verbs"))

        dao.rename(id, "Actions", now = 99)

        val row = dao.observeAll("bo").first().single()
        assertThat(row.name).isEqualTo("Actions")
        assertThat(row.updatedAt).isEqualTo(99L)
        assertThat(row.createdAt).isEqualTo(1L)
    }

    @Test
    fun countsPerFolder() = runTest {
        val verbs = dao.insert(folder("Verbs"))
        val nouns = dao.insert(folder("Nouns"))
        dao.insert(folder("Empty"))
        val animals = dao.insert(folder("Animals", packId = "dz"))
        val saved = db.savedEntryDao()
        saved.insert(saved(1, verbs))
        saved.insert(saved(2, verbs))
        saved.insert(saved(3, nouns))
        saved.insert(saved(4, null))
        saved.insert(saved(5, animals, packId = "dz"))

        assertThat(dao.observeCounts("bo").first())
            .containsExactly(FolderCount(folderId = verbs, count = 2), FolderCount(folderId = nouns, count = 1))
        assertThat(dao.observeCounts("dz").first()).containsExactly(FolderCount(folderId = animals, count = 1))
    }

    private fun folder(name: String, position: Int = 0, packId: String = "bo") =
        FolderEntity(packId = packId, name = name, position = position, createdAt = 1, updatedAt = 1)

    private fun saved(entryId: Long, folderId: Long?, packId: String = "bo") = SavedEntryEntity(
        entryId = entryId,
        packId = packId,
        directionId = "bo-en",
        headword = "h$entryId",
        wylie = null,
        glossPreview = "gloss",
        folderId = folderId,
        note = null,
        savedAt = entryId,
        updatedAt = entryId,
    )
}
