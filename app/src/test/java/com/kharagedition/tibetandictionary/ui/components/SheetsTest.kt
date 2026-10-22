package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.resources
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SheetsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun directionDescriptionCoversEveryPackDirection() {
        val ids = DictionaryPack.entries.flatMap { it.resources.directionDescriptions.entries }
        val shown = mutableMapOf<String, String>()
        val expected = mutableMapOf<String, String>()
        compose.setContent {
            DictTheme(darkTheme = false) {
                ids.forEach { (id, res) ->
                    shown[id] = directionDescription(SampleData.boEn.copy(id = id))
                    expected[id] = stringResource(res)
                }
            }
        }
        compose.waitForIdle()

        assertThat(shown.keys).containsAtLeast("bo-en", "bo-bo", "en-bo", "dz-en", "dz-dz", "en-dz")
        assertThat(shown).isEqualTo(expected)
        assertThat(shown.values).doesNotContain(SampleData.boEn.labelEn)
    }

    @Test
    fun directionWithoutDescriptionShowsItsLabel() {
        var shown = ""
        compose.setContent {
            DictTheme(darkTheme = false) { shown = directionDescription(SampleData.boEn.copy(id = "zh-bo")) }
        }
        compose.waitForIdle()

        assertThat(shown).isEqualTo(SampleData.boEn.labelEn)
    }
}
