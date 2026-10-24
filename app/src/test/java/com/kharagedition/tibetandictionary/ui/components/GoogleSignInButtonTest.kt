package com.kharagedition.tibetandictionary.ui.components

import android.view.View
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.ui.pixelsOf
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The four colours of Google's "G". */
private val GoogleColors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixels
class GoogleSignInButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private var clicks = 0

    private fun show(dark: Boolean): PixelMap {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            DictTheme(darkTheme = dark, language = AppLanguage.EN) {
                GoogleSignInButton("Continue with Google", { clicks++ }, Modifier.width(280.dp).testTag("google"))
            }
        }
        return compose.pixelsOf(root, "google")
    }

    @Test
    fun `light theme draws the colour G on white`() {
        val pixels = show(dark = false)

        GoogleColors.forEach { assertThat(pixels.count(it)).isAtLeast(10) }
        assertThat(pixels.near(pixels[pixels.width / 2, 4], Color.White)).isTrue()
    }

    @Test
    fun `dark theme draws the colour G on Google's near-black`() {
        val pixels = show(dark = true)

        GoogleColors.forEach { assertThat(pixels.count(it)).isAtLeast(10) }
        assertThat(pixels.near(pixels[pixels.width / 2, 4], Color(0xFF131314))).isTrue()
    }

    @Test
    fun `a tap signs in`() {
        show(dark = false)

        compose.onNodeWithText("Continue with Google").assertExists()
        compose.onNodeWithTag("google").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    private fun PixelMap.count(target: Color): Int =
        (0 until width).sumOf { x -> (0 until height).count { y -> near(this[x, y], target) } }

    @Suppress("UnusedReceiverParameter")
    private fun PixelMap.near(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < TOLERANCE && abs(a.green - b.green) < TOLERANCE && abs(a.blue - b.blue) < TOLERANCE

    private companion object {
        const val TOLERANCE = 0.06f
    }
}
