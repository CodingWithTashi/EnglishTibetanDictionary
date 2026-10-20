package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.Jomolhari
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
        show { SenseCard(1, null, "house", house) }

        val text = compose.onNodeWithText("house", substring = true).text()
        assertThat(text.text).isEqualTo("house — e.g. ཁང་པ་ཆེན་པོ། (a big house)")
    }

    @Test
    fun `a blank definition shows its example alone, without the dash`() {
        show { SenseCard(2, "n.", "", house) }

        val text = compose.onNodeWithText("e.g.", substring = true).text()
        assertThat(text.text).isEqualTo("(n.) e.g. ཁང་པ་ཆེན་པོ། (a big house)")
    }

    @Test
    fun `a later card of a numbered sense keeps the number column's indent`() {
        show {
            SenseCard(SenseUi(1, null, "first card", null))
            SenseCard(SenseUi(null, null, "later card", null, reserveNumberColumn = true))
            SenseCard(SenseUi(null, null, "unnumbered card", null))
        }

        val first = compose.onNodeWithText("first card").getUnclippedBoundsInRoot().left
        val later = compose.onNodeWithText("later card").getUnclippedBoundsInRoot().left
        val unnumbered = compose.onNodeWithText("unnumbered card").getUnclippedBoundsInRoot().left
        assertThat(later).isEqualTo(first)
        // The 16dp number column and the 14dp gap after it.
        assertThat((first - unnumbered).value).isWithin(0.5f).of(30.dp.value)
    }

    @Test
    @Config(qualifiers = "bo")
    fun `the example label is drawn in the Tibetan font in bo`() {
        show(language = null) { SenseCard(1, null, "auspicious", ExampleUi("good luck", null, textIsTibetan = false)) }

        val text = compose.onNodeWithText("auspicious", substring = true).text()
        val label = "དཔེར་ན།"
        assertThat(text.text).isEqualTo("auspicious — $label good luck")
        val start = text.text.indexOf(label)
        val labelSpans = text.spanStyles.filter { it.start <= start && it.end >= start + label.length }
        assertThat(labelSpans.map { it.item.fontFamily }).contains(Jomolhari)
        assertThat(labelSpans.map { it.item.color }).contains(DictColors.Light.muted)
    }
}
