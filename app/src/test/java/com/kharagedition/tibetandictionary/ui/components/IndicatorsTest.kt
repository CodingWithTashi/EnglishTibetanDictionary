package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndicatorsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun stateIs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `the page indicator tells accessibility services which step is current`() {
        var current by mutableIntStateOf(0)
        compose.setContent {
            DictTheme(darkTheme = false) { PageIndicator(count = 3, current = current, modifier = Modifier.testTag(TAG)) }
        }
        compose.onNodeWithTag(TAG).assert(stateIs("Step 1 of 3"))

        current = 1

        compose.onNodeWithTag(TAG).assert(stateIs("Step 2 of 3"))
    }

    @Test
    fun `the step position uses Tibetan digits in the bo UI`() {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.BO) {
                PageIndicator(count = 3, current = 1, modifier = Modifier.testTag(TAG))
            }
        }

        compose.onNodeWithTag(TAG).assert(stateIs("Step ༢ of ༣"))
    }

    private companion object {
        const val TAG = "pages"
    }
}
