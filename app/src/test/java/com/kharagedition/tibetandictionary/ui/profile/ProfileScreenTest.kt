package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<ProfileEvent>()

    private val shipped = ProfileUiState(
        textSize = AppTextSize.DEFAULT,
        lineSpacing = 1.9f,
        appearance = Appearance.SYSTEM,
        defaultDirection = SampleData.boEn,
        directions = SampleData.directions,
        language = AppLanguage.EN,
        versionName = "3.0.0",
        entryCount = 180_412,
        links = listOf(ProfileLink.ABOUT, ProfileLink.RATE, ProfileLink.SHARE, ProfileLink.MORE_APPS),
        otherApps = OtherApp.entries,
    )

    private fun show(state: ProfileUiState, language: AppLanguage = AppLanguage.EN) {
        compose.setContent {
            DictTheme(darkTheme = false, language = language) {
                ProfileScreen(state, onEvent = { events += it })
            }
        }
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun `the reading rows show the current settings`() {
        show(shipped.copy(textSize = AppTextSize.LARGE, lineSpacing = 2.1f))

        compose.onNodeWithTag(PROFILE_SCREEN_TAG).assertExists()
        compose.onNodeWithText("Profile", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Large · 2.1").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithTag("profile_row_direction").assertIsDisplayed()
        compose.onNodeWithTag("profile_row_theme").assertIsDisplayed()
    }

    @Test
    fun `rows open their sheets`() {
        show(shipped)

        tap("profile_row_text_size")
        tap("profile_row_direction")
        tap("profile_row_language")

        assertThat(events).containsExactly(
            ProfileEvent.OpenSheet(ProfileSheet.TEXT_SIZE),
            ProfileEvent.OpenSheet(ProfileSheet.DIRECTION),
            ProfileEvent.OpenSheet(ProfileSheet.LANGUAGE),
        ).inOrder()
    }

    @Test
    fun `the theme control sets the appearance`() {
        show(shipped)

        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Light").performClick()

        assertThat(events).containsExactly(
            ProfileEvent.SetAppearance(Appearance.DARK),
            ProfileEvent.SetAppearance(Appearance.LIGHT),
        ).inOrder()
    }

    @Test
    fun `no direction row while no direction has entries`() {
        show(shipped.copy(defaultDirection = null, directions = emptyList()))

        compose.onNodeWithTag("profile_row_direction").assertDoesNotExist()
    }

    @Test
    fun `the footer shows only the visible links`() {
        show(shipped)

        listOf("about", "rate", "share", "more").forEach { compose.onNodeWithTag("profile_link_$it").assertExists() }
        compose.onNodeWithTag("profile_link_support").assertDoesNotExist()
        compose.onNodeWithTag("profile_link_privacy").assertDoesNotExist()
    }

    @Test
    fun `footer links send their link`() {
        show(shipped.copy(links = ProfileLink.entries))

        tap("profile_link_about")
        tap("profile_link_rate")
        tap("profile_link_share")
        tap("profile_link_more")
        tap("profile_link_support")
        tap("profile_link_privacy")

        assertThat(events).containsExactlyElementsIn(ProfileLink.entries.map(ProfileEvent::OpenLink)).inOrder()
    }

    @Test
    fun `the version line shows the entry count`() {
        show(shipped)

        compose.onNodeWithText("Tibetan Dictionary 3.0.0 · 180,412 entries · offline").performScrollTo()
    }

    @Test
    fun `the version line leaves the count out when it is unknown`() {
        show(shipped.copy(entryCount = null))

        compose.onNodeWithText("Tibetan Dictionary 3.0.0 · offline").performScrollTo()
    }

    @Test
    fun `the version line uses Tibetan digits in the Tibetan UI`() {
        show(shipped, language = AppLanguage.BO)

        compose.onNodeWithText("༡༨༠,༤༡༢", substring = true).performScrollTo()
        compose.onNodeWithText("༣.༠.༠", substring = true).assertExists()
    }

    @Test
    fun `the language sheet picks a language`() {
        show(shipped.copy(sheet = ProfileSheet.LANGUAGE))

        compose.onNodeWithText("བོད་ཡིག").performClick()

        assertThat(events).containsExactly(ProfileEvent.SetLanguage(AppLanguage.BO))
    }

    @Test
    fun `the direction sheet picks the default direction`() {
        show(shipped.copy(sheet = ProfileSheet.DIRECTION))

        compose.onNodeWithText("English to Tibetan").performClick()

        assertThat(events).containsExactly(ProfileEvent.SetDefaultDirection(SampleData.enBo))
    }

    @Test
    fun `the text size sheet shows the sliders`() {
        show(shipped.copy(sheet = ProfileSheet.TEXT_SIZE))

        compose.onNodeWithText("Line spacing").assertExists()
        compose.onNodeWithText("Text size").assertExists()
    }

    @Test
    fun `the more apps sheet opens an app`() {
        show(shipped.copy(sheet = ProfileSheet.MORE_APPS))

        compose.onNodeWithText("Tibetan Calendar").performClick()

        assertThat(events).containsExactly(ProfileEvent.OpenOtherApp(OtherApp.CALENDAR))
    }
}
