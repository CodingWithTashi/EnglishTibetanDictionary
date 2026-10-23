package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.SavedTranslationsRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationRecord
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.domain.SavedPeriod
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedTranslationsRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.FakeWordOfTheDayRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import com.kharagedition.tibetandictionary.ui.model.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = FakeClock() // 2026-10-19T09:00Z, UTC
    private val dictionary = FakeDictionaryRepository()
    private val saved = FakeSavedRepository(
        initialFolders = listOf(Folder(1, "Dharma", 0, 0), Folder(2, "Daily", 1, 0)),
        clock = clock,
    )
    private val wordOfTheDay = FakeWordOfTheDayRepository() // today's word is SampleData.tashiEntry
    private val prefs = FakeUserPreferencesRepository()
    private val errors = RecordingErrorReporter()
    private val translations = FakeSavedTranslationsRepository()

    private val tashi = SampleData.tashi

    /** A view model whose state is collected for the whole test, as the screen does. */
    private fun TestScope.viewModel(
        saved: SavedRepository = this@SavedViewModelTest.saved,
        wordOfTheDay: WordOfTheDayRepository = this@SavedViewModelTest.wordOfTheDay,
        dictionary: DictionaryRepository = this@SavedViewModelTest.dictionary,
        savedState: SavedStateHandle = SavedStateHandle(),
        savedTranslations: SavedTranslationsRepository = translations,
    ): SavedViewModel {
        val vm = SavedViewModel(
            savedState,
            saved,
            SavedWordActions(saved, prefs),
            dictionary,
            wordOfTheDay,
            clock,
            errors,
            savedTranslations,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun row(summary: EntrySummary, at: String, folderId: Long? = null) = SavedEntry(
        entryId = summary.id,
        packId = "bo",
        directionId = summary.directionId,
        headword = summary.headword,
        wylie = summary.wylie,
        glossPreview = summary.glossPreview,
        folderId = folderId,
        note = null,
        savedAt = Instant.parse(at).toEpochMilli(),
    )

    /** A saved word saved [minutesAgo] minutes before the clock's now. */
    private fun word(id: Long, headword: String, wylie: String?, gloss: String, minutesAgo: Long, folderId: Long? = null) =
        SavedEntry(
            entryId = id,
            packId = "bo",
            directionId = if (wylie != null) "bo-en" else "en-bo",
            headword = headword,
            wylie = wylie,
            glossPreview = gloss,
            folderId = folderId,
            note = null,
            savedAt = clock.instant.minusSeconds(minutesAgo * 60).toEpochMilli(),
        )

    /** Eight saved words: enough for the search-within field. */
    private val eightWords = listOf(
        word(1, "བཀྲ་ཤིས་", "bkra shis", "auspicious", minutesAgo = 1, folderId = 1),
        word(2, "བཀྲ་ཤིས་བདེ་ལེགས་", "bkra shis bde legs", "greeting: good luck", minutesAgo = 2, folderId = 2),
        word(3, "སྙིང་རྗེ་", "snying rje", "compassion, mercy", minutesAgo = 3, folderId = 1),
        word(4, "ཤེས་རབ་", "shes rab", "wisdom", minutesAgo = 4),
        word(5, "བྱམས་པ་", "byams pa", "loving-kindness", minutesAgo = 5),
        word(6, "བདེ་བ་", "bde ba", "happiness, bliss", minutesAgo = 6),
        word(7, "house", null, "ཁང་པ། ཁྱིམ་ཚང་།", minutesAgo = 7),
        word(8, "compassion", null, "སྙིང་རྗེ། ཐུགས་རྗེ།", minutesAgo = 8, folderId = 1),
    )

    private fun SavedUiState.ids(): List<Long> = groups.flatMap { group -> group.items.map { it.entryId } }

    // Grouping and mapping

    @Test
    fun `saved words are grouped by when they were saved, newest first`() = runTest {
        saved.saved.value = listOf(
            row(tashi, "2026-10-19T08:00:00Z"),
            row(SampleData.house, "2026-10-19T08:30:00Z"),
            row(SampleData.nyingje, "2026-10-15T08:00:00Z"),
            row(SampleData.tashiDelek, "2026-10-02T08:00:00Z"),
            row(SampleData.tashiBoBo, "2026-08-02T08:00:00Z"),
        )

        val state = viewModel().uiState.value

        assertThat(state.loading).isFalse()
        assertThat(state.totalCount).isEqualTo(5)
        assertThat(state.groups.map { group -> group.period to group.items.map { it.entryId } }).containsExactly(
            SavedPeriod.TODAY to listOf(SampleData.house.id, tashi.id),
            SavedPeriod.THIS_WEEK to listOf(SampleData.nyingje.id),
            SavedPeriod.THIS_MONTH to listOf(SampleData.tashiDelek.id),
            SavedPeriod.EARLIER to listOf(SampleData.tashiBoBo.id),
        ).inOrder()
    }

    @Test
    fun `a row shows the headword over its Wylie and gloss`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z", folderId = 2), row(SampleData.house, "2026-10-19T07:00:00Z"))

        val items = viewModel().uiState.value.groups.single().items

        assertThat(items).containsExactly(
            SavedItemUi(
                entryId = tashi.id,
                headword = "བཀྲ་ཤིས་",
                headwordIsTibetan = true,
                secondary = AnnotatedString("bkra shis · auspicious; good fortune, blessing"),
                folderId = 2,
            ),
            SavedItemUi(
                entryId = SampleData.house.id,
                headword = "house",
                headwordIsTibetan = false,
                secondary = AnnotatedString("ཁང་པ། ཁྱིམ་ཚང་། རིགས་རྒྱུད།"),
                folderId = null,
            ),
        ).inOrder()
    }

    // Folders

    @Test
    fun `selecting a folder shows only its words, and All shows every word`() = runTest {
        saved.saved.value = listOf(
            row(tashi, "2026-10-19T08:00:00Z", folderId = 1),
            row(SampleData.nyingje, "2026-10-19T07:00:00Z", folderId = 1),
            row(SampleData.house, "2026-10-19T06:00:00Z", folderId = 2),
            row(SampleData.tashiDelek, "2026-10-19T05:00:00Z"),
        )
        val vm = viewModel()

        assertThat(vm.uiState.value.folders).containsExactly(FolderUi(1, "Dharma", 2), FolderUi(2, "Daily", 1)).inOrder()
        assertThat(vm.uiState.value.selectedFolderId).isNull()

        vm.onEvent(SavedEvent.SelectFolder(1))

        assertThat(vm.uiState.value.selectedFolderId).isEqualTo(1)
        assertThat(vm.uiState.value.totalCount).isEqualTo(4)
        assertThat(vm.uiState.value.ids()).containsExactly(tashi.id, SampleData.nyingje.id).inOrder()

        vm.onEvent(SavedEvent.SelectFolder(2))
        assertThat(vm.uiState.value.ids()).containsExactly(SampleData.house.id)

        vm.onEvent(SavedEvent.SelectFolder(null))
        assertThat(vm.uiState.value.ids()).hasSize(4)
    }

    @Test
    fun `an empty folder shows no matches rather than the empty state`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z", folderId = 1))
        val vm = viewModel()

        vm.onEvent(SavedEvent.SelectFolder(2))

        assertThat(vm.uiState.value.groups).isEmpty()
        assertThat(vm.uiState.value.noMatches).isTrue()
        assertThat(vm.uiState.value.isEmpty).isFalse()
    }

    @Test
    fun `a selected folder that is deleted falls back to All`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z", folderId = 2), row(SampleData.house, "2026-10-19T07:00:00Z"))
        val vm = viewModel()
        vm.onEvent(SavedEvent.SelectFolder(2))

        saved.deleteFolder(2)

        assertThat(vm.uiState.value.selectedFolderId).isNull()
        assertThat(vm.uiState.value.ids()).containsExactly(tashi.id, SampleData.house.id).inOrder()
    }

    @Test
    fun `AddFolder creates the folder and closes the sheet`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z"))
        val vm = viewModel()

        vm.onEvent(SavedEvent.StartAddFolder)
        assertThat(vm.uiState.value.newFolder).isEqualTo(NewFolderUi())
        vm.onEvent(SavedEvent.NewFolderNameChanged("Grammar"))
        assertThat(vm.uiState.value.newFolder).isEqualTo(NewFolderUi(name = "Grammar"))
        vm.onEvent(SavedEvent.AddFolder(" Grammar "))

        assertThat(vm.uiState.value.newFolder).isNull()
        assertThat(vm.uiState.value.folders.map { it.name }).containsExactly("Dharma", "Daily", "Grammar").inOrder()
        assertThat(vm.uiState.value.selectedFolderId).isNull()
    }

    @Test
    fun `a duplicate folder name shows folder_exists and a blank one is ignored`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z"))
        val vm = viewModel()
        vm.onEvent(SavedEvent.StartAddFolder)

        vm.onEvent(SavedEvent.AddFolder("   "))
        assertThat(vm.uiState.value.newFolder).isEqualTo(NewFolderUi())

        vm.onEvent(SavedEvent.NewFolderNameChanged("dharma "))
        vm.onEvent(SavedEvent.AddFolder("dharma "))
        assertThat(vm.uiState.value.newFolder?.error).isEqualTo(UiText(R.string.folder_exists, listOf("dharma")))
        assertThat(saved.folders.value).hasSize(2)

        // Editing the name clears the error; dismissing closes the sheet.
        vm.onEvent(SavedEvent.NewFolderNameChanged("dharma 2"))
        assertThat(vm.uiState.value.newFolder).isEqualTo(NewFolderUi(name = "dharma 2"))
        vm.onEvent(SavedEvent.DismissAddFolder)
        assertThat(vm.uiState.value.newFolder).isNull()
    }

    // Search within

    @Test
    fun `search within filters the list, combined with the folder`() = runTest {
        saved.saved.value = eightWords
        val vm = viewModel()
        assertThat(vm.uiState.value.showSearch).isTrue()

        vm.onEvent(SavedEvent.QueryChanged("bkra"))
        assertThat(vm.uiState.value.query).isEqualTo("bkra")
        assertThat(vm.uiState.value.ids()).containsExactly(1L, 2L).inOrder()

        vm.onEvent(SavedEvent.QueryChanged("compassion"))
        assertThat(vm.uiState.value.ids()).containsExactly(3L, 8L).inOrder()

        vm.onEvent(SavedEvent.SelectFolder(1))
        vm.onEvent(SavedEvent.QueryChanged("bkra"))
        assertThat(vm.uiState.value.ids()).containsExactly(1L)

        vm.onEvent(SavedEvent.QueryChanged("nothing like this"))
        assertThat(vm.uiState.value.groups).isEmpty()
        assertThat(vm.uiState.value.noMatches).isTrue()

        vm.onEvent(SavedEvent.QueryChanged(""))
        assertThat(vm.uiState.value.ids()).containsExactly(1L, 3L, 8L).inOrder()
    }

    @Test
    fun `the typed query and the selected folder survive process death`() = runTest {
        saved.saved.value = eightWords
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        vm.onEvent(SavedEvent.SelectFolder(1))
        vm.onEvent(SavedEvent.QueryChanged("compassion"))

        // What the system restores into the view model it recreates.
        val restored = viewModel(savedState = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) }))

        with(restored.uiState.value) {
            assertThat(query).isEqualTo("compassion")
            assertThat(selectedFolderId).isEqualTo(1L)
            assertThat(ids()).containsExactly(3L, 8L).inOrder()
        }
    }

    @Test
    fun `a whitespace-only query does not filter`() = runTest {
        saved.saved.value = eightWords
        val vm = viewModel()

        vm.onEvent(SavedEvent.QueryChanged("   "))

        assertThat(vm.uiState.value.query).isEqualTo("   ")
        assertThat(vm.uiState.value.ids()).hasSize(8)
        assertThat(vm.uiState.value.noMatches).isFalse()
    }

    @Test
    fun `below eight saved words there is no search field and a typed query does not filter`() = runTest {
        saved.saved.value = eightWords
        val vm = viewModel()
        vm.onEvent(SavedEvent.QueryChanged("bkra"))

        saved.unsave(8)

        assertThat(vm.uiState.value.showSearch).isFalse()
        assertThat(vm.uiState.value.ids()).hasSize(7)
    }

    // Unsave and undo

    @Test
    fun `Unsave removes the word and Undo restores it as it was, in its folder`() = runTest {
        val original = row(tashi, "2026-10-12T08:00:00Z", folderId = 2).copy(note = "from the Lamrim class")
        saved.saved.value = listOf(original, row(SampleData.house, "2026-10-19T07:00:00Z"))
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.Unsave(tashi.id))

            assertThat(awaitItem()).isEqualTo(SavedEffect.Removed(tashi.id, folderId = 2))
            assertThat(vm.uiState.value.ids()).containsExactly(SampleData.house.id)

            clock.instant = clock.instant.plusSeconds(60)
            vm.onEvent(SavedEvent.UndoUnsave(tashi.id, folderId = 2))

            // Same folder, note and savedAt: the word goes back to its "This week" section.
            assertThat(saved.saved.value.single { it.entryId == tashi.id }).isEqualTo(original)
            assertThat(vm.uiState.value.groups.map { it.period }).containsExactly(SavedPeriod.TODAY, SavedPeriod.THIS_WEEK).inOrder()
            expectNoEvents()
        }
    }

    @Test
    fun `undo without a removed word does nothing`() = runTest {
        val vm = viewModel()

        vm.onEvent(SavedEvent.UndoUnsave(tashi.id, folderId = 2))

        assertThat(saved.saved.value).isEmpty()
    }

    @Test
    fun `a failed unsave is recorded and shown as an error toast`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z"))
        val failing = object : SavedRepository by saved {
            override suspend fun unsave(entryId: Long) = throw IOException("disk full")
        }
        val vm = viewModel(saved = failing)

        vm.effects.test {
            vm.onEvent(SavedEvent.Unsave(tashi.id))

            assertThat(awaitItem()).isEqualTo(SavedEffect.Toast(UiText(R.string.entry_action_failed)))
        }
        assertThat(errors.recorded.single()).isInstanceOf(IOException::class.java)
        assertThat(saved.saved.value).hasSize(1)
    }

    // Opening entries

    @Test
    fun `OpenEntry navigates to an entry the dictionary has`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z"))
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.OpenEntry(tashi.id))

            assertThat(awaitItem()).isEqualTo(SavedEffect.NavigateEntry(tashi.id))
        }
    }

    /** Review Focus 4: a saved word whose entry a newer dictionary dropped. */
    @Test
    fun `a failed entry lookup is recorded and shows the error toast`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z"))
        val failing = object : DictionaryRepository by dictionary {
            override suspend fun summaries(ids: List<Long>): List<EntrySummary> = throw IOException("db closed")
        }
        val vm = viewModel(dictionary = failing)

        vm.effects.test {
            vm.onEvent(SavedEvent.OpenEntry(tashi.id))

            assertThat(awaitItem()).isEqualTo(SavedEffect.Toast(UiText(R.string.entry_action_failed)))
            expectNoEvents()
        }
        assertThat(errors.recorded.single()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun missingEntryToast() = runTest {
        val gone = row(tashi, "2026-10-19T08:00:00Z").copy(entryId = 1_999_999, headword = "དཀར་པོ་", wylie = "dkar po")
        saved.saved.value = listOf(gone)
        val vm = viewModel()

        // The row still renders from its snapshot.
        val item = vm.uiState.value.groups.single().items.single()
        assertThat(item.entryId).isEqualTo(1_999_999)
        assertThat(item.headword).isEqualTo("དཀར་པོ་")
        assertThat(item.secondary.text).isEqualTo("dkar po · auspicious; good fortune, blessing")

        vm.effects.test {
            vm.onEvent(SavedEvent.OpenEntry(1_999_999))

            assertThat(awaitItem()).isEqualTo(SavedEffect.Toast(UiText(R.string.entry_missing)))
            expectNoEvents()
        }
    }

    // Empty state and today's word

    @Test
    fun `no saved words show the empty state with today's word to save`() = runTest {
        val state = viewModel().uiState.value

        assertThat(state.isEmpty).isTrue()
        assertThat(state.totalCount).isEqualTo(0)
        assertThat(state.groups).isEmpty()
        assertThat(state.noMatches).isFalse()
        val wotd = checkNotNull(state.wotd)
        assertThat(wotd.entryId).isEqualTo(tashi.id)
        assertThat(wotd.headword).isEqualTo(tashi.headword)
        assertThat(wotd.isSaved).isFalse()
        assertThat(state.showSaveWotd).isTrue()
    }

    @Test
    fun `the save-today's-word button is hidden when the word is already saved`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-18T08:00:00Z"))
        val vm = viewModel()

        assertThat(vm.uiState.value.wotd?.isSaved).isTrue()
        assertThat(vm.uiState.value.showSaveWotd).isFalse()

        saved.unsave(tashi.id)

        assertThat(vm.uiState.value.isEmpty).isTrue()
        assertThat(vm.uiState.value.showSaveWotd).isTrue()
    }

    @Test
    fun `the button is hidden when there is no word of the day`() = runTest {
        wordOfTheDay.entry.value = null

        val state = viewModel().uiState.value

        assertThat(state.isEmpty).isTrue()
        assertThat(state.wotd).isNull()
        assertThat(state.showSaveWotd).isFalse()
    }

    @Test
    fun `the list does not wait for today's word`() = runTest {
        saved.saved.value = listOf(row(tashi, "2026-10-19T08:00:00Z"))
        val slow = object : WordOfTheDayRepository {
            override fun observe(date: LocalDate): Flow<Entry?> = flow { awaitCancellation() }
            override suspend fun forDate(date: LocalDate): Entry? = null
        }

        val state = viewModel(wordOfTheDay = slow).uiState.value

        assertThat(state.loading).isFalse()
        assertThat(state.ids()).containsExactly(tashi.id)
        assertThat(state.wotd).isNull()
    }

    @Test
    fun `a failing word of the day is recorded and the empty state shows without the button`() = runTest {
        val failing = object : WordOfTheDayRepository {
            override fun observe(date: LocalDate): Flow<Entry?> = flow { throw IOException("no data") }
            override suspend fun forDate(date: LocalDate): Entry? = throw IOException("no data")
        }

        val state = viewModel(wordOfTheDay = failing).uiState.value

        assertThat(state.isEmpty).isTrue()
        assertThat(state.showSaveWotd).isFalse()
        assertThat(errors.recorded.single()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `SaveWotd saves today's word to the last-used folder and offers Change`() = runTest {
        prefs.prefs.value = UserPrefs(lastUsedFolderId = 2)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.SaveWotd)

            assertThat(awaitItem()).isEqualTo(
                SavedEffect.Toast(
                    UiText(R.string.saved_to_folder, listOf(tashi.headword, "Daily")),
                    R.string.change,
                    SavedEvent.ChangeFolder(tashi.id),
                ),
            )
        }
        assertThat(saved.saved.value.single().folderId).isEqualTo(2)
        assertThat(vm.uiState.value.isEmpty).isFalse()
        assertThat(vm.uiState.value.wotd?.isSaved).isTrue()
    }

    @Test
    fun `tapping Save today's word twice saves it once`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.SaveWotd)
            val first = saved.saved.value.single()
            clock.instant = clock.instant.plusSeconds(1)
            vm.onEvent(SavedEvent.SaveWotd)

            assertThat(awaitItem()).isInstanceOf(SavedEffect.Toast::class.java)
            expectNoEvents()
            assertThat(saved.saved.value).containsExactly(first)
        }
    }

    @Test
    fun `Save today's word saves the word of the current day`() = runTest {
        val byDate = object : WordOfTheDayRepository {
            override fun observe(date: LocalDate): Flow<Entry?> = flowOf(wordFor(date))
            override suspend fun forDate(date: LocalDate): Entry? = wordFor(date)
            fun wordFor(date: LocalDate) = if (date == LocalDate.parse("2026-10-19")) SampleData.tashiEntry else SampleData.nyingjeEntry
        }
        val vm = viewModel(wordOfTheDay = byDate)

        clock.instant = Instant.parse("2026-10-20T09:00:00Z")
        vm.onEvent(SavedEvent.SaveWotd)

        assertThat(saved.saved.value.single().entryId).isEqualTo(SampleData.nyingje.id)
    }

    @Test
    fun `SaveWotd with a deleted last-used folder saves without a folder`() = runTest {
        prefs.prefs.value = UserPrefs(lastUsedFolderId = 9)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.SaveWotd)

            assertThat(awaitItem()).isEqualTo(
                SavedEffect.Toast(UiText(R.string.saved_simple, listOf(tashi.headword)), R.string.change, SavedEvent.ChangeFolder(tashi.id)),
            )
        }
        assertThat(saved.saved.value.single().folderId).isNull()
    }

    @Test
    fun `Change opens the folder picker and picking files the word there`() = runTest {
        prefs.prefs.value = UserPrefs(lastUsedFolderId = 2)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.SaveWotd)
            skipItems(1) // "Saved བཀྲ་ཤིས་ to Daily"

            vm.onEvent(SavedEvent.ChangeFolder(tashi.id))

            assertThat(vm.uiState.value.folderPicker).isEqualTo(
                FolderPickerUi(folders = listOf(FolderUi(1, "Dharma", 0), FolderUi(2, "Daily", 1)), selectedId = 2),
            )

            vm.onEvent(SavedEvent.PickFolder(1))

            assertThat(awaitItem()).isEqualTo(SavedEffect.Toast(UiText(R.string.saved_to_folder, listOf(tashi.headword, "Dharma"))))
        }
        assertThat(saved.saved.value.single().folderId).isEqualTo(1)
        assertThat(prefs.prefs.value.lastUsedFolderId).isEqualTo(1)
        assertThat(vm.uiState.value.folderPicker).isNull()
    }

    @Test
    fun `a folder created in the picker gets the word, and a duplicate shows folder_exists`() = runTest {
        val vm = viewModel()
        vm.onEvent(SavedEvent.SaveWotd)
        vm.onEvent(SavedEvent.ChangeFolder(tashi.id))
        vm.onEvent(SavedEvent.StartCreateFolder)
        assertThat(vm.uiState.value.folderPicker?.creating).isTrue()

        vm.onEvent(SavedEvent.CreateFolder("Daily"))
        assertThat(vm.uiState.value.folderPicker?.createError).isEqualTo(UiText(R.string.folder_exists, listOf("Daily")))

        vm.onEvent(SavedEvent.CreateFolder("Grammar"))

        val grammar = saved.folders.value.single { it.name == "Grammar" }
        assertThat(saved.saved.value.single().folderId).isEqualTo(grammar.id)
        assertThat(vm.uiState.value.folderPicker).isNull()
    }

    @Test
    fun `the picker closes on dismiss and ignores a word that is no longer saved`() = runTest {
        val vm = viewModel()
        vm.onEvent(SavedEvent.ChangeFolder(tashi.id))
        assertThat(vm.uiState.value.folderPicker).isNull()

        vm.onEvent(SavedEvent.SaveWotd)
        vm.onEvent(SavedEvent.ChangeFolder(tashi.id))
        vm.onEvent(SavedEvent.DismissFolderPicker)
        assertThat(vm.uiState.value.folderPicker).isNull()
    }

    // Translations (1y)

    private val boToEn = LangPair(Lang.BO, Lang.EN)
    private val enToBo = LangPair(Lang.EN, Lang.BO)
    private fun record(id: Long, pair: LangPair, source: String, translation: String, provider: Provider, at: String) =
        TranslationRecord(id, pair, source, translation, provider, Instant.parse(at))

    private val howAreYou =
        record(1, boToEn, "ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།", "How are you?", Provider.GOOGLE, "2026-10-19T08:00:00Z")
    private val mayAll = record(
        2,
        enToBo,
        "May all beings be happy",
        "སེམས་ཅན་ཐམས་ཅད་བདེ་བ་དང་ལྡན་པར་ཤོག",
        Provider.AZURE,
        "2026-10-16T08:00:00Z",
    )
    private val thankYou = record(3, boToEn, "བཀའ་དྲིན་ཆེ།", "Thank you.", Provider.GOOGLE, "2026-09-01T08:00:00Z")

    private fun ui(record: TranslationRecord) = SavedTranslationUi(
        id = record.id,
        source = record.source,
        translation = record.translation,
        pair = record.pair,
        sourceIsTibetan = record.pair.source != Lang.EN,
    )

    @Test
    fun `translation count and groups come from the saved translations`() = runTest {
        translations.records.value = listOf(thankYou, howAreYou, mayAll)
        val vm = viewModel()

        with(vm.uiState.value) {
            assertThat(translationCount).isEqualTo(3)
            assertThat(translationGroups).containsExactly(
                SavedTranslationGroupUi(SavedPeriod.TODAY, listOf(ui(howAreYou))),
                SavedTranslationGroupUi(SavedPeriod.THIS_WEEK, listOf(ui(mayAll))),
                SavedTranslationGroupUi(SavedPeriod.EARLIER, listOf(ui(thankYou))),
            ).inOrder()
        }
    }

    @Test
    fun `remove then undo restores the row`() = runTest {
        translations.records.value = listOf(howAreYou, mayAll)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.RemoveTranslation(howAreYou.id))
            val removed = awaitItem() as SavedEffect.TranslationRemoved
            assertThat(removed.record).isEqualTo(howAreYou)
            assertThat(vm.uiState.value.translationCount).isEqualTo(1)

            vm.onEvent(SavedEvent.UndoRemoveTranslation(removed.record))
            assertThat(vm.uiState.value.translationCount).isEqualTo(2)
            assertThat(translations.records.value).contains(howAreYou)
        }
    }

    @Test
    fun `open emits the translate request with the saved result`() = runTest {
        translations.records.value = listOf(howAreYou, mayAll)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SavedEvent.OpenTranslation(mayAll.id))
            assertThat(awaitItem()).isEqualTo(
                SavedEffect.OpenTranslate(
                    TranslateRequest(
                        enToBo,
                        "May all beings be happy",
                        TranslatedText("སེམས་ཅན་ཐམས་ཅད་བདེ་བ་དང་ལྡན་པར་ཤོག", cached = false, provider = Provider.AZURE),
                    ),
                ),
            )
            // A row removed meanwhile (another screen) opens nothing.
            vm.onEvent(SavedEvent.OpenTranslation(99))
            expectNoEvents()
        }
    }

    @Test
    fun `the words tab is unchanged`() = runTest {
        // Translations alone: the Words tab is still empty, with no search field.
        translations.records.value = listOf(howAreYou)
        val vm = viewModel()

        with(vm.uiState.value) {
            assertThat(tab).isEqualTo(SavedTab.WORDS)
            assertThat(totalCount).isEqualTo(0)
            assertThat(isEmpty).isTrue()
            assertThat(showSearch).isFalse()
            assertThat(translationCount).isEqualTo(1)
        }
    }

    @Test
    fun `the chosen tab survives process death`() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)

        vm.onEvent(SavedEvent.SelectTab(SavedTab.TRANSLATIONS))
        assertThat(vm.uiState.value.tab).isEqualTo(SavedTab.TRANSLATIONS)

        val restored = viewModel(savedState = SavedStateHandle(savedState.keys().associateWith { savedState[it] }))
        assertThat(restored.uiState.value.tab).isEqualTo(SavedTab.TRANSLATIONS)
    }

    @Test
    fun `a failing translations table is recorded and leaves the words working`() = runTest {
        saved.save(tashi, folderId = null)
        val failing = object : SavedTranslationsRepository by translations {
            override fun observe(): Flow<List<TranslationRecord>> = flow { throw IOException("disk") }
        }
        val vm = viewModel(savedTranslations = failing)

        with(vm.uiState.value) {
            assertThat(totalCount).isEqualTo(1)
            assertThat(translationCount).isEqualTo(0)
        }
        assertThat(errors.recorded).hasSize(1)
    }
}
