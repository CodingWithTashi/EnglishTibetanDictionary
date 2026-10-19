package com.kharagedition.tibetandictionary

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Launches the real app (Hilt graph, [DictionaryApplication], `AppInitializer`) under Robolectric. Until L1 ships
 * the installer, its stub fails every install, so start-up must end on the install-failed screen, not a crash.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = DictionaryApplication::class)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `cold start reaches the install screen without crashing`() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Try again").fetchSemanticsNodes().isNotEmpty()
        }

        assertThat(compose.activityRule.scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Couldn't prepare the dictionary.").assertExists()
    }
}
