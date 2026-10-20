package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.Jomolhari
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class SenseCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val house = ExampleUi("ཁང་པ་ཆེན་པོ།", "a big house", textIsTibetan = true)

    private fun SemanticsNodeInteraction.text(): AnnotatedString =
        fetchSemanticsNode().config[SemanticsProperties.Text].single()

    private fun show(language: AppLanguage? = AppLanguage.EN, content: @Composable () -> Unit) {
        compose.setContent {
            if (language != null) {
                DictTheme(darkTheme = false, language = language) { Column { content() } }
            } else {
                // The language follows the configuration (the bo qualifier).
                DictTheme(darkTheme = false) { Column { content() } }
            }
        }
    }

    @Test
    fun `a definition with an example shows the dash and the example label`() {
        show { SenseCard(1, null, "house", listOf(house)) }

        val text = compose.onNodeWithText("house", substring = true).text()
        assertThat(text.text).isEqualTo("house — e.g. ཁང་པ་ཆེན་པོ། (a big house)")
    }

    @Test
    fun `a blank definition shows its example alone, without the dash`() {
        show { SenseCard(2, "n.", "", listOf(house)) }

        val text = compose.onNodeWithText("e.g.", substring = true).text()
        assertThat(text.text).isEqualTo("(n.) e.g. ཁང་པ་ཆེན་པོ། (a big house)")
    }

    @Test
    fun `every example follows the definition, each after its own dash`() {
        val second = ExampleUi("ཁང་པ་གསར་པ།", null, textIsTibetan = true)
        show { SenseCard(1, null, "house", listOf(house, second)) }

        val text = compose.onNodeWithText("house", substring = true).text()
        assertThat(text.text).isEqualTo("house — e.g. ཁང་པ་ཆེན་པོ། (a big house) — e.g. ཁང་པ་གསར་པ།")
    }

    @Test
    fun `a blank definition starts with its first example and dashes the rest`() {
        val second = ExampleUi("ཁང་པ་གསར་པ།", null, textIsTibetan = true)
        show { SenseCard(2, null, "", listOf(house, second)) }

        val text = compose.onNodeWithText("e.g.", substring = true).text()
        assertThat(text.text).isEqualTo("e.g. ཁང་པ་ཆེན་པོ། (a big house) — e.g. ཁང་པ་གསར་པ།")
    }

    @Test
    fun `a sense label is an inline badge after the part of speech`() {
        show { SenseCard(SenseUi(4, "n.", "ཁྱིམ།", label = "astrol")) }

        val node = compose.onNodeWithText("ཁྱིམ།", substring = true)
        // The badge's text is the placeholder's alternate text, so screen readers read it in place.
        assertThat(node.text().text).isEqualTo("(n.) astrol. ཁྱིམ།")
        compose.onNodeWithTag(LABEL_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a screen reader reads the label once, from the text, not again from the badge`() {
        show { SenseCard(SenseUi(4, "n.", "ཁྱིམ།", label = "astrol")) }

        // The merged tree is what accessibility services read.
        compose.onAllNodesWithText("astrol.", substring = true).assertCountEquals(1)
        val badge = compose.onNodeWithTag(LABEL_BADGE_TAG).fetchSemanticsNode()
        assertThat(SemanticsProperties.Text in badge.config).isFalse()
        assertThat(badge.children).isEmpty()
    }

    @Test
    fun `canonical label keys read as short display strings, one badge each`() {
        show {
            SenseCard(SenseUi(1, null, "a support", label = "poetic,archaic"))
            SenseCard(SenseUi(2, null, "council", label = "adm,law"))
            SenseCard(SenseUi(3, null, "a lord", label = "us,definition"))
        }

        assertThat(compose.onNodeWithText("a support", substring = true).text().text)
            .isEqualTo("poet. archaic a support")
        assertThat(compose.onNodeWithText("council", substring = true).text().text).isEqualTo("adm. law council")
        assertThat(compose.onNodeWithText("a lord", substring = true).text().text).isEqualTo("US def. a lord")
        compose.onAllNodesWithTag(LABEL_BADGE_TAG, useUnmergedTree = true).assertCountEquals(6)
    }

    /** Shows [content] at the largest text size and font scale 2.0 in a 360dp-wide column. */
    private fun showAtLargest(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DictTheme(darkTheme = false, textScale = AppTextSize.LARGEST.scale, language = AppLanguage.EN) {
                    Column(Modifier.width(360.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun `the badge is measured, so it holds its text at the largest settings`() {
        var textSize = DpSize.Zero
        showAtLargest {
            val measured = rememberTextMeasurer().measure("astrol.", DictTheme.type.badge).size
            textSize = with(LocalDensity.current) { DpSize(measured.width.toDp(), measured.height.toDp()) }
            SenseCard(SenseUi(1, "n.", "ཁྱིམ།", label = "astrol"))
        }

        val badge = compose.onNodeWithTag(LABEL_BADGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        // 10sp × 1.3 × 2: the badge grows with the text instead of clipping it.
        assertThat(textSize.height.value).isGreaterThan(20f)
        assertThat(badge.height.value).isAtLeast(textSize.height.value)
        assertThat(badge.width.value).isAtLeast(textSize.width.value + 12f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
    fun `a long run of labels wraps between badges inside the card at the largest settings`() {
        showAtLargest { SenseCard(SenseUi(3, null, "a format", label = "adm,pub,jrn,comput,sci"), Modifier.testTag("card")) }

        val card = compose.onNodeWithTag("card").getUnclippedBoundsInRoot()
        val badges = compose.onAllNodesWithTag(LABEL_BADGE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertThat(badges).hasSize(5)
        val contentRight = with(compose.density) { card.right.toPx() - 16.dp.toPx() }
        // Each badge stays inside the card's 16dp padding; one joined badge ran past it and was clipped.
        badges.forEach { assertThat(it.right).isAtMost(contentRight + 0.5f) }
        assertThat(badges.map { it.top }.distinct().size).isGreaterThan(1)
    }

    @Test
    @Config(qualifiers = "bo")
    fun `Tibetan-source labels read in Tibetan in bo`() {
        show(language = null) { SenseCard(SenseUi(2, null, "སངས་རྒྱས་ཀྱི་མཚན།", label = "poetic")) }

        assertThat(compose.onNodeWithText("སངས་རྒྱས་ཀྱི་མཚན།", substring = true).text().text)
            .isEqualTo("མངོན་བརྗོད། སངས་རྒྱས་ཀྱི་མཚན།")
        compose.onNodeWithTag(LABEL_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a note goes on a muted line under the definition`() {
        show { SenseCard(SenseUi(1, "vi.", "རྒྱུག་པ།", note = "ran, run")) }

        val gloss = compose.onNodeWithText("རྒྱུག་པ།", substring = true).getUnclippedBoundsInRoot()
        val note = compose.onNodeWithText("ran, run").getUnclippedBoundsInRoot()
        assertThat(note.top).isAtLeast(gloss.bottom)
        assertThat(note.left).isEqualTo(gloss.left)
    }

    @Test
    fun `a later card of a numbered sense keeps the number column's indent`() {
        show {
            SenseCard(SenseUi(1, null, "first card"))
            SenseCard(SenseUi(null, null, "later card", reserveNumberColumn = true))
            SenseCard(SenseUi(null, null, "unnumbered card"))
        }

        val first = compose.onNodeWithText("first card").getUnclippedBoundsInRoot().left
        val later = compose.onNodeWithText("later card").getUnclippedBoundsInRoot().left
        val unnumbered = compose.onNodeWithText("unnumbered card").getUnclippedBoundsInRoot().left
        assertThat(later).isEqualTo(first)
        // The 16dp number column and the 14dp gap after it.
        assertThat((first - unnumbered).value).isWithin(0.5f).of(30.dp.value)
    }

    /** Layout of the sense number [number], read from the laid-out text. */
    private fun numberLayout(number: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(number, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
    fun `a two-digit sense number fits its column on one line`() {
        show { SenseCard(SenseUi(24, null, "a meaning"), numberDigits = 2) }

        val layout = numberLayout("24")
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.didOverflowWidth).isFalse()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
    fun `a two-digit sense number fits its column on one line at the largest settings`() {
        showAtLargest { SenseCard(SenseUi(24, null, "a meaning"), numberDigits = 2) }

        val layout = numberLayout("24")
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.didOverflowWidth).isFalse()
    }

    @Test
    @Config(qualifiers = "bo")
    fun `the example label is drawn in the Tibetan font in bo`() {
        show(language = null) { SenseCard(1, null, "auspicious", listOf(ExampleUi("good luck", null, textIsTibetan = false))) }

        val text = compose.onNodeWithText("auspicious", substring = true).text()
        val label = "དཔེར་ན།"
        assertThat(text.text).isEqualTo("auspicious — $label good luck")
        val start = text.text.indexOf(label)
        val labelSpans = text.spanStyles.filter { it.start <= start && it.end >= start + label.length }
        assertThat(labelSpans.map { it.item.fontFamily }).contains(Jomolhari)
        assertThat(labelSpans.map { it.item.color }).contains(DictColors.Light.muted)
    }
}
