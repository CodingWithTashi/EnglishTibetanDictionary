package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.domain.SavedPeriod
import com.kharagedition.tibetandictionary.ui.components.AppToastHost
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.Toaster
import com.kharagedition.tibetandictionary.ui.entry.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SavedEvent>()

    private val folders = listOf(FolderUi(1, "Dharma", 2), FolderUi(2, "Daily", 1))

    private val tashi = SavedItemUi(1, "བཀྲ་ཤིས་", true, AnnotatedString("bkra shis · auspicious"), folderId = 1)
    private val house = SavedItemUi(2, "house", false, AnnotatedString("ཁང་པ།"), folderId = 2)
    private val nyingje = SavedItemUi(3, "སྙིང་རྗེ་", true, AnnotatedString("snying rje · compassion"), folderId = 1)

    private val populated = SavedUiState(
        loading = false,
        totalCount = 3,
        folders = folders,
        groups = listOf(
            SavedGroupUi(SavedPeriod.TODAY, listOf(tashi, house)),
            SavedGroupUi(SavedPeriod.EARLIER, listOf(nyingje)),
        ),
    )

    private val wotd = WordOfTheDayUi(
        entryId = 1,
        date = "19 Oct",
        headword = "བཀྲ་ཤིས་",
        wylie = "bkra shis",
        gloss = "auspicious",
        hasAudio = false,
        audioFile = null,
        isSaved = false,
    )

    private val empty = SavedUiState(loading = false, wotd = wotd)

    private var state by mutableStateOf(SavedUiState())
    private var shown = false

    /** Shows [newState]; later calls in a test swap the state on the same content. */
    private fun show(newState: SavedUiState) {
        state = newState
        if (shown) {
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            DictTheme(darkTheme = false) {
                SavedContent(state, onEvent = { events += it })
            }
        }
    }

    private fun heartOf(entryId: Long) =
        compose.onNode(hasContentDescription("Remove from Saved") and hasAnyAncestor(hasTestTag("saved_row_$entryId")))

    // Populated

    @Test
    fun `saved words show the count, folder chips and rows in sections`() {
        show(populated)

        compose.onNodeWithTag("saved_screen").assertExists()
        compose.onNodeWithText("3 words").assertIsDisplayed()
        compose.onNodeWithTag("folder_chip_all").assertIsSelected()
        compose.onNodeWithText("All 3").assertIsDisplayed()
        compose.onNodeWithTag("folder_chip_1").assert(!isSelected())
        compose.onNodeWithText("Dharma 2").assertIsDisplayed()
        compose.onNodeWithText("Daily 1").assertExists()
        compose.onNodeWithTag("saved_add_folder").assertExists()
        compose.onNodeWithTag("saved_group_today").assertIsDisplayed()
        compose.onNodeWithTag("saved_group_week").assertDoesNotExist()
        compose.onNodeWithTag("saved_group_earlier").assertExists()
        compose.onNodeWithTag("saved_row_1").assertIsDisplayed()
        compose.onNodeWithText("bkra shis · auspicious").assertIsDisplayed()
        compose.onNodeWithText("house").assertIsDisplayed()
        compose.onNodeWithTag("saved_empty_cta").assertDoesNotExist()
        compose.onNodeWithTag("saved_no_matches").assertDoesNotExist()
    }

    @Test
    fun `the selected folder chip is marked`() {
        show(populated.copy(selectedFolderId = 2))

        compose.onNodeWithTag("folder_chip_2").assertIsSelected()
        compose.onNodeWithTag("folder_chip_all").assert(!isSelected())
    }

    @Test
    fun `chips select a folder or all, and the add chip opens the new-folder sheet`() {
        show(populated)

        compose.onNodeWithTag("folder_chip_2").performClick()
        compose.onNodeWithTag("folder_chip_all").performClick()
        compose.onNodeWithTag("saved_add_folder").performClick()

        assertThat(events).containsExactly(
            SavedEvent.SelectFolder(2),
            SavedEvent.SelectFolder(null),
            SavedEvent.StartAddFolder,
        ).inOrder()
    }

    @Test
    fun `tapping a row opens it and its heart removes it`() {
        show(populated)

        compose.onNodeWithTag("saved_row_2").performClick()
        heartOf(1).performClick()

        assertThat(events).containsExactly(SavedEvent.OpenEntry(2), SavedEvent.Unsave(1)).inOrder()
    }

    @Test
    fun `the search field shows from eight saved words on and sends the query`() {
        show(populated.copy(totalCount = 7))
        compose.onNodeWithTag("saved_search").assertDoesNotExist()

        show(populated.copy(totalCount = 8))
        compose.onNodeWithTag("saved_search").assertIsDisplayed()
        compose.onNodeWithText("Search saved words").assertIsDisplayed()
        compose.onNodeWithTag("search_field").performTextInput("bkra")

        assertThat(events).containsExactly(SavedEvent.QueryChanged("bkra"))
    }

    @Test
    fun `a filter that leaves nothing shows No matches instead of the list`() {
        show(populated.copy(totalCount = 9, query = "zzz", groups = emptyList()))

        compose.onNodeWithTag("saved_no_matches").assertIsDisplayed()
        compose.onNodeWithText("No matches").assertIsDisplayed()
        compose.onNodeWithTag("folder_chip_all").assertExists()
        compose.onNodeWithTag("saved_search").assertExists()
        compose.onNodeWithTag("saved_empty_cta").assertDoesNotExist()
        compose.onNodeWithText("Nothing saved yet").assertDoesNotExist()
    }

    // Empty

    @Test
    fun `nothing saved shows the empty state and saves today's word from it`() {
        show(empty)

        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
        compose.onNodeWithText("Tap the heart on any entry to keep it here.").assertIsDisplayed()
        compose.onNodeWithText("words", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("folder_chip_all").assertDoesNotExist()
        compose.onNodeWithTag("saved_search").assertDoesNotExist()
        compose.onNodeWithText("Save today's word བཀྲ་ཤིས་").assertIsDisplayed()

        compose.onNodeWithTag("saved_empty_cta").performClick()

        assertThat(events).containsExactly(SavedEvent.SaveWotd)
    }

    @Test
    fun `the empty state has no button when today's word is already saved or missing`() {
        show(empty.copy(wotd = wotd.copy(isSaved = true)))
        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
        compose.onNodeWithTag("saved_empty_cta").assertDoesNotExist()

        show(empty.copy(wotd = null))
        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
        compose.onNodeWithTag("saved_empty_cta").assertDoesNotExist()
    }

    @Test
    fun `the flashcards sentence shows only when Learn is on`() {
        compose.setContent {
            DictTheme(darkTheme = false) { SavedEmpty(wotd = null, onSaveWotd = {}, learnEnabled = true) }
        }

        compose.onNodeWithText(
            "Tap the heart on any entry to keep it here. Saved words become your flashcards in Learn.",
        ).assertIsDisplayed()
    }

    @Test
    fun `while loading only the title shows`() {
        show(SavedUiState())

        compose.onNodeWithText("Saved", substring = true).assertExists()
        compose.onNodeWithTag("folder_chip_all").assertDoesNotExist()
        compose.onNodeWithText("Nothing saved yet").assertDoesNotExist()
        compose.onNodeWithText("words", substring = true).assertDoesNotExist()
    }

    // Largest text settings (Review Focus 5)

    /** [content] at text size ×1.3, line spacing 2.3 and system font scale 2.0, on the default small test screen. */
    private fun showAtExtremeScale(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DictTheme(darkTheme = false, textScale = 1.3f, lineSpacing = LineSpacing.MAX) { content() }
            }
        }
    }

    @Test
    fun `at the largest text settings the empty state scrolls to its button`() {
        showAtExtremeScale { SavedContent(empty, onEvent = { events += it }) }

        compose.onNodeWithTag("saved_empty_cta").performScrollTo().assertIsDisplayed().performClick()

        assertThat(events).containsExactly(SavedEvent.SaveWotd)
    }

    @Test
    fun `at the largest text settings No matches scrolls into view`() {
        showAtExtremeScale { SavedContent(populated.copy(totalCount = 9, query = "zzz", groups = emptyList()), onEvent = {}) }

        compose.onNodeWithTag("saved_no_matches").performScrollTo().assertIsDisplayed()
    }

    // Effects

    @Test
    fun `effects navigate, and the toast actions send Undo and Change back`() {
        val channel = Channel<SavedEffect>(Channel.UNLIMITED)
        val effects = channel.receiveAsFlow()
        val opened = mutableListOf<Long>()
        compose.setContent {
            DictTheme(darkTheme = false) {
                val host = remember { SnackbarHostState() }
                val toaster = remember(host) { Toaster(host) }
                CompositionLocalProvider(LocalToaster provides toaster) {
                    Box(Modifier.fillMaxSize()) {
                        AppToastHost(host, rootHeightPx = 0, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                    SavedEffects(effects, onEvent = { events += it }, onOpenEntry = { opened += it })
                }
            }
        }

        channel.trySend(SavedEffect.NavigateEntry(7))
        channel.trySend(SavedEffect.Removed(1, folderId = 2))
        compose.onNodeWithText("Removed").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()

        channel.trySend(
            SavedEffect.Toast(
                UiText(R.string.saved_to_folder, listOf("བཀྲ་ཤིས་", "Daily")),
                R.string.change,
                SavedEvent.ChangeFolder(1),
            ),
        )
        compose.onNodeWithText("Saved བཀྲ་ཤིས་ to Daily").assertIsDisplayed()
        compose.onNodeWithText("Change").performClick()
        compose.waitForIdle()

        assertThat(opened).containsExactly(7L)
        assertThat(events).containsExactly(SavedEvent.UndoUnsave(1, folderId = 2), SavedEvent.ChangeFolder(1)).inOrder()
    }

    // Sheets

    @Test
    fun `the new-folder sheet edits the name, saves it and shows a duplicate error`() {
        show(populated.copy(newFolder = NewFolderUi(name = "Dharma", error = UiText(R.string.folder_exists, listOf("Dharma")))))

        compose.onNodeWithText("A folder named “Dharma” already exists").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithTag("saved_folder_name").performTextReplacement("Grammar")

        assertThat(events).containsExactly(
            SavedEvent.AddFolder("Dharma"),
            SavedEvent.NewFolderNameChanged("Grammar"),
        ).inOrder()
    }

    @Test
    fun `the folder picker files today's word in the picked folder`() {
        show(populated.copy(folderPicker = FolderPickerUi(folders, selectedId = 2)))

        compose.onNodeWithText("Save to folder").assertIsDisplayed()
        compose.onNodeWithText("Dharma").performClick()

        assertThat(events).containsExactly(SavedEvent.PickFolder(1))
    }
}
