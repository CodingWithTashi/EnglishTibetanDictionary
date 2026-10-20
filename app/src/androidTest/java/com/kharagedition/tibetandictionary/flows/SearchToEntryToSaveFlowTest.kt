package com.kharagedition.tibetandictionary.flows

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

/**
 * The core loop on the real dictionary (§9.3/§9.5): a Wylie search, the entry, the heart and the Saved tab; plus
 * the direction auto-switch for an English query and did-you-mean for a misspelt one. Prefs start onboarded with
 * the default direction `bo-en`, and `user.db` starts empty.
 */
@HiltAndroidTest
class SearchToEntryToSaveFlowTest : AppFlowTest() {

    @Before
    fun onHome() = waitForHome()

    @Test
    fun wylieSearchOpensTheEntryWhoseHeartSavesItToTheSavedTab() {
        openSearchFromHome()
        typeQuery("bkra")

        // The exact match leads the list; བཀྲ་ཤིས་ follows among the prefix hits. Each shows "bkra" highlighted.
        waitFor(resultRow(EXACT_HEADWORD))
        val exactTag = tagOf(resultRow(EXACT_HEADWORD))
        assertThat(tagOf(hasTestTagPrefix(RESULT_ROW))).isEqualTo(exactTag)
        assertWylieHighlighted(exactTag, "bkra")
        compose.onNodeWithTag("search_results").performScrollToNode(hasText(HEADWORD))
        val rowTag = tagOf(resultRow(HEADWORD))
        assertWylieHighlighted(rowTag, "bkra")
        val entryId = rowTag.removePrefix(RESULT_ROW).toLong()

        tapAndWait(hasTestTag(rowTag), hasTestTag("entry_screen"))
        waitFor(isHeading() and hasText(HEADWORD) and hasAnyAncestor(hasTestTag("entry_screen")))

        compose.onNodeWithTag("entry_save").performClick()
        // Toast "Saved བཀྲ་ཤིས་" with its "Change" action; the heart turns to "Saved".
        waitFor(hasText("Saved $HEADWORD", substring = true))
        waitFor(hasText("Change") and hasClickAction())
        waitFor(hasTestTag("entry_save") and hasContentDescription("Saved"))
        awaitSavedInDatabase(entryId)

        leaveEntry()
        leaveSearch()
        openTab("saved", "saved_screen")
        waitFor(hasTestTag("saved_row_$entryId") and hasText(HEADWORD, substring = true))
    }

    @Test
    fun englishQuerySwitchesTheDirectionToEnglishToTibetan() {
        openSearchFromHome()
        val enBo = hasTestTag("direction_segment_en-bo") and hasAnyAncestor(hasTestTag("search_screen"))
        val boEn = hasTestTag("direction_segment_bo-en") and hasAnyAncestor(hasTestTag("search_screen"))
        waitFor(boEn and isSelected())

        typeQuery("compassion")

        waitFor(enBo and isSelected())
        waitFor(resultRow("compassion"))
        assertThat(exists(boEn and isSelected())).isFalse()
    }

    @Test
    fun misspeltWylieShowsDidYouMeanRows() {
        openSearchFromHome()
        typeQuery("bkrashi")

        waitFor(hasTestTagPrefix(SUGGESTION_ROW))
        compose.onAllNodes(hasTestTagPrefix(RESULT_ROW)).assertCountEquals(0)
    }

    /** A result row whose headword is exactly [headword]. */
    private fun resultRow(headword: String) = hasTestTagPrefix(RESULT_ROW) and hasText(headword)

    /** The row's Wylie line draws [typed] at 600 weight, from its first letter. */
    private fun assertWylieHighlighted(rowTag: String, typed: String) {
        val wylie = compose.onNode(
            hasTestTag(WYLIE_TAG) and hasAnyAncestor(hasTestTag(rowTag)),
            useUnmergedTree = true,
        ).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val highlights = wylie.spanStyles.filter { it.item.fontWeight == FontWeight.SemiBold }
        assertWithMessage("highlighted spans in \"${wylie.text}\"").that(highlights).hasSize(1)
        val span = highlights.single()
        assertThat(span.start).isEqualTo(0)
        assertThat(wylie.text.substring(span.start, span.end)).isEqualTo(typed)
    }

    /** The save reached `user.db`, not only the screen. */
    private fun awaitSavedInDatabase(entryId: Long) = runBlocking {
        withTimeout(UI_TIMEOUT_MS) { userDatabase.savedEntryDao().observeIsSaved(entryId).first { it } }
    }

    private companion object {
        const val RESULT_ROW = "result_row_"
        const val SUGGESTION_ROW = "suggestion_row_"
        const val WYLIE_TAG = "entry_row_wylie"

        /** "bkra shis": headwords are stored with their final tsheg, and shown as stored. */
        const val HEADWORD = "བཀྲ་ཤིས་"

        /** "bkra", the exact Wylie match for the query. */
        const val EXACT_HEADWORD = "བཀྲ་"
    }
}
