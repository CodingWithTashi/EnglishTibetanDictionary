package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
class OptionCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float, textSize: AppTextSize) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DictTheme(darkTheme = false, textScale = textSize.scale, language = AppLanguage.EN) {
                    Column(Modifier.width(320.dp)) {
                        OptionCard(true, {}, Modifier.testTag("card"), description = "Tibetan to English") {
                            Text("EN")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `an option card is 64dp high at the default size`() {
        show(fontScale = 1f, textSize = AppTextSize.DEFAULT)

        assertThat(compose.onNodeWithTag("card").getBoundsInRoot().height).isEqualTo(64.dp)
    }

    @Test
    fun `a wrapped description keeps 8dp off the border at the largest settings`() {
        show(fontScale = 2f, textSize = AppTextSize.LARGEST)

        val card = compose.onNodeWithTag("card").getBoundsInRoot()
        // The card is selectable, so it merges its text; the description's own bounds are in the unmerged tree.
        val description = compose.onNodeWithText("Tibetan to English", useUnmergedTree = true).getBoundsInRoot()
        assertThat(description.height).isGreaterThan(64.dp)
        assertThat((description.top - card.top).value).isAtLeast(7.5f)
        assertThat((card.bottom - description.bottom).value).isAtLeast(7.5f)
    }
}
