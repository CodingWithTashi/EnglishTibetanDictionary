package com.kharagedition.tibetandictionary.ui.entry

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import com.kharagedition.tibetandictionary.core.audio.AudioSource
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.Example
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.RelatedEntry
import com.kharagedition.tibetandictionary.data.model.Relation
import com.kharagedition.tibetandictionary.data.model.Sense
import com.kharagedition.tibetandictionary.data.model.SenseItem
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeActivityRepository
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val dictionary = FakeDictionaryRepository()
    private val saved = FakeSavedRepository(
        initialFolders = listOf(Folder(1, "Dharma", 0, 0), Folder(2, "Daily", 1, 0)),
        clock = clock,
    )
    private val activity = FakeActivityRepository(clock)
    private val prefs = FakeUserPreferencesRepository()
    private val audio = RecordingAudioPlayer()

    private val tashi = SampleData.tashi

    /** A view model whose state is collected for the whole test, as the screen does. */
    private fun TestScope.viewModel(entryId: Long = tashi.id): EntryViewModel {
        val vm = EntryViewModel(entryId, dictionary, saved, activity, prefs, Optional.of(audio))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun add(entry: Entry) {
        dictionary.entries.value = dictionary.entries.value + (entry.summary.id to entry)
    }

    private fun EntryViewModel.entry(): EntryUi = checkNotNull(uiState.value.entry)

    // Loading and mapping

    @Test
    fun `an entry is read and mapped for display`() = runTest {
        val state = viewModel().uiState.value

        assertThat(state.loading).isFalse()
        assertThat(state.missing).isFalse()
        val entry = checkNotNull(state.entry)
        assertThat(entry.id).isEqualTo(tashi.id)
        assertThat(entry.direction).isEqualTo(SampleData.boEn)
        assertThat(entry.headword).isEqualTo("བཀྲ་ཤིས་")
        assertThat(entry.headwordIsTibetan).isTrue()
        assertThat(entry.wylie).isEqualTo("bkra shis")
        assertThat(entry.phonetic).isEqualTo("tashi")
        assertThat(entry.partsOfSpeech).isEmpty()
        assertThat(entry.label).isNull()
        assertThat(entry.isTruncated).isFalse()
        assertThat(entry.senses).containsExactly(
            SenseUi(1, null, "auspicious, fortunate, favourable", null),
            SenseUi(2, null, "good luck, good fortune, blessing", null),
            SenseUi(3, null, "a common personal name, \"Tashi\"", null),
        ).inOrder()
    }

    @Test
    fun `markEntryOpened is called once the entry is shown`() = runTest {
        viewModel()

        assertThat(activity.days.value[clock.today()]?.entriesOpened).isEqualTo(1)
    }

    @Test
    fun `a missing entry sets missing and shows a toast`() = runTest {
        val vm = viewModel(entryId = 42)

        assertThat(vm.uiState.value).isEqualTo(EntryUiState(loading = false, entry = null, missing = true))
        assertThat(activity.days.value).isEmpty()
        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(EntryEffect.Toast(UiText(R.string.entry_missing)))
        }
    }

    @Test
    fun `several parts of speech are abbreviated and shown on each sense`() = runTest {
        val entry = viewModel(SampleData.house.id).entry()

        assertThat(entry.headwordIsTibetan).isFalse()
        assertThat(entry.wylie).isNull()
        assertThat(entry.partsOfSpeech).containsExactly("n.", "vt.").inOrder()
        assertThat(entry.senses.map { it.number }).containsExactly(1, 2, 3, 4, 5).inOrder()
        assertThat(entry.senses.map { it.partOfSpeech }).containsExactly("n.", "n.", "n.", "n.", "vt.").inOrder()
        assertThat(entry.senses.first().example).isEqualTo(
            ExampleUi("a two-storey house", "ཐོག་བརྩེགས་གཉིས་ཅན་གྱི་ཁང་པ།", textIsTibetan = false),
        )
    }

    @Test
    fun `a single sense has no number and no per-sense part of speech`() = runTest {
        val entry = viewModel(SampleData.tashiBoBo.id).entry()

        assertThat(entry.partsOfSpeech).containsExactly("n.")
        assertThat(entry.senses).containsExactly(SenseUi(null, null, "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།", null))
    }

    @Test
    fun `sub-senses become cards with the sense number and the part of speech on the first`() = runTest {
        val items = listOf(item("to dwell"), item("to remain", example = "གནས་སྡོད།"))
        add(
            SampleData.houseEntry.copy(
                senses = listOf(
                    Sense(1, "vi", listOf("hon"), null, emptyList(), items),
                    Sense(2, "n", listOf("hon"), null, emptyList(), listOf(item("a dwelling"))),
                ),
            ),
        )

        val entry = viewModel(SampleData.house.id).entry()

        assertThat(entry.label).isEqualTo("hon")
        assertThat(entry.senses).containsExactly(
            SenseUi(1, "vi.", "to dwell", null),
            SenseUi(1, null, "to remain", ExampleUi("གནས་སྡོད།", null, textIsTibetan = true)),
            SenseUi(2, "n.", "a dwelling", null),
        ).inOrder()
    }

    @Test
    fun `audio is hidden when the entry has no audio file`() = runTest {
        val vm = viewModel()

        assertThat(vm.entry().hasAudio).isFalse()
        assertThat(vm.entry().audioFile).isNull()
        vm.onEvent(EntryEvent.PlayAudio)
        assertThat(audio.played).isEmpty()
    }

    @Test
    fun `PlayAudio plays the bundled pronunciation`() = runTest {
        add(SampleData.tashiEntry.copy(summary = tashi.copy(audioFile = "audio/bkra_shis.ogg")))
        val vm = viewModel()

        assertThat(vm.entry().hasAudio).isTrue()
        vm.onEvent(EntryEvent.PlayAudio)

        assertThat(audio.played).containsExactly(AudioSource.Asset("audio/bkra_shis.ogg"))
    }

    @Test
    fun `see-also links resolve their target and its direction`() = runTest {
        add(
            SampleData.houseEntry.copy(
                related = listOf(
                    RelatedEntry(SampleData.tashiBoBo.id, "བཀྲ་ཤིས་", "bkra shis", Relation.SEE_ALSO),
                    RelatedEntry(9_999_999, "dwelling", null, Relation.SYNONYM),
                    RelatedEntry(null, "home", null, Relation.SYNONYM),
                ),
            ),
        )

        val related = viewModel(SampleData.house.id).entry().related

        assertThat(related).containsExactly(
            RelatedUi(SampleData.tashiBoBo.id, SampleData.boBo, "བཀྲ་ཤིས་", isTibetan = true),
            // A target id that is not in the data is searched by headword instead.
            RelatedUi(null, SampleData.enBo, "dwelling", isTibetan = false),
            RelatedUi(null, SampleData.enBo, "home", isTibetan = false),
        ).inOrder()
    }

    @Test
    fun `related empty hides the see-also section`() = runTest {
        assertThat(viewModel(SampleData.nyingje.id).entry().related).isEmpty()
    }

    @Test
    fun `redirectOnlyEntry has no senses and keeps its see-also link`() = runTest {
        val redirect = Entry(
            summary = SampleData.tashiDelek.copy(id = 1_009_001, glossPreview = "", senseCount = 0),
            senses = emptyList(),
            related = listOf(RelatedEntry(tashi.id, tashi.headword, tashi.wylie, Relation.SEE_ALSO)),
        )
        add(redirect)

        val entry = viewModel(redirect.summary.id).entry()

        assertThat(entry.senses).isEmpty()
        assertThat(entry.related).containsExactly(RelatedUi(tashi.id, SampleData.boEn, tashi.headword, isTibetan = true))
    }

    // Saving

    @Test
    fun `saving files the word in the last-used folder and offers Change`() = runTest {
        prefs.prefs.value = UserPrefs(lastUsedFolderId = 2)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(EntryEvent.ToggleSave)

            val toast = awaitItem() as EntryEffect.Toast
            assertThat(toast.message).isEqualTo(UiText(R.string.saved_to_folder, listOf("བཀྲ་ཤིས་", "Daily")))
            assertThat(toast.action).isEqualTo(R.string.change)
            assertThat(saved.saved.value.single().folderId).isEqualTo(2L)
            assertThat(vm.uiState.value.isSaved).isTrue()
            assertThat(vm.uiState.value.savedFolderName).isEqualTo("Daily")

            checkNotNull(toast.onAction).invoke()
            val picker = checkNotNull(vm.uiState.value.folderPicker)
            assertThat(picker.selectedId).isEqualTo(2L)
            assertThat(picker.folders.map { it.name to it.count }).containsExactly("Dharma" to 0, "Daily" to 1).inOrder()
        }
    }

    @Test
    fun `saving without a folder says Saved and still offers Change`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(EntryEvent.ToggleSave)

            val toast = awaitItem() as EntryEffect.Toast
            assertThat(toast.message).isEqualTo(UiText(R.string.saved_simple, listOf("བཀྲ་ཤིས་")))
            assertThat(toast.action).isEqualTo(R.string.change)
            assertThat(saved.saved.value.single().folderId).isNull()
            assertThat(vm.uiState.value.savedFolderName).isNull()
        }
    }

    @Test
    fun `unsaving shows Removed and Undo restores the word in its folder`() = runTest {
        saved.saved.value = listOf(SampleData.savedTashi.copy(folderId = 1))
        val vm = viewModel()
        assertThat(vm.uiState.value.isSaved).isTrue()
        assertThat(vm.uiState.value.savedFolderName).isEqualTo("Dharma")

        vm.effects.test {
            vm.onEvent(EntryEvent.ToggleSave)

            val toast = awaitItem() as EntryEffect.Toast
            assertThat(toast.message).isEqualTo(UiText(R.string.removed))
            assertThat(toast.action).isEqualTo(R.string.undo)
            assertThat(saved.saved.value).isEmpty()
            assertThat(vm.uiState.value.isSaved).isFalse()

            checkNotNull(toast.onAction).invoke()
            assertThat(saved.saved.value.single().folderId).isEqualTo(1L)
            assertThat(vm.uiState.value.isSaved).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun `picking a folder files the saved word and remembers the folder`() = runTest {
        saved.saved.value = listOf(SampleData.savedTashi)
        val vm = viewModel()

        vm.onEvent(EntryEvent.ChangeFolder)
        assertThat(vm.uiState.value.folderPicker?.selectedId).isNull()
        vm.effects.test {
            vm.onEvent(EntryEvent.PickFolder(1))

            assertThat(awaitItem()).isEqualTo(
                EntryEffect.Toast(UiText(R.string.saved_to_folder, listOf("བཀྲ་ཤིས་", "Dharma"))),
            )
        }

        assertThat(saved.saved.value.single().folderId).isEqualTo(1L)
        assertThat(prefs.prefs.value.lastUsedFolderId).isEqualTo(1L)
        assertThat(vm.uiState.value.folderPicker).isNull()
        assertThat(vm.uiState.value.savedFolderName).isEqualTo("Dharma")
    }

    @Test
    fun `a new folder is created and the word filed in it`() = runTest {
        saved.saved.value = listOf(SampleData.savedTashi)
        val vm = viewModel()
        vm.onEvent(EntryEvent.ChangeFolder)
        vm.onEvent(EntryEvent.StartCreateFolder)
        vm.onEvent(EntryEvent.NewFolderNameChanged("Grammar"))
        val picker = checkNotNull(vm.uiState.value.folderPicker)
        assertThat(picker.creating).isTrue()
        assertThat(picker.newFolderName).isEqualTo("Grammar")

        vm.onEvent(EntryEvent.CreateFolder("Grammar"))

        val grammar = saved.folders.value.single { it.name == "Grammar" }
        assertThat(saved.saved.value.single().folderId).isEqualTo(grammar.id)
        assertThat(prefs.prefs.value.lastUsedFolderId).isEqualTo(grammar.id)
        assertThat(vm.uiState.value.folderPicker).isNull()
        assertThat(vm.uiState.value.savedFolderName).isEqualTo("Grammar")
    }

    @Test
    fun `createFolderDuplicateShowsError until the name is edited`() = runTest {
        saved.saved.value = listOf(SampleData.savedTashi)
        val vm = viewModel()
        vm.onEvent(EntryEvent.ChangeFolder)
        vm.onEvent(EntryEvent.StartCreateFolder)
        vm.onEvent(EntryEvent.NewFolderNameChanged("daily"))

        vm.onEvent(EntryEvent.CreateFolder("daily"))

        val picker = checkNotNull(vm.uiState.value.folderPicker)
        assertThat(picker.creating).isTrue()
        assertThat(picker.createError).isNotEmpty()
        assertThat(saved.folders.value).hasSize(2)
        assertThat(saved.saved.value.single().folderId).isNull()

        vm.onEvent(EntryEvent.NewFolderNameChanged("daily words"))
        assertThat(vm.uiState.value.folderPicker?.createError).isNull()
    }

    @Test
    fun `cancelling a new folder keeps the picker open and dismissing closes it`() = runTest {
        val vm = viewModel()
        vm.onEvent(EntryEvent.ChangeFolder)
        vm.onEvent(EntryEvent.StartCreateFolder)
        vm.onEvent(EntryEvent.NewFolderNameChanged("Grammar"))

        vm.onEvent(EntryEvent.CancelCreateFolder)
        val picker = checkNotNull(vm.uiState.value.folderPicker)
        assertThat(picker.creating).isFalse()
        assertThat(picker.newFolderName).isEmpty()

        vm.onEvent(EntryEvent.DismissFolderPicker)
        assertThat(vm.uiState.value.folderPicker).isNull()
    }

    // Navigation and sharing

    @Test
    fun `OpenRelated with a null id searches the headword, otherwise opens the entry`() = runTest {
        val vm = viewModel()
        val (known, unknown) = vm.entry().related

        vm.effects.test {
            vm.onEvent(EntryEvent.OpenRelated(unknown))
            assertThat(awaitItem()).isEqualTo(EntryEffect.NavigateSearch("བཀྲ་ཤིས་རྟགས་བརྒྱད་"))

            vm.onEvent(EntryEvent.OpenRelated(known))
            assertThat(awaitItem()).isEqualTo(EntryEffect.NavigateEntry(SampleData.tashiDelek.id))
        }
    }

    @Test
    fun `back, copy, share and report correction become effects`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(EntryEvent.Back)
            assertThat(awaitItem()).isEqualTo(EntryEffect.NavigateBack)

            vm.onEvent(EntryEvent.Copy)
            assertThat(awaitItem()).isEqualTo(EntryEffect.CopyToClipboard("བཀྲ་ཤིས་ (bkra shis)"))

            vm.onEvent(EntryEvent.Share)
            assertThat(awaitItem()).isEqualTo(
                EntryEffect.ShareText(
                    "བཀྲ་ཤིས་ (bkra shis) — auspicious, fortunate, favourable · good luck, good fortune, blessing · " +
                        "a common personal name, \"Tashi\"",
                ),
            )

            vm.onEvent(EntryEvent.ReportCorrection)
            val email = awaitItem() as EntryEffect.OpenEmail
            assertThat(email.address).isEqualTo(Links.DEV_EMAIL)
            assertThat(email.subject).isEqualTo("Correction: བཀྲ་ཤིས་ (#1004211)")
            assertThat(email.body).contains("bo-en #1004211")
        }
    }

    @Test
    fun `an entry without Wylie copies and shares the headword alone`() = runTest {
        val vm = viewModel(SampleData.house.id)

        vm.effects.test {
            vm.onEvent(EntryEvent.Copy)
            assertThat(awaitItem()).isEqualTo(EntryEffect.CopyToClipboard("house"))

            vm.onEvent(EntryEvent.Share)
            assertThat(awaitItem()).isEqualTo(
                EntryEffect.ShareText("house — ཁང་པ། · ཁྱིམ་ཚང་། · རིགས་རྒྱུད། · ཁྱིམ། · གནས་ཚང་སྤྲོད་པ།"),
            )
        }
    }

    private fun item(gloss: String, example: String? = null) = SenseItem(
        subNumber = null,
        gloss = gloss,
        glossLang = "en",
        isTruncated = false,
        examples = listOfNotNull(example?.let { Example(it, null, null) }),
    )

    /** Records what would have been played. */
    private class RecordingAudioPlayer : AudioPlayer {
        val played = mutableListOf<AudioSource>()

        override fun play(source: AudioSource) {
            played += source
        }

        override fun stop() = Unit
    }
}
