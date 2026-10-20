package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.ui.profile.ProfileScreen
import com.kharagedition.tibetandictionary.ui.profile.ProfileUiState
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
// Real font metrics, so a 12sp label is shorter than the 26dp segment pill.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedControlTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                var selected by remember { mutableIntStateOf(0) }
                Box(Modifier.padding(24.dp)) {
                    SegmentedControl3(listOf("Light", "Dark", "System"), selected, { selected = it }, Modifier.testTag(TAG))
                }
            }
        }
    }

    /** Profile, whose Theme row holds the control; the language follows the configuration (the bo qualifier). */
    private fun showProfile() {
        val state = ProfileUiState(
            textSize = AppTextSize.DEFAULT,
            lineSpacing = 1.9f,
            appearance = Appearance.SYSTEM,
            defaultDirection = SampleData.boEn,
            directions = SampleData.directions,
            language = AppLanguage.EN,
            versionName = "3.0.0",
            entryCount = 180_412,
            links = emptyList(),
            otherApps = emptyList(),
        )
        compose.setContent {
            DictTheme(darkTheme = false) { ProfileScreen(state, onEvent = {}) }
        }
    }

    @Test
    fun `the control is a 32dp track around 26dp segment pills`() {
        show()

        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(32.dp)
        // The segment pill sits inside the track's 3dp padding: 26 + 2 × 3 = 32.
        compose.onNodeWithText("Dark").assertHeightIsEqualTo(26.dp)
    }

    @Test
    fun `a touch just outside a segment's pill, within its touch target, selects it`() {
        show()

        compose.onNodeWithText("Dark").performTouchInput { click(Offset(centerX, -8.dp.toPx())) }

        compose.onNodeWithText("Dark").assertIsSelected()
    }

    @Test
    fun `the Theme row keeps the design's height`() {
        showProfile()

        compose.onNodeWithTag(THEME_ROW).assertHeightIsEqualTo(52.dp)
    }

    @Test
    @Config(qualifiers = "bo")
    fun `the Theme row keeps the design's height in bo`() {
        showProfile()

        compose.onNodeWithTag(THEME_ROW).assertHeightIsEqualTo(56.dp)
    }

    private companion object {
        const val TAG = "segmented"
        const val THEME_ROW = "profile_row_theme"
    }
}
