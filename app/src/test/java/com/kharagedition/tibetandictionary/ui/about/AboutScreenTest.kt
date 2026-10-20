package com.kharagedition.tibetandictionary.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var backs = 0
    private var emails = 0

    private val state = AboutUiState(
        versionName = "3.0.0",
        entryCount = 180_412,
        attribution = SampleData.attribution.text,
    )

    private fun show(state: AboutUiState) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                AboutScreen(state, onBack = { backs++ }, onEmail = { emails++ })
            }
        }
    }

    @Test
    fun `About shows the app, the data source and the fonts`() {
        show(state)

        compose.onNodeWithTag(ABOUT_SCREEN_TAG).assertExists()
        compose.onNodeWithText("བོད་ཡིག་ཚིག་མཛོད།").assertIsDisplayed()
        compose.onNodeWithText("Tibetan Dictionary 3.0.0 · 180,412 entries · offline").assertIsDisplayed()
        compose.onNodeWithText("Dictionary data: Monlam Tibetan Dictionary by Lobsang Monlam (GPL-3)").assertExists()
        compose.onNodeWithText(SampleData.attribution.text).assertExists()
        compose.onNodeWithText("Fonts: Jomolhari and Noto Sans (SIL Open Font License)").assertExists()
    }

    @Test
    fun `the data's own attribution is left out when it has none`() {
        show(state.copy(attribution = null))

        compose.onNodeWithText(SampleData.attribution.text).assertDoesNotExist()
        compose.onNodeWithText("Dictionary data: Monlam Tibetan Dictionary by Lobsang Monlam (GPL-3)").assertExists()
    }

    @Test
    fun `the correction e-mail row writes to the developer`() {
        show(state)

        compose.onNodeWithText(Links.DEV_EMAIL).assertExists()
        compose.onNodeWithTag("about_email").performScrollTo().performClick()

        assertThat(emails).isEqualTo(1)
    }

    @Test
    fun `back pops the route`() {
        show(state)

        compose.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }
}
