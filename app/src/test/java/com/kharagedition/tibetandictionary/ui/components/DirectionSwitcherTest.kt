package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class DirectionSwitcherTest {

    @get:Rule
    val compose = createComposeRule()

    private val boEn = SampleData.boEn
    private val boBo = SampleData.boBo
    private val enBo = SampleData.enBo
    private val zhBo = enBo.copy(id = "zh-bo", sourceLang = "zh", sourceScript = Script.HAN, position = 3)

    private val three = DirectionSlots(fixed = listOf(boEn, boBo, enBo), overflow = emptyList())
    private val four = DirectionSlots(fixed = listOf(boEn, enBo), overflow = listOf(boBo, zhBo))

    private fun stateIs(label: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)

    private val segments = SemanticsMatcher("is a direction segment") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(SEGMENT_PREFIX) == true
    }

    @Test
    fun `three directions render three segments with the selected one marked`() {
        compose.setContent { DictTheme(darkTheme = false) { DirectionSwitcher(three, boEn, onSelect = {}) } }

        compose.onAllNodes(segments).assertCountEquals(3)
        compose.onNodeWithTag(SEGMENT_PREFIX + "bo-en").assertIsSelected()
        compose.onNodeWithTag(SEGMENT_PREFIX + "bo-bo").assertIsNotSelected()
        compose.onNodeWithTag(SEGMENT_PREFIX + "en-bo").assertIsNotSelected()
        compose.onNodeWithTag(SEGMENT_PREFIX + "more").assertDoesNotExist()
    }

    @Test
    fun `tapping a segment reports its direction`() {
        var picked: Direction? = null
        compose.setContent { DictTheme(darkTheme = false) { DirectionSwitcher(three, boEn, onSelect = { picked = it }) } }

        compose.onNodeWithTag(SEGMENT_PREFIX + "en-bo").performClick()

        assertThat(picked).isEqualTo(enBo)
    }

    @Test
    fun `labels follow the direction label rules`() {
        compose.setContent { DictTheme(darkTheme = false) { DirectionSwitcher(three, boEn, onSelect = {}) } }

        compose.onNodeWithText("བོད→ EN").assertIsDisplayed()
        compose.onNodeWithText("བོད→བོད").assertIsDisplayed()
        compose.onNodeWithText("EN →བོད").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real font metrics
    fun `a Tibetan token stands a third of an em off the arrow, as the mockups space it`() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                Column {
                    DirectionLabel(boBo, Modifier.testTag("drawn"))
                    MixedText(
                        directionLabelText(boBo),
                        DictTheme.type.label,
                        DictTheme.type.tibetanInline15,
                        Modifier.testTag("touching"),
                    )
                }
            }
        }

        val drawn = compose.onNodeWithTag("drawn").getBoundsInRoot().width
        val touching = compose.onNodeWithTag("touching").getBoundsInRoot().width
        // Two gaps of ⅓ em at the 13sp label size (≈ 8.7dp); the word joiner adds nothing.
        assertThat((drawn - touching).value).isWithin(1.5f).of(2 * 13f / 3)
    }

    @Test
    fun `four directions put the overflow behind More, which opens a menu`() {
        var picked: Direction? = null
        compose.setContent { DictTheme(darkTheme = false) { DirectionSwitcher(four, boEn, onSelect = { picked = it }) } }

        compose.onAllNodes(segments).assertCountEquals(3)
        compose.onNodeWithText("More").assertIsDisplayed()
        compose.onNodeWithTag(MENU_PREFIX + "bo-bo").assertDoesNotExist()

        compose.onNodeWithTag(SEGMENT_PREFIX + "more").performClick()
        compose.onNodeWithTag(MENU_PREFIX + "bo-bo").assertIsDisplayed()
        compose.onNodeWithTag(MENU_PREFIX + "zh-bo").assertIsDisplayed()
        compose.onNodeWithTag(MENU_PREFIX + "zh-bo").performClick()

        assertThat(picked).isEqualTo(zhBo)
        compose.onNodeWithTag(MENU_PREFIX + "bo-bo").assertDoesNotExist()
    }

    @Test
    fun `a selected overflow direction labels and selects the More segment`() {
        val slots = four.copy(moreLabel = zhBo)
        compose.setContent { DictTheme(darkTheme = false) { DirectionSwitcher(slots, zhBo, onSelect = {}) } }

        compose.onNodeWithText("中→བོད").assertIsDisplayed()
        compose.onNodeWithText("More").assertDoesNotExist()
        compose.onNodeWithTag(SEGMENT_PREFIX + "more").assertIsSelected()
        compose.onNodeWithTag(SEGMENT_PREFIX + "bo-en").assertIsNotSelected()
        compose.onNodeWithTag(SWITCHER).assert(stateIs("中→བོད"))
    }

    @Test
    fun `an auto-selected direction moves the selection and the thumb`() {
        var selected by mutableStateOf(boEn)
        var auto by mutableStateOf(false)
        compose.setContent {
            DictTheme(darkTheme = false) { DirectionSwitcher(three, selected, onSelect = {}, autoSelected = auto) }
        }
        compose.onNodeWithTag(SWITCHER).assert(stateIs("བོད→ EN"))
        val thumbStart = compose.onNodeWithTag(THUMB).getBoundsInRoot().left
        assertThat(thumbStart).isEqualTo(compose.onNodeWithTag(SEGMENT_PREFIX + "bo-en").getBoundsInRoot().left)

        selected = enBo
        auto = true
        compose.mainClock.advanceTimeBy(ANIMATION_SETTLE_MS)
        compose.waitForIdle()

        compose.onNodeWithTag(SEGMENT_PREFIX + "en-bo").assertIsSelected()
        compose.onNodeWithTag(SEGMENT_PREFIX + "bo-en").assertIsNotSelected()
        // The live region announces this description when it changes.
        compose.onNodeWithTag(SWITCHER).assert(stateIs("EN →བོད"))
        val thumbEnd = compose.onNodeWithTag(THUMB).getBoundsInRoot().left
        val target = compose.onNodeWithTag(SEGMENT_PREFIX + "en-bo").getBoundsInRoot().left
        assertThat(thumbEnd.value).isWithin(0.5f).of(target.value)
        assertThat(thumbEnd.value).isGreaterThan(thumbStart.value)
    }

    @Test
    fun `auto-selection is announced politely`() {
        compose.setContent {
            DictTheme(darkTheme = false) { DirectionSwitcher(three, enBo, onSelect = {}, autoSelected = true) }
        }

        compose.onNodeWithTag(SWITCHER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private companion object {
        const val SEGMENT_PREFIX = "direction_segment_"
        const val MENU_PREFIX = "direction_menu_"
        const val THUMB = "direction_thumb"
        const val SWITCHER = "direction_switcher"
        const val ANIMATION_SETTLE_MS = 1_000L
    }
}
