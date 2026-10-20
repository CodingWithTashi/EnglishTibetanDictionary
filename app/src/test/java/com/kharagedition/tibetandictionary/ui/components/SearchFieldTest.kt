package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf(TextFieldValue(""))
    private var showMic by mutableStateOf(true)
    private var cleared = 0
    private var micTaps = 0
    private var searches = 0

    private fun setField(variant: SearchFieldVariant = SearchFieldVariant.Focused) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                SearchField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Search a word · ཚིག་འཚོལ།",
                    variant = variant,
                    showMic = showMic,
                    onMic = { micTaps++ },
                    onClear = {
                        cleared++
                        value = TextFieldValue("")
                    },
                    onSearch = { searches++ },
                )
            }
        }
    }

    @Test
    fun `clear is hidden while the field is empty`() {
        setField()

        compose.onNodeWithTag(CLEAR).assertDoesNotExist()
        compose.onNodeWithText("Search a word · ཚིག་འཚོལ།").assertIsDisplayed()
    }

    @Test
    fun `clear appears with text and clears it`() {
        setField()

        compose.onNodeWithTag(FIELD).performTextInput("bkra")
        compose.onNodeWithTag(CLEAR).assertIsDisplayed()
        assertThat(value.text).isEqualTo("bkra")

        compose.onNodeWithTag(CLEAR).performClick()

        assertThat(cleared).isEqualTo(1)
        compose.onNodeWithTag(CLEAR).assertDoesNotExist()
    }

    @Test
    fun `mic shows only when enabled and the field is empty`() {
        setField(SearchFieldVariant.Home)

        compose.onNodeWithTag(MIC).assertIsDisplayed().performClick()
        assertThat(micTaps).isEqualTo(1)

        value = TextFieldValue("snying")
        compose.onNodeWithTag(MIC).assertDoesNotExist()
        compose.onNodeWithTag(CLEAR).assertIsDisplayed()

        value = TextFieldValue("")
        showMic = false
        compose.onNodeWithTag(MIC).assertDoesNotExist()
        compose.onNodeWithTag(CLEAR).assertDoesNotExist()
    }

    @Test
    fun `the keyboard Search key calls onSearch`() {
        setField()

        compose.onNodeWithTag(FIELD).performTextInput("bkra shis")
        compose.onNodeWithTag(FIELD).performImeAction()

        assertThat(searches).isEqualTo(1)
    }

    @Test
    fun `a Home field with onClick is a button and does not edit`() {
        var opened = 0
        compose.setContent {
            DictTheme(darkTheme = false) {
                SearchField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Search a word · ཚིག་འཚོལ།",
                    variant = SearchFieldVariant.Home,
                    onClick = { opened++ },
                )
            }
        }

        compose.onNodeWithTag(FIELD).performClick()

        assertThat(opened).isEqualTo(1)
        compose.onNodeWithTag(MIC).assertDoesNotExist()
    }

    private companion object {
        const val FIELD = "search_field"
        const val CLEAR = "search_clear"
        const val MIC = "search_mic"
    }
}
