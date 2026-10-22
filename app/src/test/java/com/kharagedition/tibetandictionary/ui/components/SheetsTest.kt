package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SheetsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun directionDescriptionCoversDdcIds() {
        val ids = mapOf(
            "dz-en" to R.string.direction_desc_dz_en,
            "dz-dz" to R.string.direction_desc_dz_dz,
            "en-dz" to R.string.direction_desc_en_dz,
        )
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

        assertThat(shown).isEqualTo(expected)
        assertThat(shown.values).doesNotContain(SampleData.boEn.labelEn)
    }
}
