package com.kharagedition.tibetandictionary.ui.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.model.UiText

/**
 * The Profile tab (2f/3d/1z). [dictionary] is the pack in use. [defaultDirection] is the stored default, or the first
 * visible direction when the stored one has no entries (null only when no direction has entries); [directions] are
 * the visible ones in switcher order. [entryCount] is null when unknown (the version line then leaves it out).
 * [account] is the signed-in Google account (null for a guest or no user: the signed-out card shows);
 * [wotdNotification] stays null while its feature is off. [plan] is the plan card, null while a Google account's usage
 * is not known yet. [smartTranslate] is the Search group's switch; [confirmDelete] shows the delete dialog,
 * [deleting] says its delete is running, and [showProSheet] shows the Pro sheet.
 */
@Immutable
data class ProfileUiState(
    val account: AccountUi? = null,
    val textSize: AppTextSize = AppTextSize.DEFAULT,
    val lineSpacing: Float = LineSpacing.DEFAULT,
    val appearance: Appearance = Appearance.SYSTEM,
    val dictionary: DictionaryPack = DictionaryPack.TIBETAN,
    val defaultDirection: Direction? = null,
    val directions: List<Direction> = emptyList(),
    val language: AppLanguage = AppLanguage.EN,
    val wotdNotification: WotdNotifUi? = null,
    val versionName: String = "",
    val entryCount: Int? = null,
    val links: List<ProfileLink> = emptyList(),
    val otherApps: List<OtherApp> = emptyList(),
    val sheet: ProfileSheet? = null,
    val plan: PlanCardUi? = PlanCardUi.NoUser,
    val smartTranslate: Boolean = true,
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    val showProSheet: Boolean = false,
)

/** The plan card (1z): today's translations for whoever is translating. */
@Immutable
sealed interface PlanCardUi {
    /** No Firebase user yet (nothing translated): the pitch. */
    data object NoUser : PlanCardUi

    /** A guest: [used] of [limit] today, and the free plan's [freeLimit] (null until known) for "Sign in for 10". */
    data class Guest(val used: Int, val limit: Int, val freeLimit: Int?) : PlanCardUi

    /** The free plan: [used] of [limit] today, [maxChars] characters each, and Upgrade. */
    data class Free(val used: Int, val limit: Int, val maxChars: Int) : PlanCardUi
}

/** The word-of-the-day notification setting; [minuteOfDay] is minutes after local midnight. */
@Immutable
data class WotdNotifUi(val enabled: Boolean, val minuteOfDay: Int)

/** The bottom sheet open over Profile; ACCOUNT is the signed-in account's Sign out / Delete account. */
enum class ProfileSheet { TEXT_SIZE, DICTIONARY, DIRECTION, LANGUAGE, MORE_APPS, ACCOUNT }

/** Footer links, in display order. */
enum class ProfileLink { ABOUT, RATE, SHARE, MORE_APPS, SUPPORT, PRIVACY }

/**
 * The developer's other apps, listed by the "More apps" sheet to the users of [pack], in declaration order; an app with
 * a blank [url] is left out. The calendar app is listed to both, under the name its listing uses for each.
 */
enum class OtherApp(@param:StringRes val label: Int, val url: String, val pack: DictionaryPack) {
    PRAYER(R.string.other_app_prayer, Links.DAILY_PRAYER_URL, DictionaryPack.TIBETAN),
    CALENDAR(R.string.other_app_calendar, Links.CALENDAR_URL, DictionaryPack.TIBETAN),
    DZONGKHA_KEYBOARD(R.string.other_app_dzongkha_keyboard, Links.DZONGKHA_KEYBOARD_URL, DictionaryPack.DZONGKHA),
    BHUTANESE_CALENDAR(R.string.other_app_bhutanese_calendar, Links.CALENDAR_URL, DictionaryPack.DZONGKHA),
}

/**
 * Where the footer links go; a blank target hides its link. [playStore] backs Rate and Share app; More apps lists the
 * [otherApps] of the active pack ([otherAppsFor]). The defaults are the shipped [Links].
 */
data class ProfileLinkTargets(
    val playStore: String = Links.PLAY_STORE_URL,
    val support: String = Links.SUPPORT_URL,
    val privacy: String = Links.PRIVACY_URL,
    val otherApps: List<OtherApp> = OtherApp.entries.filter { it.url.isNotBlank() },
) {
    /** The other apps listed to the users of [pack]. */
    fun otherAppsFor(pack: DictionaryPack): List<OtherApp> = otherApps.filter { it.pack == pack }

    /** The links to show while [pack] is active: About always, every other one while its target is set. */
    fun visibleFor(pack: DictionaryPack): List<ProfileLink> = ProfileLink.entries.filter { link ->
        when (link) {
            ProfileLink.ABOUT -> true
            ProfileLink.RATE, ProfileLink.SHARE -> playStore.isNotBlank()
            ProfileLink.MORE_APPS -> otherAppsFor(pack).isNotEmpty()
            ProfileLink.SUPPORT -> support.isNotBlank()
            ProfileLink.PRIVACY -> privacy.isNotBlank()
        }
    }
}

sealed interface ProfileEvent {
    /**
     * The screen is showing: re-read the app language, which the system's per-app language setting can change
     * while the app is in the background, and refresh today's translation count when it is stale.
     */
    data object Shown : ProfileEvent
    data class OpenSheet(val sheet: ProfileSheet) : ProfileEvent
    data object DismissSheet : ProfileEvent
    data class SetTextSize(val size: AppTextSize) : ProfileEvent
    data class SetLineSpacing(val spacing: Float) : ProfileEvent
    data class SetAppearance(val appearance: Appearance) : ProfileEvent
    data class SetDictionary(val pack: DictionaryPack) : ProfileEvent
    data class SetDefaultDirection(val direction: Direction) : ProfileEvent
    data class SetLanguage(val language: AppLanguage) : ProfileEvent
    data object ToggleWotdNotification : ProfileEvent
    data class OpenLink(val link: ProfileLink) : ProfileEvent
    data class OpenOtherApp(val app: OtherApp) : ProfileEvent

    /** A sign-in button ([source]: the account card or the plan card); the screen runs it with its activity. */
    data class SignIn(val source: PromptSource) : ProfileEvent

    /** The signed-in account card: opens the account sheet. */
    data object OpenAccount : ProfileEvent
    data object SignOut : ProfileEvent

    /** The account sheet's Delete account: asks first. */
    data object AskDeleteAccount : ProfileEvent
    data object ConfirmDeleteAccount : ProfileEvent
    data object DismissDelete : ProfileEvent
    data object ToggleSmartTranslate : ProfileEvent

    /** The plan card's Upgrade: the Pro sheet (a fake door, logged as pro_interest). */
    data object OpenPro : ProfileEvent
    data object DismissProSheet : ProfileEvent
}

sealed interface ProfileEffect {
    data class OpenUrl(val url: String) : ProfileEffect

    /** Share the app; the screen words the message around [listingUrl] in the UI language. */
    data class ShareApp(val listingUrl: String, val pack: DictionaryPack) : ProfileEffect

    data object NavigateAbout : ProfileEffect

    /** A toast, worded by the screen in the UI language (a setting that could not be saved, sign-in, sign-out). */
    data class Toast(val message: UiText) : ProfileEffect
}
