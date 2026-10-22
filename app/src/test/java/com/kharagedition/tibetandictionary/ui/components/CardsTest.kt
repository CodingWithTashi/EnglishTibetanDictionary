package com.kharagedition.tibetandictionary.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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

    /** Colour of the pixel at ([xDp], [yDp]) from the top-left corner of the tagged element. */
    private fun pixelAt(xDp: Float, yDp: Float): Color {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
        val density = compose.activity.resources.displayMetrics.density
        val window = compose.runOnUiThread { compose.activity.window.decorView.drawToBitmap() }
        return Color(window.getPixel((bounds.left + xDp * density).toInt(), (bounds.top + yDp * density).toInt()))
    }

    private fun setWordOfTheDay(dark: Boolean) {
        compose.setContent {
            DictTheme(darkTheme = dark) {
                Box(Modifier.background(Page).testTag(TAG)) { WordOfTheDayCard(PreviewSamples.wotd, {}, {}, {}, {}) }
            }
        }
    }

    @Test
    fun `the word-of-the-day strand hangs whole from the card's top edge`() {
        setWordOfTheDay(dark = false)
        val colors = DictColors.Light
        // The 1dp line is the card's first row, 20dp in from the card's edge (past the 16dp corner).
        assertColor(pixelAt(STRAND_START + 2f, 0.5f), colors.flagLine.compositeOver(colors.surface1), "strand line")
        assertColor(pixelAt(STRAND_START - 2f, 0.5f), colors.surface1, "card before the strand")
        // Each 12×8 flag hangs from the line, whole: its first and last rows are drawn, the row below is the card.
        val blue = STRAND_START + 6f + 6f
        assertColor(pixelAt(blue, 1.5f), colors.flagBlue, "top row of the blue flag")
        assertColor(pixelAt(blue, 8.5f), colors.flagBlue, "bottom row of the blue flag")
        assertColor(pixelAt(blue, 9.5f), colors.surface1, "card below the blue flag")
        assertColor(pixelAt(blue + 3 * 15f, 5f), colors.flagGreen, "green flag")
        assertColor(pixelAt(blue + 4 * 15f, 5f), colors.flagYellow, "yellow flag")
    }

    @Test
    fun `the word-of-the-day date is shown in the theme language's digits`() {
        var language by mutableStateOf(AppLanguage.EN)
        compose.setContent {
            DictTheme(darkTheme = false, language = language) { WordOfTheDayCard(PreviewSamples.wotd, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("16 Oct").assertExists()

        language = AppLanguage.BO
        compose.onNodeWithText("༡༦ Oct").assertExists()
    }

    @Test
    @Config(qualifiers = "bo")
    fun `a system language of bo with no in-app choice shows the date in Tibetan digits`() {
        // The app root's theme takes its language from the configuration, as here.
        compose.setContent { DictTheme(darkTheme = false) { WordOfTheDayCard(PreviewSamples.wotd, {}, {}, {}, {}) } }

        compose.onNodeWithText("༡༦ Oct").assertExists()
    }

    @Test
    fun `the white flag takes the page colour on the light card`() {
        setWordOfTheDay(dark = false)
        assertColor(pixelAt(WHITE_FLAG, 5f), DictColors.Light.background, "light white flag")
    }

    @Test
    fun `the white flag keeps its own colour on the dark card`() {
        setWordOfTheDay(dark = true)
        assertColor(pixelAt(WHITE_FLAG, 5f), DictColors.Dark.flagWhite, "dark white flag")
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

    /** The reading preview and the greeting as a screen under the app root with [active] in use shows them. */
    private fun showPackContent(active: DictionaryPack) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                CompositionLocalProvider(LocalActivePack provides active) {
                    Column {
                        Greeting(TimeOfDay.EVENING)
                        PreviewCard(AppTextSize.DEFAULT, LineSpacing.DEFAULT)
                    }
                }
            }
        }
    }

    @Test
    fun `the greeting and the reading preview follow the Tibetan dictionary`() {
        showPackContent(DictionaryPack.TIBETAN)

        compose.onNodeWithText("Good evening · དགོང་དྲོ་བདེ་ལེགས།").assertExists()
        compose.onNodeWithText("bkra shis").assertExists()
        compose.onNodeWithText("1\u00A0 auspicious, fortunate", substring = true).assertExists()
        compose.onNodeWithText("བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།").assertExists()
    }

    @Test
    fun `the greeting and the reading preview follow the Dzongkha dictionary`() {
        showPackContent(DictionaryPack.DZONGKHA)

        compose.onNodeWithText("Good evening · ཕྱི་རུ་བདེ་ལེགས།").assertExists()
        // A single gloss is not numbered.
        compose.onNodeWithText("auspicious, good, prosperity").assertExists()
        compose.onNodeWithText("ལེགས་ཤོམ། དགེ་བ་ཕུན་སུམ་ཚོགས་མི་ཅིག་ལུ་སླབཔ་ཨིན།").assertExists()
        compose.onNodeWithText("དགོང་དྲོ་བདེ་ལེགས།", substring = true).assertDoesNotExist()
    }

    private companion object {
        const val TAG = "card"
        const val CALLER_PADDING = 24

        /** The strand's line starts at the word-of-the-day card's 20dp body inset. */
        const val STRAND_START = 20f

        /** Centre of the second (white) flag: 6dp start pad, one 12dp flag and a 3dp gap, then half a flag. */
        const val WHITE_FLAG = STRAND_START + 6f + 15f + 6f

        /** A page colour unlike any theme token, so the card's edge is unambiguous. */
        val Page = Color(0xFF00FF00)
    }
}
