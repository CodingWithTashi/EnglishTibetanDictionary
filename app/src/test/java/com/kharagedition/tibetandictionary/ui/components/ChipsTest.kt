package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
class ChipsTest {

    @get:Rule
    val compose = createComposeRule()

    private var added = 0

    private fun setRow() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
                    TextChip("All 6", {}, Modifier.testTag("all"), selected = true, size = ChipSize.Small)
                    AddChip({ added++ }, "New folder", Modifier.testTag("add"), size = ChipSize.Small)
                }
            }
        }
    }

    @Test
    fun `the add chip sits one chip gap after its neighbour, as in 1i`() {
        setRow()

        val all = compose.onNodeWithTag("all").getBoundsInRoot()
        val add = compose.onNodeWithTag("add").getBoundsInRoot()
        assertThat(add.left - all.right).isEqualTo(8.dp)
        assertThat(add.width).isEqualTo(32.dp)
    }

    @Test
    fun `the add chip answers taps across a 48dp target`() {
        setRow()
        val chip = compose.onNodeWithContentDescription("New folder")

        // 6dp outside the 32dp circle on either side and above: still inside the 48dp target.
        chip.performTouchInput { click(Offset(-6.dp.toPx(), centerY)) }
        chip.performTouchInput { click(Offset(width + 6.dp.toPx(), centerY)) }
        chip.performTouchInput { click(Offset(centerX, -6.dp.toPx())) }

        assertThat(added).isEqualTo(3)
    }
}
