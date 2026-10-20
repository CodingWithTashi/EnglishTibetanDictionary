package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.Jomolhari
import com.kharagedition.tibetandictionary.ui.theme.NotoSans
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToastTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showToast(message: CharSequence, action: String?, onAction: (suspend () -> Unit)?) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                val host = remember { SnackbarHostState() }
                val toaster = remember(host) { Toaster(host) }
                CompositionLocalProvider(LocalToaster provides toaster) {
                    Box(Modifier.fillMaxSize()) {
                        AppToastHost(host, rootHeightPx = 0, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                    val shown = LocalToaster.current
                    LaunchedEffect(Unit) { shown.show(message, action, onAction = onAction) }
                }
            }
        }
    }

    @Test
    fun `tapping the action runs its callback`() {
        var undone = 0
        showToast("Removed", action = "Undo", onAction = { undone++ })

        compose.onNodeWithText("Removed").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()

        assertThat(undone).isEqualTo(1)
        compose.onNodeWithText("Removed").assertDoesNotExist()
    }

    @Test
    fun `a Tibetan action label is drawn in the Tibetan font`() {
        showToast("བཀྲ་ཤིས། removed", action = "ཕྱིར་ལོག", onAction = {})

        assertThat(fontOf("ཕྱིར་ལོག")).isEqualTo(Jomolhari)
    }

    @Test
    fun `a Latin action label stays in the Latin font`() {
        showToast("Removed", action = "Undo", onAction = {})

        assertThat(fontOf("Undo")).isEqualTo(NotoSans)
    }

    /** The font family [text] is laid out in. */
    private fun fontOf(text: String): Any? {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontFamily
    }

    @Test
    fun `a toast without an action shows only its message`() {
        showToast("Saved བཀྲ་ཤིས། to Daily", action = null, onAction = null)

        compose.onNodeWithText("Saved བཀྲ་ཤིས། to Daily").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `the toast closes by itself after its timeout`() {
        var undone = 0
        showToast("Removed", action = "Undo", onAction = { undone++ })
        compose.onNodeWithText("Removed").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(Toaster.WITH_ACTION_MS + 1_000)
        compose.waitForIdle()

        compose.onNodeWithText("Removed").assertDoesNotExist()
        assertThat(undone).isEqualTo(0)
    }
}
