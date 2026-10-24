package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData.boEn
import com.kharagedition.tibetandictionary.SampleData.directions
import com.kharagedition.tibetandictionary.SampleData.enBo
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.translate.UsagePillUi
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
    fun `the end of the list asks again after each page and stops when paging ends`() {
        val paging = base.copy(
            query = query("bkra"),
            phase = SearchPhase.Results,
            results = listOf(tashi, tashiDelek),
            resultCount = 2,
            hasMore = true,
            settledQuery = "bkra",
            searchId = 1,
            pagesLoaded = 1,
        )
        show(paging)
        compose.waitForIdle()
        assertThat(events).containsExactly(SearchEvent.LoadMore)

        // A page landed (even one that listed nothing new): the trailing row, still in view, asks again.
        state = paging.copy(pagesLoaded = 2)
        compose.waitForIdle()
        assertThat(events).containsExactly(SearchEvent.LoadMore, SearchEvent.LoadMore)

        // A failed or empty page ends paging: the trailing skeleton goes and nothing more is asked.
        state = paging.copy(pagesLoaded = 2, hasMore = false)
        compose.onNodeWithTag("search_load_more").assertDoesNotExist()
        compose.onNodeWithTag("search_results_count").assertTextEquals("2 results")
        assertThat(events).hasSize(2)
    }

    @Test
    fun `a new settled search starts the list at the top`() {
        val rows = (1..40L).map { tashi.copy(entryId = 100 + it, wylie = "bkra shis $it") }
        val first = base.copy(
            query = query("bkra"),
            phase = SearchPhase.Results,
            results = rows,
            resultCount = rows.size,
            settledQuery = "bkra",
            searchId = 1,
            pagesLoaded = 1,
        )
        show(first)
        compose.onNodeWithTag("search_results").performScrollToIndex(rows.lastIndex)
        compose.onNodeWithTag("result_row_101").assertDoesNotExist()

        // The same search, with another page: the position is kept.
        state = first.copy(pagesLoaded = 2)
        compose.onNodeWithTag("result_row_140").assertIsDisplayed()

        val others = (1..40L).map { tashi.copy(entryId = 200 + it, wylie = "bkra shis $it pa") }
        state = first.copy(
            query = query("bkra shis"),
            results = others,
            settledQuery = "bkra shis",
            searchId = 2,
        )
        compose.onNodeWithTag("result_row_201").assertIsDisplayed()
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
                settledQuery = "bkrashi",
                phase = SearchPhase.NoResults,
                suggestions = listOf(SuggestionUi(11, boEn.id, "བཀྲ་ཤིས་", true, "bkra shis · auspicious")),
                alternateDirection = enBo,
                noMatchHint = NoMatchHint.Wylie,
            ),
        )

        compose.onNodeWithText("No match for “bkrashi”").assertExists()
        compose.onNodeWithText("Wylie syllables are separated by spaces. Did you mean:").assertExists()
        compose.onNodeWithText("bkra shis · auspicious").assertExists()
        // The label keeps the ⅓-em gap between the arrow and the Tibetan token that 1k draws.
        compose.onNodeWithTag("search_try_alternate").assertTextEquals("Try EN →\u2004བོད instead")

        compose.onNodeWithTag("suggestion_row_11").performClick()
        // The no-results body scrolls; on the small test screen the button sits below the fold.
        compose.onNodeWithTag("search_try_alternate").performScrollTo().performClick()

        assertThat(events)
            .containsExactly(SearchEvent.SuggestionTap(11, boEn.id), SearchEvent.TryAlternateDirection)
            .inOrder()
    }

    @Test
    fun `no results with nothing to suggest show only the message`() {
        show(
            base.copy(
                query = query("zzzz"),
                settledQuery = "zzzz",
                phase = SearchPhase.NoResults,
                noMatchHint = NoMatchHint.English,
            ),
        )

        compose.onNodeWithText("No match for “zzzz”").assertExists()
        compose.onNodeWithText("Check the spelling or try a shorter word.").assertExists()
        compose.onNodeWithTag("search_try_alternate").assertDoesNotExist()
    }

    @Test
    fun `the no-match title names the query that was searched, not the one being typed`() {
        show(base.copy(query = query("zzzzq"), settledQuery = "zzzz", phase = SearchPhase.NoResults))

        compose.onNodeWithText("No match for “zzzz”").assertExists()
        compose.onNodeWithText("No match for “zzzzq”").assertDoesNotExist()
    }

    // Smart translate

    private val sevenLeft = UsagePillUi(remaining = 7, limit = 10, low = false)

    private fun withResults(smart: SmartTranslateUi?, phraseWords: List<SearchResultUi> = emptyList()) = base.copy(
        query = query("བཀྲ་ཤིས"),
        phase = SearchPhase.Results,
        results = listOf(tashi),
        resultCount = 1,
        settledQuery = "བཀྲ་ཤིས",
        smartTranslate = smart,
        phraseWords = phraseWords,
    )

    @Test
    fun `the translate offer shows what is left today and its tap translates`() {
        show(withResults(SmartTranslateUi.Offer("བཀྲ་ཤིས", Lang.EN, instead = false, sevenLeft, phrase = false)))

        compose.onNodeWithText("Translate “བཀྲ་ཤིས” → English").assertExists()
        compose.onNodeWithText("7 of 10 left today").assertExists()
        compose.onNodeWithTag("search_translate_offer").performClick()

        assertThat(events).containsExactly(SearchEvent.TranslateQuery)
    }

    @Test
    fun `a phrase offer has a Translate button`() {
        val low = UsagePillUi(remaining = 2, limit = 10, low = true)
        show(withResults(SmartTranslateUi.Offer("ཁྱེད་རང་བདེ་པོ་ཡིན།", Lang.EN, instead = false, low, phrase = true)))

        compose.onNodeWithText("Tap to translate → English").assertExists()
        compose.onNodeWithText("2 of 10 left today").assertExists()
        compose.onNodeWithTag("search_translate_button").performClick()

        assertThat(events).containsExactly(SearchEvent.TranslateQuery)
    }

    @Test
    fun `a cached quick translation says it was not counted and opens in Translate`() {
        val text = TranslatedText("Good luck", cached = true, provider = Provider.GOOGLE)
        show(
            withResults(
                SmartTranslateUi.Result(text, TranslationText.words("Good luck", Lang.EN), sevenLeft),
                phraseWords = listOf(tashiDelek),
            ),
        )

        compose.onNodeWithTag("search_translate_result").assertExists()
        compose.onNodeWithText("From cache · not counted").assertExists()
        compose.onNodeWithText("7 of 10 left today").assertDoesNotExist()
        compose.onNodeWithText("In the dictionary").assertExists()
        compose.onNodeWithTag("search_phrase_row_12").performClick()
        compose.onNodeWithTag("search_translate_open").performClick()

        assertThat(events).containsExactly(SearchEvent.OpenResult(12), SearchEvent.OpenInTranslate).inOrder()
    }

    @Test
    fun `a translated result shows the usage line and its words search`() {
        val text = TranslatedText("Good luck", cached = false, provider = Provider.AZURE)
        show(withResults(SmartTranslateUi.Result(text, TranslationText.words("Good luck", Lang.EN), sevenLeft)))

        compose.onNodeWithText("7 of 10 left today").assertExists()
        compose.onNodeWithText("Machine translation · Microsoft").assertExists()
        compose.onNodeWithTag("search_translate_word_0").performClick()

        assertThat(events).containsExactly(SearchEvent.SmartWordTap(TranslationText.words("Good luck", Lang.EN)[0]))
    }

    @Test
    fun `no results puts the instead offer under did-you-mean`() {
        show(
            base.copy(
                query = query("bkra shiz"),
                phase = SearchPhase.NoResults,
                settledQuery = "bkra shiz",
                smartTranslate = SmartTranslateUi.Offer(
                    "bkra shiz", Lang.BO, instead = true, sevenLeft, phrase = false,
                ),
            ),
        )

        compose.onNodeWithText("Translate instead").assertExists()
        compose.onNodeWithTag("search_translate_offer").performClick()

        assertThat(events).containsExactly(SearchEvent.TranslateQuery)
    }

    @Test
    fun `Pro's blocked row is the fair-use limit and offers nothing`() {
        show(withResults(SmartTranslateUi.Blocked(Plan.PRO, UsagePillUi(0, 300, low = true, unlimited = true))))

        compose.onNodeWithText("Today's fair-use limit reached").assertExists()
        compose.onNodeWithText("Resets at midnight").assertExists()
        compose.onNodeWithTag("search_translate_blocked").performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `Pro's usage line says unlimited today`() {
        val unlimited = UsagePillUi(remaining = 297, limit = 300, low = false, unlimited = true)
        show(withResults(SmartTranslateUi.Offer("བཀྲ་ཤིས", Lang.EN, instead = false, unlimited, phrase = false)))

        compose.onNodeWithText("Unlimited today").assertExists()
    }

    @Test
    fun `too long and limit rows open Translate, sign in or Pro`() {
        show(withResults(SmartTranslateUi.TooLong(length = 132, maxChars = 100, sevenLeft)))
        compose.onNodeWithText("132 / 100 characters").assertExists()
        compose.onNodeWithTag("search_translate_too_long").assertExists()
        compose.onNodeWithTag("search_translate_open").performClick()

        val none = UsagePillUi(remaining = 0, limit = 3, low = true)
        state = withResults(SmartTranslateUi.Blocked(Plan.GUEST, none))
        compose.onNodeWithText("Sign in to translate").assertExists()
        compose.onNodeWithTag("search_translate_blocked").performClick()

        state = withResults(SmartTranslateUi.Blocked(Plan.FREE, none.copy(limit = 10)))
        compose.onNodeWithText("Daily limit reached").assertExists()
        compose.onNodeWithTag("search_translate_blocked").performClick()

        assertThat(events)
            .containsExactly(SearchEvent.OpenInTranslate, SearchEvent.SmartSignIn, SearchEvent.SmartUpgrade)
            .inOrder()
    }
}
