package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val tashi = SearchResultUi(
        entryId = 1_004_211,
        direction = SampleData.boEn,
        headword = "བཀྲ་ཤིས།",
        headwordIsTibetan = true,
        wylie = "bkra shis",
        matchRange = 0..3,
        headwordMatchRange = null,
        gloss = "1. auspicious, fortunate 2. good luck, blessing",
        showBadge = false,
    )

    private fun setRow(result: SearchResultUi, dark: Boolean = false, onClick: () -> Unit = {}) {
        compose.setContent { DictTheme(darkTheme = dark) { EntryRow(result, onClick = onClick) } }
    }

    private fun SemanticsNodeInteraction.text(): AnnotatedString =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().single()

    @Test
    fun `Wylie match range is bold and accent coloured`() {
        setRow(tashi)

        val wylie = compose.onNodeWithTag(WYLIE, useUnmergedTree = true).text()

        assertThat(wylie.text).isEqualTo("bkra shis")
        val match = wylie.spanStyles.filter { it.item.fontWeight == FontWeight.SemiBold }
        assertThat(match).hasSize(1)
        assertThat(match.single().start).isEqualTo(0)
        assertThat(match.single().end).isEqualTo(4)
        assertThat(match.single().item.color).isEqualTo(DictColors.Light.accent)
    }

    @Test
    fun `the match uses the dark accent in the dark theme`() {
        setRow(tashi.copy(matchRange = 5..8), dark = true)

        val match = compose.onNodeWithTag(WYLIE, useUnmergedTree = true).text().spanStyles
            .single { it.item.fontWeight == FontWeight.SemiBold }

        assertThat(match.start).isEqualTo(5)
        assertThat(match.end).isEqualTo(9)
        assertThat(match.item.color).isEqualTo(DictColors.Dark.accent)
    }

    @Test
    fun `no match range draws no highlight`() {
        setRow(tashi.copy(matchRange = null))

        val wylie = compose.onNodeWithTag(WYLIE, useUnmergedTree = true).text()

        assertThat(wylie.spanStyles.filter { it.item.fontWeight == FontWeight.SemiBold }).isEmpty()
    }

    @Test
    fun `an out-of-bounds match range is clamped instead of crashing`() {
        setRow(tashi.copy(matchRange = 5..40))

        val match = compose.onNodeWithTag(WYLIE, useUnmergedTree = true).text().spanStyles
            .single { it.item.fontWeight == FontWeight.SemiBold }

        assertThat(match.start).isEqualTo(5)
        assertThat(match.end).isEqualTo("bkra shis".length)
    }

    @Test
    fun `row without Wylie omits the Wylie line`() {
        setRow(tashi.copy(wylie = null, matchRange = null))

        compose.onNodeWithTag(WYLIE, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("1. auspicious, fortunate 2. good luck, blessing").assertIsDisplayed()
    }

    @Test
    fun `badge shows only for hits from another direction`() {
        setRow(tashi)
        compose.onNodeWithTag(BADGE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `badge shows the direction when showBadge is set`() {
        setRow(tashi.copy(showBadge = true))

        compose.onNodeWithTag(BADGE, useUnmergedTree = true).assertExists()
        // The row merges its texts; the badge contributes the plain direction label.
        compose.onNodeWithText("བོད→ EN").assertExists()
    }

    @Test
    fun `tapping the row opens it`() {
        var taps = 0
        setRow(tashi, onClick = { taps++ })

        compose.onNodeWithText("1. auspicious, fortunate 2. good luck, blessing").performClick()

        assertThat(taps).isEqualTo(1)
    }

    private companion object {
        const val WYLIE = "entry_row_wylie"
        const val BADGE = "entry_row_badge"
    }
}
