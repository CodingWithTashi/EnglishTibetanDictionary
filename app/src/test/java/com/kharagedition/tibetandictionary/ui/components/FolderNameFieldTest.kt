package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.Jomolhari
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderNameFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private var name by mutableStateOf("")

    private fun show() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                FolderNameField(name, { name = it }, onDone = {}, Modifier.testTag(TAG))
            }
        }
    }

    /** The text as the field lays it out, with the spans its visual transformation adds. */
    private fun laidOut(): AnnotatedString {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(TAG).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.text
    }

    /** The font families of the spans covering [part] of [text]. */
    private fun familiesOver(text: AnnotatedString, part: String): List<Any?> {
        val start = text.text.indexOf(part)
        return text.spanStyles.filter { it.start <= start && it.end >= start + part.length }.map { it.item.fontFamily }
    }

    @Test
    fun `a typed Tibetan name is drawn in the Tibetan font, its Latin part is not`() {
        show()

        compose.onNodeWithTag(TAG).performTextInput("ཆོས་ notes")

        val text = laidOut()
        assertThat(name).isEqualTo("ཆོས་ notes")
        assertThat(text.text).isEqualTo("ཆོས་ notes")
        assertThat(familiesOver(text, "ཆོས་")).contains(Jomolhari)
        assertThat(familiesOver(text, "notes")).doesNotContain(Jomolhari)
    }

    private companion object {
        const val TAG = "folder_name"
    }
}
