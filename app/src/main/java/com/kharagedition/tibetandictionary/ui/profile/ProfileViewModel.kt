package com.kharagedition.tibetandictionary.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Profile tab: reading settings, theme, default direction and app language from [prefs], the visible directions
 * and entry count from the dictionary's capabilities, and the footer links. Settings persist as they change; the
 * language is also applied at once through [localeApplier]. [uiState] is null until prefs and capabilities load.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    dictionary: DictionaryRepository,
    private val localeApplier: LocaleApplier,
) : ViewModel() {

    private val linkTargets = ProfileLinkTargets()
    private val sheet = MutableStateFlow<ProfileSheet?>(null)

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState?> =
        combine(prefs.prefs, dictionary.capabilities, sheet) { prefs, capabilities, sheet ->
            state(prefs, capabilities, sheet)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.OpenSheet -> sheet.value = event.sheet
            ProfileEvent.DismissSheet -> sheet.value = null
            is ProfileEvent.SetTextSize -> persist { setTextSize(event.size) }
            is ProfileEvent.SetLineSpacing -> persist { setLineSpacing(event.spacing) }
            is ProfileEvent.SetAppearance -> persist { setAppearance(event.appearance) }
            is ProfileEvent.SetDefaultDirection -> {
                sheet.value = null
                persist { setDefaultDirectionId(event.direction.id) }
            }
            is ProfileEvent.SetLanguage -> {
                // Close the sheet first: applying the locale recreates the activity, and this state outlives it.
                sheet.value = null
                persist { setAppLanguage(event.language) }
                localeApplier.apply(event.language)
            }
            ProfileEvent.ToggleWotdNotification -> persist {
                val current = snapshot()
                setWotdNotification(!current.wotdNotificationEnabled, current.wotdNotificationMinute)
            }
            is ProfileEvent.OpenLink -> openLink(event.link)
            is ProfileEvent.OpenOtherApp -> {
                sheet.value = null
                send(ProfileEffect.OpenUrl(event.app.url))
            }
            // Accounts ship behind Features.ACCOUNTS; there is nothing to sign in to yet.
            ProfileEvent.SignIn, ProfileEvent.OpenAccount -> Unit
        }
    }

    private fun openLink(link: ProfileLink) {
        if (link !in linkTargets.visible) return
        when (link) {
            ProfileLink.ABOUT -> send(ProfileEffect.NavigateAbout)
            ProfileLink.RATE -> send(ProfileEffect.OpenUrl(linkTargets.playStore))
            ProfileLink.SHARE -> send(ProfileEffect.ShareApp(linkTargets.playStore))
            ProfileLink.MORE_APPS -> sheet.value = ProfileSheet.MORE_APPS
            ProfileLink.SUPPORT -> send(ProfileEffect.OpenUrl(linkTargets.support))
            ProfileLink.PRIVACY -> send(ProfileEffect.OpenUrl(linkTargets.privacy))
        }
    }

    private fun state(prefs: UserPrefs, capabilities: DictionaryCapabilities, sheet: ProfileSheet?): ProfileUiState {
        val visible = capabilities.directions.filter { it.entryCount > 0 }.sortedBy(Direction::position)
        return ProfileUiState(
            account = null,
            textSize = prefs.textSize,
            lineSpacing = prefs.lineSpacing,
            appearance = prefs.appearance,
            defaultDirection = defaultDirection(visible, prefs.defaultDirectionId),
            directions = visible,
            language = prefs.appLanguage,
            wotdNotification = if (Features.WOTD_NOTIFICATION) {
                WotdNotifUi(prefs.wotdNotificationEnabled, prefs.wotdNotificationMinute)
            } else {
                null
            },
            versionName = BuildConfig.VERSION_NAME,
            entryCount = capabilities.totalEntries.takeIf { it > 0 },
            links = linkTargets.visible,
            otherApps = linkTargets.otherApps,
            sheet = sheet,
        )
    }

    private fun persist(block: suspend UserPreferencesRepository.() -> Unit) {
        viewModelScope.launch { prefs.block() }
    }

    private fun send(effect: ProfileEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The stored default when it is visible, else the first visible direction (a data release removed or emptied
         * it). Display only: the stored preference is left as it is.
         */
        fun defaultDirection(visible: List<Direction>, storedId: String): Direction? =
            visible.firstOrNull { it.id == storedId } ?: visible.firstOrNull()
    }
}
