package com.kharagedition.tibetandictionary.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.ui.preview.DictPreview
import com.kharagedition.tibetandictionary.ui.preview.IconsSheet
import com.kharagedition.tibetandictionary.ui.preview.LatinTypeSheet
import com.kharagedition.tibetandictionary.ui.preview.PaletteSheet
import com.kharagedition.tibetandictionary.ui.preview.TibetanTypeSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DictThemeRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Icon tint colours every drawn pixel of a DictIcon`() {
        compose.setContent {
            OnBackground(Color.White) { Icon(DictIcons.Search, "search", Modifier.size(48.dp), tint = Color.Red) }
        }

        assertIconPixels(background = Color.White, ink = Color.Red)
    }

    @Test
    fun `Icon tint colours a filled DictIcon with the accent`() {
        compose.setContent {
            OnBackground(DictColors.Light.background) {
                Icon(DictIcons.HeartFilled, "saved", Modifier.size(48.dp), tint = DictColors.Light.accent)
            }
        }

        assertIconPixels(background = DictColors.Light.background, ink = DictColors.Light.accent)
    }

    @Test
    fun `untinted DictIcon under DictTheme draws in ink`() {
        compose.setContent {
            DictTheme(darkTheme = true) {
                assertThat(LocalContentColor.current).isEqualTo(DictColors.Dark.ink)
                OnBackground(DictTheme.colors.background) {
                    Icon(DictIcons.Active.Heart, "save", Modifier.size(48.dp))
                }
            }
        }

        assertIconPixels(background = DictColors.Dark.background, ink = DictColors.Dark.ink)
    }

    @Test
    fun `palette type and icon sheets compose with the bundled fonts in the light theme`() {
        assertSheetsCompose(dark = false)
    }

    @Test
    fun `palette type and icon sheets compose with the bundled fonts in the dark theme`() {
        assertSheetsCompose(dark = true)
    }

    private fun assertSheetsCompose(dark: Boolean) {
        compose.setContent {
            DictPreview(dark = dark) {
                Column {
                    PaletteSheet()
                    LatinTypeSheet()
                    TibetanTypeSheet()
                    IconsSheet(DictIcons.Bold)
                }
            }
        }

        compose.onNodeWithText(if (dark) "Dark palette" else "Light palette").assertExists()
        compose.onNodeWithText("screenTitle", substring = true).assertExists()
        compose.onNodeWithText("tibetanEntryHeadword", substring = true).assertExists()
        compose.onNodeWithText("Icons · stroke 2.4").assertExists()
    }

    @Composable
    private fun OnBackground(color: Color, content: @Composable () -> Unit) {
        Box(Modifier.background(color).testTag(TAG)) { content() }
    }

    /**
     * Every pixel inside the tagged box is a blend of [background] and [ink] (anti-aliased edges are partial
     * blends; an untinted black stroke is not), and the icon covers a solid area of [ink]. The window is drawn
     * in software because `captureToImage` waits for a frame commit Robolectric never sends.
     */
    private fun assertIconPixels(background: Color, ink: Color) {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
        val window = compose.runOnUiThread { compose.activity.window.decorView.drawToBitmap() }
        val delta = floatArrayOf(ink.red - background.red, ink.green - background.green, ink.blue - background.blue)
        val axis = delta.indices.maxBy { abs(delta[it]) }
        var solid = 0
        for (x in bounds.left.toInt() until bounds.right.toInt()) {
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                val c = Color(window.getPixel(x, y))
                val rgb = floatArrayOf(c.red, c.green, c.blue)
                val bg = floatArrayOf(background.red, background.green, background.blue)
                val coverage = (rgb[axis] - bg[axis]) / delta[axis]
                val error = rgb.indices.maxOf { abs(rgb[it] - (bg[it] + delta[it] * coverage)) }
                assertWithMessage("pixel ($x, $y) = $c is not a blend of $background and $ink")
                    .that(max(error, max(-coverage, coverage - 1f))).isLessThan(BLEND_TOLERANCE)
                if (coverage > 0.9f) solid++
            }
        }
        assertWithMessage("solid icon pixels").that(solid).isGreaterThan(50)
    }

    private companion object {
        const val TAG = "icon"
        const val BLEND_TOLERANCE = 0.06f
    }
}
