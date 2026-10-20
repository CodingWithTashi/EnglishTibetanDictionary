package com.kharagedition.tibetandictionary.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScaffoldTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var tab by mutableStateOf(MainTab.SEARCH)

    private fun showScaffold(start: MainTab, tabs: List<MainTab> = visibleTabs(learnEnabled = false)) {
        tab = start
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                MainScaffoldContent(tab = tab, tabs = tabs, onSelectTab = { tab = it }) { current ->
                    // Per-tab saveable state: "{TAB} {taps}"; the bump button adds one tap.
                    var taps by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        Text("${current.name} $taps")
                        Text("+", Modifier.testTag("bump").clickable { taps++ })
                    }
                }
            }
        }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `back on another tab returns to Search`() {
        showScaffold(start = MainTab.PROFILE)

        pressBack()

        assertThat(tab).isEqualTo(MainTab.SEARCH)
        assertThat(compose.activity.isFinishing).isFalse()
        compose.onNodeWithText("SEARCH 0").assertExists()
    }

    @Test
    fun `back on the Search tab leaves the app`() {
        showScaffold(start = MainTab.SEARCH)

        pressBack()

        assertThat(tab).isEqualTo(MainTab.SEARCH)
        assertThat(compose.activity.isFinishing).isTrue()
    }

    @Test
    fun `tapping a tab shows its content`() {
        showScaffold(start = MainTab.SEARCH)

        compose.onNodeWithTag("nav_saved").performClick()

        assertThat(tab).isEqualTo(MainTab.SAVED)
        compose.onNodeWithText("SAVED 0").assertExists()
    }

    @Test
    fun `a tab keeps its saved state while another tab is shown`() {
        showScaffold(start = MainTab.SAVED)
        compose.onNodeWithTag("bump").performClick()
        compose.onNodeWithText("SAVED 1").assertExists()

        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithText("PROFILE 0").assertExists()
        compose.onNodeWithTag("nav_saved").performClick()

        compose.onNodeWithText("SAVED 1").assertExists()
    }

    @Test
    fun `only the visible tabs are in the bar`() {
        showScaffold(start = MainTab.SEARCH, tabs = visibleTabs(learnEnabled = false))

        compose.onNodeWithTag("nav_learn").assertDoesNotExist()
        compose.onNodeWithTag("nav_profile").assertExists()
    }
}
