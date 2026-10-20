package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    /** The colour [label] is laid out in. */
    private fun labelColor(label: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.color
    }

    @Test
    fun `a text button is accent unless given another content colour`() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                TextButton("Open", {})
                TextButton("Skip", {}, contentColor = DictTheme.colors.muted)
            }
        }

        assertThat(labelColor("Open")).isEqualTo(DictColors.Light.accent)
        assertThat(labelColor("Skip")).isEqualTo(DictColors.Light.muted)
    }
}
