package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.main.visibleTabs
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.Jomolhari
import com.kharagedition.tibetandictionary.ui.theme.NotoSans
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class BottomNavBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val releaseTabs = visibleTabs(learnEnabled = false)

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
    fun `the bar shows Search, Translate, Saved, Profile in order`() {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                BottomNavBar(releaseTabs, MainTab.SEARCH, onSelect = {}, language = AppLanguage.EN)
            }
        }

        val items = SemanticsMatcher("nav item") { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
            tag.startsWith("nav_") && !tag.startsWith("nav_label_")
        }
        val tags = compose.onAllNodes(items).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertThat(tags).containsExactly("nav_search", "nav_translate", "nav_saved", "nav_profile").inOrder()
        assertThat(compose.onNodeWithTag("nav_translate").texts()).containsExactly("Translate", "ཡིག་སྒྱུར།").inOrder()
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

    /** Line counts of every text in the bar, read from the laid-out texts. */
    private fun lineCounts(): List<Int> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.single().lineCount
            }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
    fun `every label and gloss fits on one line with four tabs on a 320dp screen`() {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                BottomNavBar(releaseTabs, MainTab.SEARCH, {}, AppLanguage.EN, Modifier.testTag(BAR))
            }
        }

        assertThat(lineCounts()).containsExactlyElementsIn(List(releaseTabs.size * 2) { 1 })
        compose.onNodeWithTag(BAR).assertHeightIsEqualTo(88.dp)
    }

    @Test
    @Config(qualifiers = "bo")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
    fun `every Tibetan label and Latin gloss fits on one line with four tabs on a 320dp screen`() {
        compose.setContent {
            DictTheme(darkTheme = false) {
                BottomNavBar(releaseTabs, MainTab.SAVED, {}, DictTheme.language, Modifier.testTag(BAR))
            }
        }

        assertThat(lineCounts()).containsExactlyElementsIn(List(releaseTabs.size * 2) { 1 })
        // Jomolhari 15 over Latin 11 needs about 83dp: the 88dp bar (72 in the design) holds it.
        compose.onNodeWithTag(BAR).assertHeightIsEqualTo(88.dp)
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

    private companion object {
        const val BAR = "bar"
    }
}
