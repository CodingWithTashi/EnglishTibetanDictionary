package com.kharagedition.tibetandictionary.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val account = AccountUi("tenzin.dolma@gmail.com", "TD", "Synced 2 min ago")

    private fun setCard(card: @Composable (Modifier) -> Unit) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                // Tag a wrapper: a clickable card merges semantics and reports bounds inside the caller's padding.
                Box(Modifier.background(Page).testTag(TAG)) { card(Modifier.padding(CALLER_PADDING.dp)) }
            }
        }
    }

    /** Colour of the pixel [xDp] into the tagged element, halfway down. */
    private fun pixelAt(xDp: Int): Color {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
        val density = compose.activity.resources.displayMetrics.density
        val window = compose.runOnUiThread { compose.activity.window.decorView.drawToBitmap() }
        return Color(window.getPixel((bounds.left + xDp * density).toInt(), bounds.center.y.toInt()))
    }

    private fun assertColor(actual: Color, expected: Color, what: String) {
        val close = abs(actual.red - expected.red) < 0.02f &&
            abs(actual.green - expected.green) < 0.02f &&
            abs(actual.blue - expected.blue) < 0.02f
        assertWithMessage("$what: $actual, expected $expected").that(close).isTrue()
    }

    @Test
    fun `a caller's padding sits outside the account card background`() {
        setCard { AccountCard(account, onClick = {}, modifier = it) }

        assertColor(pixelAt(CALLER_PADDING / 2), Page, "inside the caller's padding")
        assertColor(pixelAt(CALLER_PADDING + 4), DictColors.Light.surface1, "just inside the card")
    }

    @Test
    fun `a caller's padding sits outside the signed-out card background`() {
        setCard { SignedOutAccountCard("Not signed in", "Sync saved words", "Sign in", onSignIn = {}, modifier = it) }

        assertColor(pixelAt(CALLER_PADDING / 2), Page, "inside the caller's padding")
        assertColor(pixelAt(CALLER_PADDING + 4), DictColors.Light.surface1, "just inside the card")
    }

    @Test
    fun `the account card opens the account and the signed-out card signs in`() {
        var opened = 0
        var signIns = 0
        compose.setContent {
            DictTheme(darkTheme = false) {
                Column {
                    AccountCard(account, onClick = { opened++ })
                    SignedOutAccountCard("Not signed in", "Sync saved words", "Sign in", onSignIn = { signIns++ })
                }
            }
        }

        compose.onNodeWithText("tenzin.dolma@gmail.com").performClick()
        compose.onNodeWithText("Sign in").performClick()

        assertThat(opened).isEqualTo(1)
        assertThat(signIns).isEqualTo(1)
    }

    private companion object {
        const val TAG = "card"
        const val CALLER_PADDING = 24

        /** A page colour unlike any theme token, so the card's edge is unambiguous. */
        val Page = Color(0xFF00FF00)
    }
}
