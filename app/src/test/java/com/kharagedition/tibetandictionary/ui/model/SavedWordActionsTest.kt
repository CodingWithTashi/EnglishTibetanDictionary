package com.kharagedition.tibetandictionary.ui.model

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeSavedRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions.NewFolder
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions.Toggle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedWordActionsTest {

    private val clock = FakeClock()
    private val saved = FakeSavedRepository(
        initialFolders = listOf(Folder(1, "Dharma", 0, 0), Folder(2, "Daily", 1, 0)),
        clock = clock,
    )
    private val prefs = FakeUserPreferencesRepository()
    private val actions = SavedWordActions(saved, prefs)

    private val tashi = SampleData.tashi

    /** [saved], whose saves wait for [gate] — a slow write, so a second tap lands while the first still runs. */
    private class SlowSaves(private val delegate: FakeSavedRepository) : SavedRepository by delegate {
        val gate = CompletableDeferred<Unit>()

        override suspend fun save(entry: EntrySummary, folderId: Long?) {
            gate.await()
            delegate.save(entry, folderId)
        }
    }

    // Toggle

    @Test
    fun `toggle saves to the last-used folder`() = runTest {
        prefs.prefs.value = UserPrefs(lastUsedFolderId = 2)

        val result = actions.toggle(tashi)

        assertThat(result).isEqualTo(Toggle.Saved(Folder(2, "Daily", 1, 0)))
        assertThat(saved.saved.value.single().folderId).isEqualTo(2L)
    }

    @Test
    fun `toggle without a last-used folder, or with a deleted one, saves without a folder`() = runTest {
        assertThat(actions.toggle(tashi)).isEqualTo(Toggle.Saved(folder = null))
        actions.toggle(tashi)

        prefs.prefs.value = UserPrefs(lastUsedFolderId = 99)
        assertThat(actions.toggle(tashi)).isEqualTo(Toggle.Saved(folder = null))
        assertThat(saved.saved.value.single().folderId).isNull()
        // The stored folder is left as it is.
        assertThat(prefs.prefs.value.lastUsedFolderId).isEqualTo(99L)
    }

    @Test
    fun `toggle removes a saved word and restore puts it back unchanged`() = runTest {
        val original = SampleData.savedTashi.copy(folderId = 1, note = "from the Lamrim class")
        saved.saved.value = listOf(original)

        val result = actions.toggle(tashi)

        assertThat(result).isEqualTo(Toggle.Removed(original))
        assertThat(saved.saved.value).isEmpty()

        clock.instant = clock.instant.plusSeconds(60)
        actions.restore(original)
        assertThat(saved.saved.value).containsExactly(original)
    }

    @Test
    fun `a second toggle while the first is still saving is dropped`() = runTest {
        val slow = SlowSaves(saved)
        val actions = SavedWordActions(slow, prefs)
        val results = mutableListOf<Toggle?>()

        launch { results += actions.toggle(tashi) }
        runCurrent()
        launch { results += actions.toggle(tashi) }
        runCurrent()
        // The second tap returned at once, without saving or removing anything.
        assertThat(results).containsExactly(null)

        slow.gate.complete(Unit)
        advanceUntilIdle()

        assertThat(results).containsExactly(null, Toggle.Saved(folder = null)).inOrder()
        assertThat(saved.saved.value.map { it.entryId }).containsExactly(tashi.id)
        // Once the first has finished, the next tap toggles again.
        assertThat(actions.toggle(tashi)).isInstanceOf(Toggle.Removed::class.java)
    }

    @Test
    fun `the single-flight guard is per entry`() = runTest {
        val slow = SlowSaves(saved)
        val actions = SavedWordActions(slow, prefs)
        val results = mutableListOf<Toggle?>()

        launch { results += actions.toggle(tashi) }
        launch { results += actions.toggle(SampleData.house) }
        runCurrent()
        slow.gate.complete(Unit)
        advanceUntilIdle()

        assertThat(results).containsExactly(Toggle.Saved(null), Toggle.Saved(null))
        assertThat(saved.saved.value.map { it.entryId }).containsExactly(tashi.id, SampleData.house.id)
    }

    @Test
    fun `a failed save is passed on and frees the entry for the next tap`() = runTest {
        var fail = true
        val failing = object : SavedRepository by saved {
            override suspend fun save(entry: EntrySummary, folderId: Long?) {
                if (fail) throw IllegalStateException("disk full") else saved.save(entry, folderId)
            }
        }
        val actions = SavedWordActions(failing, prefs)

        val failure = runCatching { actions.toggle(tashi) }.exceptionOrNull()
        assertThat(failure).hasMessageThat().isEqualTo("disk full")

        fail = false
        assertThat(actions.toggle(tashi)).isEqualTo(Toggle.Saved(null))
    }

    // Save if absent and unsave

    @Test
    fun `saveIfAbsent saves once`() = runTest {
        prefs.prefs.value = UserPrefs(lastUsedFolderId = 1)

        assertThat(actions.saveIfAbsent(tashi)).isEqualTo(Toggle.Saved(Folder(1, "Dharma", 0, 0)))
        val first = saved.saved.value.single()
        clock.instant = clock.instant.plusSeconds(1)

        assertThat(actions.saveIfAbsent(tashi)).isNull()
        assertThat(saved.saved.value).containsExactly(first)
    }

    @Test
    fun `unsave returns the removed row, and null when the word is not saved`() = runTest {
        saved.saved.value = listOf(SampleData.savedTashi)

        assertThat(actions.unsave(tashi.id)).isEqualTo(SampleData.savedTashi)
        assertThat(saved.saved.value).isEmpty()
        assertThat(actions.unsave(tashi.id)).isNull()
    }

    // Filing

    @Test
    fun `fileIn refiles a saved word and remembers the folder`() = runTest {
        saved.saved.value = listOf(SampleData.savedTashi)

        val filed = actions.fileIn(tashi.id, 1)

        assertThat(filed).isEqualTo(SavedWordActions.Filed(SampleData.savedTashi.headword, "Dharma"))
        assertThat(saved.saved.value.single().folderId).isEqualTo(1L)
        assertThat(prefs.prefs.value.lastUsedFolderId).isEqualTo(1L)

        assertThat(actions.fileIn(tashi.id, null)).isEqualTo(SavedWordActions.Filed(SampleData.savedTashi.headword, null))
        assertThat(saved.saved.value.single().folderId).isNull()
        assertThat(prefs.prefs.value.lastUsedFolderId).isNull()
    }

    @Test
    fun `fileIn saves an unsaved word when given its summary, and otherwise does nothing`() = runTest {
        assertThat(actions.fileIn(tashi.id, 2)).isNull()
        assertThat(saved.saved.value).isEmpty()
        assertThat(prefs.prefs.value.lastUsedFolderId).isNull()

        val filed = actions.fileIn(tashi.id, 2, unsaved = tashi)

        assertThat(filed).isEqualTo(SavedWordActions.Filed(tashi.headword, "Daily"))
        assertThat(saved.saved.value.single().folderId).isEqualTo(2L)
        assertThat(prefs.prefs.value.lastUsedFolderId).isEqualTo(2L)
    }

    // New folders

    @Test
    fun `createFolder trims the name, rejects a duplicate and ignores a blank one`() = runTest {
        assertThat(actions.createFolder("   ")).isNull()

        assertThat(actions.createFolder(" daily ")).isEqualTo(NewFolder.Rejected(UiText(R.string.folder_exists, listOf("daily"))))
        assertThat(saved.folders.value).hasSize(2)

        val created = actions.createFolder(" Grammar ")
        val grammar = saved.folders.value.single { it.name == "Grammar" }
        assertThat(created).isEqualTo(NewFolder.Created(grammar.id))
    }

    @Test
    fun `savedMessage names the folder when there is one`() {
        assertThat(SavedWordActions.savedMessage("བཀྲ་ཤིས་", "Daily"))
            .isEqualTo(UiText(R.string.saved_to_folder, listOf("བཀྲ་ཤིས་", "Daily")))
        assertThat(SavedWordActions.savedMessage("བཀྲ་ཤིས་", null)).isEqualTo(UiText(R.string.saved_simple, listOf("བཀྲ་ཤིས་")))
    }
}
