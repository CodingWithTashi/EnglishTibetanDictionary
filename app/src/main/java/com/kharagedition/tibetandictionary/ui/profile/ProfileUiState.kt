package com.kharagedition.tibetandictionary.ui.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.model.UiText

/**
 * The Profile tab (2f/3d). [dictionary] is the pack in use. [defaultDirection] is the stored default, or the first
 * visible direction when the stored one has no entries (null only when no direction has entries); [directions] are
 * the visible ones in switcher order. [entryCount] is null when unknown (the version line then leaves it out). [account] and [wotdNotification] stay
 * null while their features are off.
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
)

/** The word-of-the-day notification setting; [minuteOfDay] is minutes after local midnight. */
@Immutable
data class WotdNotifUi(val enabled: Boolean, val minuteOfDay: Int)

/** The bottom sheet open over Profile. */
enum class ProfileSheet { TEXT_SIZE, DICTIONARY, DIRECTION, LANGUAGE, MORE_APPS }

/** Footer links, in display order. */
enum class ProfileLink { ABOUT, RATE, SHARE, MORE_APPS, SUPPORT, PRIVACY }

/** The developer's other apps, listed by the "More apps" sheet; an app with a blank [url] is left out. */
enum class OtherApp(@param:StringRes val label: Int, val url: String) {
    PRAYER(R.string.other_app_prayer, Links.DAILY_PRAYER_URL),
    CALENDAR(R.string.other_app_calendar, Links.CALENDAR_URL),
}

/**
 * Where the footer links go; a blank target hides its link. [playStore] backs Rate and Share app; More apps lists
 * [otherApps]. The defaults are the shipped [Links].
 */
data class ProfileLinkTargets(
    val playStore: String = Links.PLAY_STORE_URL,
    val support: String = Links.SUPPORT_URL,
    val privacy: String = Links.PRIVACY_URL,
    val otherApps: List<OtherApp> = OtherApp.entries.filter { it.url.isNotBlank() },
) {
    /** The links to show: About always, every other one while its target is set. */
    val visible: List<ProfileLink> = ProfileLink.entries.filter { link ->
        when (link) {
            ProfileLink.ABOUT -> true
            ProfileLink.RATE, ProfileLink.SHARE -> playStore.isNotBlank()
            ProfileLink.MORE_APPS -> otherApps.isNotEmpty()
            ProfileLink.SUPPORT -> support.isNotBlank()
            ProfileLink.PRIVACY -> privacy.isNotBlank()
        }
    }
}

sealed interface ProfileEvent {
    /**
     * The screen is showing: re-read the app language, which the system's per-app language setting can change
     * while the app is in the background.
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
    data object SignIn : ProfileEvent
    data object OpenAccount : ProfileEvent
}

sealed interface ProfileEffect {
    data class OpenUrl(val url: String) : ProfileEffect

    /** Share the app; the screen words the message around [listingUrl] in the UI language. */
    data class ShareApp(val listingUrl: String, val pack: DictionaryPack) : ProfileEffect

    data object NavigateAbout : ProfileEffect

    /** A toast, worded by the screen in the UI language (a setting that could not be saved). */
    data class Toast(val message: UiText) : ProfileEffect
}
