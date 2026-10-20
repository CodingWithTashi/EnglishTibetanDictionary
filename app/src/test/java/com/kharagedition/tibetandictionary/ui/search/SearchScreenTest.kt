package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData.boEn
import com.kharagedition.tibetandictionary.SampleData.directions
import com.kharagedition.tibetandictionary.SampleData.enBo
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SearchEvent>()
    private var state by mutableStateOf(SearchUiState())

    private val base = SearchUiState(
        slots = DirectionSlots(fixed = directions, overflow = emptyList()),
        selectedDirection = boEn,
        micAvailable = true,
    )

    private val tashi = SearchResultUi(
        entryId = 11,
        direction = boEn,
        headword = "བཀྲ་ཤིས་",
        headwordIsTibetan = true,
        wylie = "bkra shis",
        matchRange = 0..3,
        headwordMatchRange = null,
        gloss = "auspicious; good fortune, blessing",
        showBadge = false,
    )
    private val tashiDelek = tashi.copy(entryId = 12, headword = "བཀྲ་ཤིས་བདེ་ལེགས་", wylie = "bkra shis bde legs")

    private fun query(text: String) = TextFieldValue(text, TextRange(text.length))

    private fun show(initial: SearchUiState) {
        state = initial
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                SearchContent(state, onEvent = { events += it })
            }
        }
    }

    // Suggestions

    @Test
    fun `an empty query lists the recent searches with clear and the tip`() {
        show(
            base.copy(
                recents = listOf(
                    RecentRowUi(1, "སྙིང་རྗེ", true, boEn.id, openedHeadword = null, openedIsTibetan = false),
                    RecentRowUi(2, "house", false, enBo.id, openedHeadword = "ཁང་པ།", openedIsTibetan = true),
                ),
                showTip = true,
            ),
        )

        compose.onNodeWithText("Recent").assertExists()
        compose.onNodeWithText("ཁང་པ།").assertExists()
        compose.onNodeWithTag("search_tip").assertExists()

        compose.onNodeWithTag("search_recent_2").performClick()
        compose.onNodeWithTag("search_clear_recents").performClick()

        assertThat(events).containsExactly(SearchEvent.RecentTap("house", enBo.id), SearchEvent.ClearRecents).inOrder()
    }

    @Test
    fun `without recents or Wylie there is no recent list and no tip`() {
        show(base)

        compose.onNodeWithText("Recent").assertDoesNotExist()
        compose.onNodeWithTag("search_clear_recents").assertDoesNotExist()
        compose.onNodeWithTag("search_tip").assertDoesNotExist()
    }

    // Header

    @Test
    fun `the mic shows only while the query is empty and speech is available`() {
        show(base)

        compose.onNodeWithTag("search_mic").performClick()
        assertThat(events).containsExactly(SearchEvent.VoiceSearch)

        state = base.copy(query = query("bkra"), phase = SearchPhase.Results)
        compose.onNodeWithTag("search_mic").assertDoesNotExist()

        state = base.copy(micAvailable = false)
        compose.onNodeWithTag("search_mic").assertDoesNotExist()
    }

    @Test
    fun `typing, clearing, a segment and back become events`() {
        show(base)

        compose.onNodeWithTag("search_field").performTextInput("bkra")
        assertThat((events.last() as SearchEvent.QueryChanged).value.text).isEqualTo("bkra")

        state = base.copy(query = query("bkra"), phase = SearchPhase.Results)
        compose.onNodeWithTag("search_clear").performClick()
        compose.onNodeWithTag("direction_segment_${enBo.id}").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertThat(events.takeLast(3))
            .containsExactly(SearchEvent.Clear, SearchEvent.SelectDirection(enBo.id), SearchEvent.Back)
            .inOrder()
    }

    @Test
    fun `the switcher waits for the directions`() {
        show(base.copy(selectedDirection = null))

        compose.onNodeWithTag("direction_switcher").assertDoesNotExist()
        compose.onNodeWithTag("search_field").assertExists()
    }

    // Loading and results

    @Test
    fun `a slow search shows the skeleton without a count`() {
        show(base.copy(query = query("bkra"), phase = SearchPhase.Loading))

        compose.onNodeWithTag("search_loading").assertExists()
        compose.onNodeWithTag("search_results_count").assertDoesNotExist()
        compose.onNodeWithTag("search_recent_1").assertDoesNotExist()
    }

    @Test
    fun `results show the count and open on tap`() {
        show(
            base.copy(
                query = query("bkra"),
                phase = SearchPhase.Results,
                results = listOf(tashi, tashiDelek),
                resultCount = 2,
                highlightFirst = true,
            ),
        )

        compose.onNodeWithTag("search_results_count").assertTextEquals("2 results")
        compose.onNodeWithTag("result_row_12").performClick()

        assertThat(events).containsExactly(SearchEvent.OpenResult(12))
    }

    @Test
    fun `more results are announced with a plus and loaded at the end of the list`() {
        show(
            base.copy(
                query = query("bkra"),
                phase = SearchPhase.Results,
                results = listOf(tashi, tashiDelek),
                resultCount = 2,
                hasMore = true,
            ),
        )

        compose.onNodeWithTag("search_results_count").assertTextEquals("2+ results")
        compose.waitForIdle()
        assertThat(events).containsExactly(SearchEvent.LoadMore)
    }

    @Test
    fun `while the first search is pending nothing is listed`() {
        show(base.copy(query = query("bkra"), phase = SearchPhase.Results))

        compose.onNodeWithTag("search_results_count").assertDoesNotExist()
        compose.onNodeWithTag("search_tip").assertDoesNotExist()
        assertThat(events).isEmpty()
    }

    // No results

    @Test
    fun `no results offer did-you-mean rows and another direction`() {
        show(
            base.copy(
                query = query("bkrashi"),
                phase = SearchPhase.NoResults,
                suggestions = listOf(SuggestionUi(11, boEn.id, "བཀྲ་ཤིས་", true, "bkra shis · auspicious")),
                alternateDirection = enBo,
                noMatchHint = NoMatchHint.Wylie,
            ),
        )

        compose.onNodeWithText("No match for “bkrashi”").assertExists()
        compose.onNodeWithText("Wylie syllables are separated by spaces. Did you mean:").assertExists()
        compose.onNodeWithText("bkra shis · auspicious").assertExists()
        compose.onNodeWithTag("search_try_alternate").assertTextEquals("Try EN →བོད instead")

        compose.onNodeWithTag("suggestion_row_11").performClick()
        compose.onNodeWithTag("search_try_alternate").performClick()

        assertThat(events)
            .containsExactly(SearchEvent.SuggestionTap(11, boEn.id), SearchEvent.TryAlternateDirection)
            .inOrder()
    }

    @Test
    fun `no results with nothing to suggest show only the message`() {
        show(base.copy(query = query("zzzz"), phase = SearchPhase.NoResults, noMatchHint = NoMatchHint.English))

        compose.onNodeWithText("No match for “zzzz”").assertExists()
        compose.onNodeWithText("Check the spelling or try a shorter word.").assertExists()
        compose.onNodeWithTag("search_try_alternate").assertDoesNotExist()
    }
}
