package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
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
class SavedRepositoryImplTest {

    private val clock = FakeClock()
    private lateinit var db: UserDatabase
    private lateinit var repo: SavedRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        repo = SavedRepositoryImpl(db, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveThenUnsaveTogglesIsSaved() = runTest {
        repo.observeIsSaved(tashi.id).test {
            assertThat(awaitItem()).isFalse()

            repo.save(tashi, folderId = null)
            assertThat(awaitItem()).isTrue()

            repo.unsave(tashi.id)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun saveStoresASnapshotOfTheEntry() = runTest {
        repo.save(tashi, folderId = null)

        assertThat(repo.observeAll().first()).containsExactly(
            SavedEntry(
                entryId = tashi.id,
                directionId = tashi.directionId,
                headword = tashi.headword,
                wylie = tashi.wylie,
                glossPreview = tashi.glossPreview,
                folderId = null,
                note = null,
                savedAt = clock.now().toEpochMilli(),
            ),
        )
    }

    @Test
    fun unsaveOfAnEntryThatIsNotSavedIsHarmless() = runTest {
        repo.save(tashi, folderId = null)

        repo.unsave(nyingje.id)

        assertThat(repo.observeSavedIds().first()).containsExactly(tashi.id)
    }

    @Test
    fun observeAllIsNewestFirst() = runTest {
        repo.save(tashi, folderId = null)
        tick()
        repo.save(nyingje, folderId = null)
        tick()
        repo.save(house, folderId = null)

        assertThat(repo.observeAll().first().map { it.entryId })
            .containsExactly(house.id, nyingje.id, tashi.id).inOrder()
    }

    @Test
    fun observeSavedIdsAndCountFollowSaves() = runTest {
        repo.observeSavedIds().test {
            assertThat(awaitItem()).isEmpty()

            repo.save(tashi, folderId = null)
            assertThat(awaitItem()).containsExactly(tashi.id)

            repo.save(nyingje, folderId = null)
            assertThat(awaitItem()).containsExactly(tashi.id, nyingje.id)

            repo.unsave(tashi.id)
            assertThat(awaitItem()).containsExactly(nyingje.id)
        }
        repo.observeCount().test {
            assertThat(awaitItem()).isEqualTo(1)

            repo.save(house, folderId = null)
            assertThat(awaitItem()).isEqualTo(2)
        }
    }

    @Test
    fun observeFolderNullReturnsOnlyUnfiledEntries() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.save(tashi, folderId = null)
        tick()
        repo.save(nyingje, folderId = verbs)
        tick()
        repo.save(house, folderId = null)

        assertThat(repo.observeFolder(null).first().map { it.entryId })
            .containsExactly(house.id, tashi.id).inOrder()
        assertThat(repo.observeFolder(verbs).first().map { it.entryId }).containsExactly(nyingje.id)
        assertThat(repo.observeAll().first()).hasSize(3)
    }

    @Test
    fun observeFolderIsNewestFirst() = runTest {
        val words = repo.createFolder("Words")
        repo.save(tashi, folderId = words)
        tick()
        repo.save(nyingje, folderId = words)

        assertThat(repo.observeFolder(words).first().map { it.entryId })
            .containsExactly(nyingje.id, tashi.id).inOrder()
    }

    @Test
    fun searchMatchesHeadwordWylieAndGlossIgnoringCase() = runTest {
        repo.save(tashi, folderId = null)
        tick()
        repo.save(nyingje, folderId = null)
        tick()
        repo.save(house, folderId = null)

        assertThat(repo.search("bkra").first().map { it.entryId }).containsExactly(tashi.id)
        assertThat(repo.search("BKRA SHIS").first().map { it.entryId }).containsExactly(tashi.id)
        assertThat(repo.search("སྙིང་").first().map { it.entryId }).containsExactly(nyingje.id)
        assertThat(repo.search("COMPASSION").first().map { it.entryId }).containsExactly(nyingje.id)
        assertThat(repo.search("nothing like this").first()).isEmpty()
    }

    @Test
    fun searchResultsAreNewestFirstAndTreatWildcardsLiterally() = runTest {
        repo.save(tashi, folderId = null)
        tick()
        repo.save(nyingje, folderId = null)

        assertThat(repo.search("s").first().map { it.entryId }).containsExactly(nyingje.id, tashi.id).inOrder()
        assertThat(repo.search("%").first()).isEmpty()
        assertThat(repo.search("_").first()).isEmpty()
    }

    @Test
    fun searchFollowsLaterChanges() = runTest {
        repo.search("bkra").test {
            assertThat(awaitItem()).isEmpty()

            repo.save(tashi, folderId = null)
            assertThat(awaitItem().map { it.entryId }).containsExactly(tashi.id)

            repo.unsave(tashi.id)
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun setFolderMovesAnEntryAndBack() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.save(tashi, folderId = null)
        tick()

        repo.setFolder(tashi.id, verbs)
        assertThat(repo.observeFolder(verbs).first().map { it.entryId }).containsExactly(tashi.id)
        assertThat(repo.observeFolder(null).first()).isEmpty()
        assertThat(db.savedEntryDao().get(tashi.id)?.updatedAt).isEqualTo(clock.now().toEpochMilli())

        repo.setFolder(tashi.id, null)
        assertThat(repo.observeFolder(null).first().map { it.entryId }).containsExactly(tashi.id)
        assertThat(repo.observeFolder(verbs).first()).isEmpty()
    }

    @Test
    fun deleteFolderKeepsItsEntriesWithNoFolder() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.save(tashi, folderId = verbs)
        repo.save(nyingje, folderId = verbs)

        repo.deleteFolder(verbs)

        assertThat(repo.observeFolders().first()).isEmpty()
        assertThat(repo.observeAll().first().map { it.folderId }).containsExactly(null, null)
        assertThat(repo.observeFolder(null).first().map { it.entryId }).containsExactly(tashi.id, nyingje.id)
    }

    @Test
    fun createFolderRejectsADuplicateNameIgnoringCaseAndSurroundingSpaces() = runTest {
        repo.createFolder("Verbs")

        assertThat(failureOf { repo.createFolder("verbs") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failureOf { repo.createFolder("  VERBS  ") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("Verbs")
    }

    @Test
    fun createFolderRejectsABlankName() = runTest {
        assertThat(failureOf { repo.createFolder("") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failureOf { repo.createFolder("   ") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repo.observeFolders().first()).isEmpty()
    }

    @Test
    fun createFolderTrimsTheNameAndAppendsAtTheEnd() = runTest {
        val verbs = repo.createFolder("  Verbs ")
        val nouns = repo.createFolder("Nouns")
        val prayers = repo.createFolder("ཞལ་འདོན་")

        assertThat(verbs).isNotEqualTo(nouns)
        assertThat(repo.observeFolders().first()).containsExactly(
            Folder(id = verbs, name = "Verbs", position = 0, count = 0),
            Folder(id = nouns, name = "Nouns", position = 1, count = 0),
            Folder(id = prayers, name = "ཞལ་འདོན་", position = 2, count = 0),
        ).inOrder()
    }

    @Test
    fun newFolderGoesAfterTheHighestPositionEvenAfterADelete() = runTest {
        val first = repo.createFolder("A")
        repo.createFolder("B")
        repo.deleteFolder(first)

        val third = repo.createFolder("C")

        assertThat(repo.observeFolders().first().map { it.id to it.position }.last()).isEqualTo(third to 2)
    }

    @Test
    fun renameFolderToItsOwnNameWithDifferentCasingSucceeds() = runTest {
        val verbs = repo.createFolder("Verbs")

        repo.renameFolder(verbs, "VERBS")

        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("VERBS")
    }

    @Test
    fun renameFolderTrimsAndUpdatesTheName() = runTest {
        val verbs = repo.createFolder("Verbs")

        repo.renameFolder(verbs, "  Actions ")

        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("Actions")
    }

    @Test
    fun renameFolderRejectsAnotherFoldersNameAndABlankName() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.createFolder("Nouns")

        assertThat(failureOf { repo.renameFolder(verbs, " nouns ") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failureOf { repo.renameFolder(verbs, "  ") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("Verbs", "Nouns").inOrder()
    }

    @Test
    fun aUniqueIndexViolationBecomesAnIllegalArgumentException() = runTest {
        // Simulates another writer inserting the same name between the duplicate check and the insert.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER raced BEFORE INSERT ON folders WHEN NEW.name = 'Raced' " +
                "BEGIN SELECT RAISE(ABORT, 'UNIQUE constraint failed: folders.name'); END",
        )

        assertThat(failureOf { repo.createFolder("Raced") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repo.observeFolders().first()).isEmpty()
    }

    @Test
    fun aUniqueIndexViolationOnRenameBecomesAnIllegalArgumentException() = runTest {
        val verbs = repo.createFolder("Verbs")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER raced BEFORE UPDATE OF name ON folders WHEN NEW.name = 'Raced' " +
                "BEGIN SELECT RAISE(ABORT, 'UNIQUE constraint failed: folders.name'); END",
        )

        assertThat(failureOf { repo.renameFolder(verbs, "Raced") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("Verbs")
    }

    @Test
    fun folderCountsUpdateLive() = runTest {
        val verbs = repo.createFolder("Verbs")

        repo.observeFolders().test {
            assertThat(awaitItem().single().count).isEqualTo(0)

            repo.save(tashi, folderId = verbs)
            awaitUntil { it.single().count == 1 }

            repo.save(nyingje, folderId = null)
            repo.setFolder(nyingje.id, verbs)
            awaitUntil { it.single().count == 2 }

            repo.unsave(tashi.id)
            awaitUntil { it.single().count == 1 }

            repo.deleteFolder(verbs)
            awaitUntil { it.isEmpty() }
        }
    }

    @Test
    fun resaveKeepsTheNoteAndMovesTheEntryToTheNewFolder() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.save(tashi, folderId = null)
        db.savedEntryDao().setNote(tashi.id, "greeting", now = clock.now().toEpochMilli())
        tick()

        repo.save(tashi, folderId = verbs)

        val saved = repo.observeAll().first().single()
        assertThat(saved.note).isEqualTo("greeting")
        assertThat(saved.folderId).isEqualTo(verbs)
        assertThat(saved.savedAt).isEqualTo(clock.now().toEpochMilli())
        assertThat(db.savedEntryDao().get(tashi.id)?.updatedAt).isEqualTo(clock.now().toEpochMilli())
        assertThat(repo.observeCount().first()).isEqualTo(1)
    }

    @Test
    fun resaveRefreshesTheSnapshotWithoutTheEntryEverShowingAsUnsaved() = runTest {
        repo.save(tashi, folderId = null)

        repo.observeAll().test {
            assertThat(awaitItem()).hasSize(1)

            tick()
            repo.save(tashi.copy(glossPreview = "updated gloss"), folderId = null)

            while (true) {
                val saved = awaitItem()
                assertThat(saved).hasSize(1)
                if (saved.single().glossPreview == "updated gloss") break
            }
        }
    }

    private fun tick() {
        clock.instant = clock.instant.plus(Duration.ofSeconds(1))
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    private suspend fun ReceiveTurbine<List<Folder>>.awaitUntil(matches: (List<Folder>) -> Boolean) {
        do {
            val item = awaitItem()
        } while (!matches(item))
    }

    private val tashi get() = SampleData.tashi
    private val nyingje get() = SampleData.nyingje
    private val house get() = SampleData.house
}
