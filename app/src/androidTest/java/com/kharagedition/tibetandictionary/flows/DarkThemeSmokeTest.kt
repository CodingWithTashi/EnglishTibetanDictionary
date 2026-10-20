package com.kharagedition.tibetandictionary.flows

import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.test.filters.SdkSuppress
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

/**
 * Profile → Theme → Dark: the activity comes back in night mode and Profile, Home, Search and Entry draw on the dark
 * background token. Pixels are read from the window (`captureToImage`, API 26+).
 */
@HiltAndroidTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class DarkThemeSmokeTest : AppFlowTest() {

    @Before
    fun onHome() = waitForHome()

    @Test
    fun darkThemeDrawsHomeSearchAndEntryOnTheDarkBackground() {
        openTab("profile", "profile_screen")
        val dark = hasText("Dark") and isSelectable() and hasAnyAncestor(hasTestTag("profile_row_theme"))
        tapAndWait(dark, dark and isSelected())
        awaitPrefs { it.appearance == Appearance.DARK }
        awaitNightModeActivity()

        // The scaffold keeps the Profile tab across the recreation.
        waitFor(hasTestTag("profile_screen"))
        waitFor(dark and isSelected())
        assertBackground(DictColors.Dark.background, "Profile")

        openTab("search", "home_screen")
        assertBackground(DictColors.Dark.background, "Home")

        openSearchFromHome()
        waitFor(hasTestTag("search_screen"))
        assertBackground(DictColors.Dark.background, "Search")

        typeQuery("bkra")
        val firstRow = hasTestTagPrefix("result_row_")
        waitFor(firstRow)
        tapAndWait(hasTestTag(tagOf(firstRow)), hasTestTag("entry_screen"))
        waitFor(hasTestTag("entry_save"))
        assertBackground(DictColors.Dark.background, "Entry")
    }

    /**
     * Waits for the resumed activity to run in night mode: AppCompat recreates it when the theme changes the
     * configuration (not when the system is already dark), and pixels must not be read mid-recreation.
     */
    private fun awaitNightModeActivity() {
        compose.waitUntil("a resumed activity in night mode", UI_TIMEOUT_MS) {
            val uiMode = resumedActivity()?.resources?.configuration?.uiMode ?: return@waitUntil false
            (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        compose.waitForIdle()
    }
}
