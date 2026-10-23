package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.ui.model.AccountUi
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
        // The account and plan cards sit above the Reading group: scroll each row into view.
        compose.onNodeWithText("Large · 2.1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("English").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile_row_direction").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile_row_theme").performScrollTo().assertIsDisplayed()
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
    fun `the dictionary row shows the active pack and opens the sheet`() {
        show(shipped.copy(dictionary = DictionaryPack.DZONGKHA))

        compose.onNodeWithTag("profile_row_dictionary").performScrollTo().assertIsDisplayed()
        compose.onNode(hasTestTag("profile_row_dictionary") and hasText("Dzongkha", substring = true)).assertExists()
        // The version line at the foot names the pack too.
        compose.onNodeWithText("Dzongkha Dictionary 3.0.0", substring = true).assertExists()
        tap("profile_row_dictionary")

        assertThat(events).containsExactly(ProfileEvent.OpenSheet(ProfileSheet.DICTIONARY))
    }

    @Test
    fun `the dictionary sheet offers both packs and picks one`() {
        show(shipped.copy(sheet = ProfileSheet.DICTIONARY))

        compose.onNodeWithTag("dictionary_option_bo").assertIsDisplayed()
        compose.onNodeWithTag("dictionary_option_dz").assertIsDisplayed()
        compose.onNodeWithTag("dictionary_option_dz").performClick()

        assertThat(events).containsExactly(ProfileEvent.SetDictionary(DictionaryPack.DZONGKHA))
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

    // Account, plan and search (1z)

    private val tenzin = AccountUi(name = "Tenzin Dolma", email = "tenzin@example.com", initials = "TD")

    @Test
    fun `signed out card button sends sign-in`() {
        show(shipped)

        compose.onNodeWithText("Sign in to get 10 free translations a day").assertIsDisplayed()
        compose.onNode(hasText("Sign in") and hasAnyAncestor(hasTestTag("profile_account_card"))).performClick()

        assertThat(events).containsExactly(ProfileEvent.SignIn(PromptSource.PROFILE_ACCOUNT))
    }

    @Test
    fun `a signed-in account shows the name and opens the account sheet`() {
        show(shipped.copy(account = tenzin, plan = PlanCardUi.Free(used = 3, limit = 10, maxChars = 100)))

        compose.onNodeWithText("Tenzin Dolma").assertIsDisplayed()
        compose.onNodeWithText("tenzin@example.com").assertIsDisplayed()
        tap("profile_account_card")

        assertThat(events).containsExactly(ProfileEvent.OpenAccount)
    }

    @Test
    fun `plan card shows 3 of 10 translations used today · 100 characters each`() {
        show(shipped.copy(account = tenzin, plan = PlanCardUi.Free(used = 3, limit = 10, maxChars = 100)))

        compose.onNodeWithText("Free plan").assertIsDisplayed()
        compose.onNodeWithText("3 of 10 translations used today · 100 characters each").assertIsDisplayed()
        tap("profile_plan_upgrade")

        assertThat(events).containsExactly(ProfileEvent.OpenPro)
    }

    @Test
    fun `a guest's plan card offers sign-in for the free plan's limit`() {
        show(shipped.copy(plan = PlanCardUi.Guest(used = 1, limit = 3, freeLimit = 10)))

        compose.onNodeWithText("Guest").assertIsDisplayed()
        compose.onNodeWithText("1 of 3 used today").assertIsDisplayed()
        compose.onNodeWithText("Sign in for 10").assertIsDisplayed()
        tap("profile_plan_sign_in")

        assertThat(events).containsExactly(ProfileEvent.SignIn(PromptSource.PROFILE_PLAN))
    }

    @Test
    fun `without a known free limit the guest button says sign in for more`() {
        show(shipped.copy(plan = PlanCardUi.Guest(used = 1, limit = 3, freeLimit = null)))
        compose.onNodeWithText("Sign in for more").assertIsDisplayed()
    }

    @Test
    fun `no user gets the plan pitch`() {
        show(shipped.copy(plan = PlanCardUi.NoUser))

        compose.onNodeWithTag("profile_plan_card").performScrollTo()
        compose.onNodeWithText("Translate sentences free every day. Sign in for more.").assertIsDisplayed()
    }

    @Test
    fun `the account sheet signs out or asks to delete`() {
        show(shipped.copy(account = tenzin, sheet = ProfileSheet.ACCOUNT))

        compose.onNodeWithTag("profile_sign_out").performClick()
        compose.onNodeWithTag("profile_delete_account").performClick()

        assertThat(events).containsExactly(ProfileEvent.SignOut, ProfileEvent.AskDeleteAccount).inOrder()
    }

    @Test
    fun `delete asks for confirmation`() {
        show(shipped.copy(account = tenzin, confirmDelete = true))

        compose.onNodeWithTag("profile_delete_confirm").assertIsDisplayed()
        compose.onNodeWithText("Delete your account?").assertIsDisplayed()
        compose.onNodeWithText("Your usage history is deleted. Saved words and translations stay on this phone.")
            .assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertThat(events).containsExactly(ProfileEvent.DismissDelete, ProfileEvent.ConfirmDeleteAccount).inOrder()
    }

    @Test
    fun `while deleting, the dialog says so and its buttons do nothing`() {
        show(shipped.copy(account = tenzin, confirmDelete = true, deleting = true))

        compose.onNodeWithText("Deleting…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        assertThat(events).isEmpty()
    }

    @Test
    fun `smart translate switch toggles`() {
        show(shipped)

        compose.onNodeWithText("Auto-translate phrases with no dictionary match").performScrollTo().assertIsDisplayed()
        tap("profile_row_smart_translate")

        assertThat(events).containsExactly(ProfileEvent.ToggleSmartTranslate)
    }

    @Test
    fun `the pro sheet shows when asked`() {
        show(shipped.copy(showProSheet = true))

        compose.onNodeWithTag("pro_sheet").assertExists()
    }
}
