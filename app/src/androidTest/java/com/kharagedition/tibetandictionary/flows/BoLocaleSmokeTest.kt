package com.kharagedition.tibetandictionary.flows

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

/**
 * Profile → App language → བོད་ཡིག: the activity comes back in Tibetan, the bottom bar's primary labels are the
 * Tibetan ones and the Saved title reads ཉར་ཚགས།; English is chosen again at the end. If the test fails while the
 * UI is Tibetan, [AppFlowTest.freshApp] drops that language once the activity has closed, so the next test still
 * starts in English.
 */
@HiltAndroidTest
class BoLocaleSmokeTest : AppFlowTest() {

    @Before
    fun onHome() = waitForHome()

    @Test
    fun tibetanUiRelabelsTheNavigationAndTheSavedTitle() {
        // Starts in English: the device language is English and no Tibetan per-app language survives a test.
        TABS_EN.forEach { (tab, label) -> waitFor(navLabel(tab, label), unmerged = true) }
        openTab("profile", "profile_screen")
        chooseLanguage(LANGUAGE_BO)

        // The bottom bar's primary label (tagged nav_label_*) is the Tibetan one, over a Latin gloss.
        TABS_BO.forEach { (tab, label) -> waitFor(navLabel(tab, label), unmerged = true) }
        awaitPrefs { it.appLanguage == AppLanguage.BO }

        openTab("saved", "saved_screen")
        waitFor(isHeading() and hasTextStartingWith(SAVED_BO) and hasAnyAncestor(hasTestTag("saved_screen")))

        openTab("profile", "profile_screen")
        chooseLanguage(LANGUAGE_EN)
        TABS_EN.forEach { (tab, label) -> waitFor(navLabel(tab, label), unmerged = true) }
        awaitPrefs { it.appLanguage == AppLanguage.EN }
    }

    /** Opens the language sheet from the Profile row and picks [name]; the activity is recreated in it. */
    private fun chooseLanguage(name: String) {
        waitFor(hasTestTag("profile_row_language"))
        compose.onNodeWithTag("profile_row_language").performClick()
        // The sheet's option cards are selectable; the row behind them, which names the current language, is not.
        val option = hasText(name) and isSelectable()
        waitForOne(option)
        compose.onNode(option).performClick()
    }

    private fun navLabel(tab: String, label: String) = hasTestTag("nav_label_$tab") and hasText(label)

    private companion object {
        const val LANGUAGE_BO = "བོད་ཡིག"
        const val LANGUAGE_EN = "English"
        const val SAVED_BO = "ཉར་ཚགས།"
        val TABS_BO = listOf("search" to "འཚོལ།", "saved" to SAVED_BO, "profile" to "སྒེར།")
        val TABS_EN = listOf("search" to "Search", "saved" to "Saved", "profile" to "Profile")
    }
}
