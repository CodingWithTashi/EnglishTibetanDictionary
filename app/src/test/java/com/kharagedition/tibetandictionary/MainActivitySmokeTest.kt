package com.kharagedition.tibetandictionary

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.app.APP_ROOT_TAG
import com.kharagedition.tibetandictionary.ui.app.INSTALLING_SCREEN_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Launches the real app ([DictionaryApplication], the Hilt graph, `AppInitializer` and the bound installer) under
 * Robolectric: start-up must get past the splash to the installing screen or the app, without crashing.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = DictionaryApplication::class)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shown(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `cold start gets past the splash without crashing`() {
        compose.waitUntil(timeoutMillis = 10_000) { shown(INSTALLING_SCREEN_TAG) || shown(APP_ROOT_TAG) }

        assertThat(compose.activityRule.scenario.state).isEqualTo(Lifecycle.State.RESUMED)
    }
}
