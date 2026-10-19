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

        assertThat(dao.observeAll().first().map { it.name }).containsExactly("Prayers", "Animals", "Verbs").inOrder()
    }

    @Test
    fun nextPositionGoesAfterTheHighest() = runTest {
        assertThat(dao.nextPosition()).isEqualTo(0)

        dao.insert(folder("Verbs", position = 4))
        dao.insert(folder("Animals", position = 2))

        assertThat(dao.nextPosition()).isEqualTo(5)
    }

    @Test
    fun namesAreUnique() = runTest {
        dao.insert(folder("Verbs"))

        val duplicate = runCatching { dao.insert(folder("Verbs")) }.exceptionOrNull()

        assertThat(duplicate).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun existsByNameIgnoresCaseAndSurroundingSpaces() = runTest {
        val verbs = dao.insert(folder("Verbs"))
        dao.insert(folder("ཚིག་"))

        assertThat(dao.existsByNameIgnoreCase("verbs", exceptId = null)).isTrue()
        assertThat(dao.existsByNameIgnoreCase(" VERBS ", exceptId = null)).isTrue()
        assertThat(dao.existsByNameIgnoreCase("ཚིག་", exceptId = null)).isTrue()
        assertThat(dao.existsByNameIgnoreCase("Nouns", exceptId = null)).isFalse()
        assertThat(dao.existsByNameIgnoreCase("verbs", exceptId = verbs)).isFalse()
    }

    @Test
    fun renameUpdatesNameAndTimestamp() = runTest {
        val id = dao.insert(folder("Verbs"))

        dao.rename(id, "Actions", now = 99)

        val row = dao.observeAll().first().single()
        assertThat(row.name).isEqualTo("Actions")
        assertThat(row.updatedAt).isEqualTo(99L)
        assertThat(row.createdAt).isEqualTo(1L)
    }

    @Test
    fun countsPerFolder() = runTest {
        val verbs = dao.insert(folder("Verbs"))
        val nouns = dao.insert(folder("Nouns"))
        dao.insert(folder("Empty"))
        val saved = db.savedEntryDao()
        saved.insert(saved(1, verbs))
        saved.insert(saved(2, verbs))
        saved.insert(saved(3, nouns))
        saved.insert(saved(4, null))

        assertThat(dao.observeCounts().first())
            .containsExactly(FolderCount(folderId = verbs, count = 2), FolderCount(folderId = nouns, count = 1))
    }

    private fun folder(name: String, position: Int = 0) =
        FolderEntity(name = name, position = position, createdAt = 1, updatedAt = 1)

    private fun saved(entryId: Long, folderId: Long?) = SavedEntryEntity(
        entryId = entryId,
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
