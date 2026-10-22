package com.kharagedition.tibetandictionary.ui.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedWhenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val requester = FocusRequester()
    private var focused = false

    /** A focusable nested two levels below the modifier under test, like the screens under the overlay. */
    private fun show(blocked: Boolean) {
        compose.setContent {
            Box(Modifier.blockedWhen(blocked)) {
                Column {
                    Box(
                        Modifier
                            .size(20.dp)
                            .focusRequester(requester)
                            .onFocusChanged { focused = it.isFocused }
                            .focusable(),
                    )
                }
            }
        }
    }

    /** Gives the compose view Android focus, which makes Compose hand focus to the first focusable it may use. */
    private fun focusTheView() {
        compose.runOnIdle {
            compose.activity.window.decorView.findViewById<android.view.View>(android.R.id.content).requestFocus()
        }
        compose.waitForIdle()
    }

    @Test
    fun `control - a nested focusable gets focus when not blocked`() {
        show(blocked = false)
        focusTheView()
        compose.runOnIdle { requester.requestFocus() }
        compose.waitForIdle()

        assertThat(focused).isTrue()
    }

    @Test
    fun `a nested focusable cannot take focus while blocked`() {
        show(blocked = true)
        focusTheView()
        compose.runOnIdle { requester.requestFocus() }
        compose.waitForIdle()

        assertThat(focused).isFalse()
    }

    @Test
    fun `moving focus with the keyboard cannot enter the blocked subtree`() {
        show(blocked = true)
        focusTheView()
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()

        assertThat(focused).isFalse()
    }

    @Test
    fun `control - moving focus with the keyboard enters the subtree when not blocked`() {
        show(blocked = false)
        focusTheView()
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()

        assertThat(focused).isTrue()
    }
}
