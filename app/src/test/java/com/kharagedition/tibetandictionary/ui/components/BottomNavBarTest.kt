package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.Jomolhari
import com.kharagedition.tibetandictionary.ui.theme.NotoSans
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class BottomNavBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val releaseTabs = listOf(MainTab.SEARCH, MainTab.SAVED, MainTab.PROFILE)

    private fun SemanticsNodeInteraction.texts(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** Font family of the primary label, read from the laid-out text. */
    private fun primaryFamily(tab: String): FontFamily? {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("nav_label_$tab", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontFamily
    }

    @Test
    fun `English UI puts the Latin label first with a Tibetan gloss`() {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                BottomNavBar(releaseTabs, MainTab.SEARCH, onSelect = {}, language = AppLanguage.EN)
            }
        }

        assertThat(compose.onNodeWithTag("nav_search").texts()).containsExactly("Search", "འཚོལ།").inOrder()
        assertThat(compose.onNodeWithTag("nav_profile").texts()).containsExactly("Profile", "སྒེར།").inOrder()
        assertThat(primaryFamily("search")).isEqualTo(NotoSans)
    }

    @Test
    @Config(qualifiers = "bo")
    fun `Tibetan locale puts the Tibetan label first with a Latin gloss`() {
        compose.setContent {
            // DictTheme derives the language from the bo configuration.
            DictTheme(darkTheme = false) {
                BottomNavBar(releaseTabs, MainTab.SAVED, onSelect = {}, language = DictTheme.language)
            }
        }

        assertThat(compose.onNodeWithTag("nav_search").texts()).containsExactly("འཚོལ།", "Search").inOrder()
        assertThat(compose.onNodeWithTag("nav_saved").texts()).containsExactly("ཉར་ཚགས།", "Saved").inOrder()
        assertThat(primaryFamily("saved")).isEqualTo(Jomolhari)
    }

    @Test
    fun `only the given tabs are shown and the selected one is marked`() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                BottomNavBar(releaseTabs, MainTab.SAVED, onSelect = {}, language = AppLanguage.EN)
            }
        }

        compose.onNodeWithTag("nav_learn").assertDoesNotExist()
        compose.onNodeWithTag("nav_saved").assertIsSelected()
        compose.onNodeWithTag("nav_search").assertIsNotSelected()
    }

    @Test
    fun `tapping a tab reports it`() {
        var picked: MainTab? = null
        compose.setContent {
            DictTheme(darkTheme = false) {
                BottomNavBar(MainTab.entries, MainTab.SEARCH, onSelect = { picked = it }, language = AppLanguage.EN)
            }
        }

        compose.onNodeWithTag("nav_learn").performClick()

        assertThat(picked).isEqualTo(MainTab.LEARN)
    }
}
