package com.kharagedition.tibetandictionary.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.pixelsOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixels
class DottedUnderlineTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the underline is a row of separate dots along the bottom edge`() {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            Box(
                Modifier
                    .size(120.dp, 20.dp)
                    .background(Color.White)
                    .dottedUnderline(Color.Black)
                    .testTag("box"),
            )
        }

        val pixels = compose.pixelsOf(root, "box")
        val bottom = (0 until pixels.width).map { pixels[it, pixels.height - 1] != Color.White }
        val dots = bottom.zipWithNext().count { (a, b) -> !a && b } + if (bottom.first()) 1 else 0
        assertThat(dots).isAtLeast(10)
        assertThat(bottom.count { it }).isLessThan(pixels.width * 3 / 4)
        // Nothing above the line.
        assertThat((0 until pixels.width).all { pixels[it, pixels.height / 2] == Color.White }).isTrue()
    }
}
