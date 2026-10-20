package com.kharagedition.tibetandictionary.ui.entry

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<EntryEvent>()

    private val related = listOf(
        RelatedUi(1_004_219, SampleData.boEn, "བཀྲ་ཤིས་བདེ་ལེགས་", isTibetan = true),
        RelatedUi(null, SampleData.enBo, "auspicious", isTibetan = false),
    )

    private val tashi = EntryUi(
        id = 1_004_211,
        direction = SampleData.boEn,
        headword = "བཀྲ་ཤིས་",
        headwordIsTibetan = true,
        wylie = "bkra shis",
        phonetic = "tashi",
        partsOfSpeech = listOf("adj.", "n."),
        label = null,
        senses = listOf(
            SenseUi(1, null, "auspicious, fortunate, favourable", null),
            SenseUi(2, "n.", "a common personal name", null),
        ),
        related = related,
        hasAudio = true,
        audioFile = "audio/bkra_shis.ogg",
        isTruncated = true,
    )

    private fun show(state: EntryUiState) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                EntryContent(state, onEvent = { events += it })
            }
        }
    }

    @Test
    fun `a populated entry shows its headword, pronunciation, senses and note`() {
        show(EntryUiState(loading = false, entry = tashi))

        compose.onNodeWithText("བཀྲ་ཤིས་").assertIsDisplayed()
        compose.onNodeWithText("bkra shis").assertIsDisplayed()
        compose.onNodeWithText("· tashi").assertIsDisplayed()
        compose.onNodeWithText("adj. · n.").assertIsDisplayed()
        compose.onNodeWithText("auspicious, fortunate, favourable").assertExists()
        compose.onNodeWithText("This entry was shortened", substring = true).assertExists()
    }

    @Test
    fun `entry_audio is shown only when the entry has audio`() {
        show(EntryUiState(loading = false, entry = tashi))
        compose.onNodeWithTag("entry_audio").performClick()
        assertThat(events).containsExactly(EntryEvent.PlayAudio)
    }

    @Test
    fun `no audio button, Wylie line or see also when the entry has none`() {
        show(
            EntryUiState(
                loading = false,
                entry = tashi.copy(
                    wylie = null,
                    phonetic = null,
                    partsOfSpeech = emptyList(),
                    hasAudio = false,
                    audioFile = null,
                    related = emptyList(),
                    isTruncated = false,
                ),
            ),
        )

        compose.onNodeWithText("བཀྲ་ཤིས་").assertIsDisplayed()
        compose.onNodeWithTag("entry_audio").assertDoesNotExist()
        compose.onNodeWithText("bkra shis").assertDoesNotExist()
        compose.onNodeWithText("See also").assertDoesNotExist()
        compose.onNodeWithText("This entry was shortened", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tapping entry_save emits ToggleSave`() {
        show(EntryUiState(loading = false, entry = tashi))

        compose.onNodeWithTag("entry_save").performClick()

        assertThat(events).containsExactly(EntryEvent.ToggleSave)
    }

    @Test
    fun `the heart reads Saved when the word is saved`() {
        show(EntryUiState(loading = false, entry = tashi, isSaved = true))

        compose.onNodeWithContentDescription("Saved").assertIsDisplayed()
    }

    @Test
    fun `toolbar actions emit their events`() {
        show(EntryUiState(loading = false, entry = tashi))

        compose.onNodeWithTag("entry_back").assertContentDescriptionEquals("Back").performClick()
        compose.onNodeWithContentDescription("Copy").performClick()
        compose.onNodeWithContentDescription("Share").performClick()

        assertThat(events).containsExactly(EntryEvent.Back, EntryEvent.Copy, EntryEvent.Share).inOrder()
    }

    @Test
    fun `see-also rows open the related entry`() {
        show(EntryUiState(loading = false, entry = tashi))

        compose.onNodeWithTag("entry_related_1004219").performScrollTo().performClick()
        compose.onNodeWithTag("entry_related_search_1").performScrollTo().performClick()

        assertThat(events).containsExactly(EntryEvent.OpenRelated(related[0]), EntryEvent.OpenRelated(related[1])).inOrder()
    }

    @Test
    fun `a redirect-only entry shows see also without sense cards`() {
        show(EntryUiState(loading = false, entry = tashi.copy(senses = emptyList())))

        compose.onNodeWithTag("entry_sense_0").assertDoesNotExist()
        compose.onNodeWithTag("entry_related_1004219").assertExists()
    }

    @Test
    fun `report a correction emits ReportCorrection`() {
        show(EntryUiState(loading = false, entry = tashi))

        compose.onNodeWithTag("entry_report").performScrollTo().performClick()

        assertThat(events).containsExactly(EntryEvent.ReportCorrection)
    }

    @Test
    fun `a missing entry shows the notice and no entry actions`() {
        show(EntryUiState(loading = false, missing = true))

        compose.onNodeWithText("This entry is no longer in the dictionary.").assertIsDisplayed()
        compose.onNodeWithText("It may have been removed in a dictionary update", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("entry_save").assertDoesNotExist()
        compose.onNodeWithTag("entry_missing_back").performClick()
        assertThat(events).containsExactly(EntryEvent.Back)
    }

    @Test
    fun `at the largest text settings the missing notice scrolls to its back button`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DictTheme(darkTheme = false, textScale = 1.3f, lineSpacing = LineSpacing.MAX) {
                    EntryContent(EntryUiState(loading = false, missing = true), onEvent = { events += it })
                }
            }
        }

        compose.onNodeWithTag("entry_missing_back").performScrollTo().assertIsDisplayed().performClick()

        assertThat(events).containsExactly(EntryEvent.Back)
    }

    @Test
    fun `loading shows the skeleton and no entry actions`() {
        show(EntryUiState())

        compose.onNodeWithTag("entry_loading").assertExists()
        compose.onNodeWithTag("entry_save").assertDoesNotExist()
    }
}
