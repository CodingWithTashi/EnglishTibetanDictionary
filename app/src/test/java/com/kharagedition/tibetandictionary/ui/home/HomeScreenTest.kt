package com.kharagedition.tibetandictionary.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData.boBo
import com.kharagedition.tibetandictionary.SampleData.boEn
import com.kharagedition.tibetandictionary.SampleData.directions
import com.kharagedition.tibetandictionary.SampleData.enBo
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.ui.model.LearnStatsUi
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<HomeEvent>()
    private var state by mutableStateOf(HomeUiState(greeting = TimeOfDay.AFTERNOON))

    private val wotd = WordOfTheDayUi(
        entryId = 11,
        date = "19 Oct",
        headword = "བཀྲ་ཤིས་",
        wylie = "bkra shis",
        gloss = "auspicious; good fortune, blessing",
        hasAudio = true,
        audioFile = "bkra_shis.ogg",
        isSaved = false,
    )

    /** Only what Home always has: the greeting, the field and the switcher. */
    private val bare = HomeUiState(
        greeting = TimeOfDay.AFTERNOON,
        slots = DirectionSlots(fixed = directions, overflow = emptyList()),
        selectedDirection = boEn,
    )

    private val full = bare.copy(
        streakDays = 12,
        wotd = wotd,
        recents = listOf(RecentChipUi("སྙིང་རྗེ"), RecentChipUi("compassion")),
        micAvailable = true,
    )

    private fun show(initial: HomeUiState) {
        state = initial
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                HomeContent(state, onEvent = { events += it })
            }
        }
    }

    @Test
    fun `a full home shows the greeting, streak, mic, switcher, word of the day and recents`() {
        show(full)

        compose.onNodeWithText("Good afternoon", substring = true).assertExists()
        compose.onNodeWithTag("home_streak").assertExists()
        compose.onNodeWithText("12-day streak").assertExists()
        compose.onNodeWithTag("home_search_field").assertExists()
        compose.onNodeWithTag("search_mic").assertExists()
        compose.onNodeWithTag("direction_segment_${boBo.id}").assertExists()
        compose.onNodeWithTag("home_wotd").assertExists()
        compose.onNodeWithText("bkra shis").assertExists()
        compose.onNodeWithContentDescription("Play pronunciation").assertExists()
        compose.onNodeWithText("Recent").assertExists()
        compose.onNodeWithTag("home_recent_0").assertExists()
        compose.onNodeWithTag("home_recent_1").assertExists()
        // The whole page scrolls, so large text never clips (Review Focus 5).
        compose.onNodeWithTag("home_screen").assert(hasScrollAction())
    }

    @Test
    fun `a bare home hides the streak, mic, word of the day, recents and stats`() {
        show(bare)

        compose.onNodeWithTag("home_search_field").assertExists()
        compose.onNodeWithTag("home_streak").assertDoesNotExist()
        compose.onNodeWithTag("search_mic").assertDoesNotExist()
        compose.onNodeWithTag("home_wotd").assertDoesNotExist()
        compose.onNodeWithText("Recent").assertDoesNotExist()
        compose.onNodeWithTag("home_recent_0").assertDoesNotExist()
        compose.onNodeWithTag("home_stats").assertDoesNotExist()
    }

    @Test
    fun `the streak pill starts at two days`() {
        show(bare.copy(streakDays = 1))
        compose.onNodeWithTag("home_streak").assertDoesNotExist()

        state = bare.copy(streakDays = 2)
        compose.onNodeWithTag("home_streak").assertExists()
        compose.onNodeWithText("2-day streak").assertExists()
    }

    @Test
    fun `the stats card stays hidden while Learn is off`() {
        show(full.copy(stats = LearnStatsUi(learnedThisWeek = 9, dueCount = 4, weekFlags = List(7) { it < 5 })))

        compose.onNodeWithTag("home_stats").assertDoesNotExist()
    }

    @Test
    fun `the word of the day leaves out a missing Wylie and speaker`() {
        show(full.copy(wotd = wotd.copy(wylie = null, hasAudio = false)))

        compose.onNodeWithTag("home_wotd").assertExists()
        compose.onNodeWithText("bkra shis").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play pronunciation").assertDoesNotExist()
    }

    @Test
    fun `the More slot shows only with overflow directions`() {
        show(full)
        compose.onNodeWithTag("direction_segment_more").assertDoesNotExist()

        state = full.copy(slots = DirectionSlots(fixed = listOf(boEn, enBo), overflow = listOf(boBo)))
        compose.onNodeWithTag("direction_segment_more").assertExists()
    }

    @Test
    fun `taps become events`() {
        show(full)

        compose.onNodeWithTag("home_search_field").performClick()
        compose.onNodeWithTag("search_mic").performClick()
        compose.onNodeWithTag("direction_segment_${enBo.id}").performClick()
        compose.onNodeWithContentDescription("Play pronunciation").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Share").performScrollTo().performClick()
        compose.onNodeWithText("Open").performScrollTo().performClick()
        // The recents sit below the fold of the default test screen: scroll the whole page up to reach them.
        compose.onNodeWithTag("home_screen").performTouchInput { swipeUp() }
        compose.onNodeWithTag("home_recent_1").performClick()

        assertThat(events).containsExactly(
            HomeEvent.OpenSearch,
            HomeEvent.VoiceSearch,
            HomeEvent.SelectDirection(enBo.id),
            HomeEvent.WotdPlay,
            HomeEvent.WotdToggleSave,
            HomeEvent.WotdShare,
            HomeEvent.WotdOpen,
            HomeEvent.RecentTap("compassion"),
        ).inOrder()
    }

    private val translateCard = TranslateCardUi(LangPair(Lang.BO, Lang.EN), remaining = 7, limit = 10)

    @Test
    fun `tapping the translate card sends OpenTranslate`() {
        show(full.copy(translateCard = translateCard))

        compose.onNodeWithTag("home_translate_card").performScrollTo().performClick()

        assertThat(events).containsExactly(HomeEvent.OpenTranslate)
    }

    @Test
    fun `the card shows 7 left today`() {
        show(bare.copy(translateCard = translateCard))

        compose.onNodeWithText("Translate a sentence").assertExists()
        compose.onNodeWithText("Tibetan ⇄ English · 7 left today").assertExists()
    }

    @Test
    fun `without a user the card leaves out the count, and without a card there is none`() {
        show(bare.copy(translateCard = translateCard.copy(remaining = null, limit = null)))
        compose.onNodeWithText("Tibetan ⇄ English").assertExists()

        state = bare
        compose.onNodeWithTag("home_translate_card").assertDoesNotExist()
    }
}
