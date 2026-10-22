package com.kharagedition.tibetandictionary.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.LocaleApplier
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.ui.model.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
 * The Profile tab: reading settings, theme and default direction from [prefs], the visible directions and entry count
 * from the dictionary's capabilities, and the footer links to [linkTargets]. Settings persist as they change. The
 * language shown is the one the app is in ([localeApplier]), falling back to the stored one while no per-app language
 * is set; choosing another persists it and switches the app to it. [uiState] is null until prefs and capabilities load.
 * A setting that cannot be stored (DataStore's IOException on a full or damaged disk) is recorded through [errors]
 * and shown as a generic error toast; the screen keeps showing the stored value.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    dictionary: DictionaryRepository,
    private val switcher: DictionarySwitcher,
    private val localeApplier: LocaleApplier,
    private val linkTargets: ProfileLinkTargets,
    private val errors: ErrorReporter,
) : ViewModel() {

    private val appLanguage = MutableStateFlow(localeApplier.currentLanguage())
    private val sheet = MutableStateFlow<ProfileSheet?>(null)

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState?> =
        combine(
            prefs.prefs,
            dictionary.capabilities,
            appLanguage,
            sheet,
        ) { prefs, capabilities, appLanguage, sheet ->
            state(prefs, capabilities, appLanguage ?: prefs.appLanguage, sheet)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.Shown -> appLanguage.value = localeApplier.currentLanguage()
            is ProfileEvent.OpenSheet -> sheet.value = event.sheet
            ProfileEvent.DismissSheet -> sheet.value = null
            is ProfileEvent.SetTextSize -> persist { setTextSize(event.size) }
            is ProfileEvent.SetLineSpacing -> persist { setLineSpacing(event.spacing) }
            is ProfileEvent.SetAppearance -> persist { setAppearance(event.appearance) }
            is ProfileEvent.SetDictionary -> setDictionary(event.pack)
            is ProfileEvent.SetDefaultDirection -> {
                sheet.value = null
                persist { setDefaultDirectionId(event.direction.id) }
            }
            is ProfileEvent.SetLanguage -> setLanguage(event.language)
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

    private fun setDictionary(pack: DictionaryPack) {
        // Close the sheet first: the switch shows a full-screen overlay, and the sheet is a window above it.
        sheet.value = null
        // The switch runs on the application scope, so leaving this screen cannot cancel it.
        if (pack != uiState.value?.dictionary) switcher.switchTo(pack)
    }

    private fun setLanguage(language: AppLanguage) {
        // Close the sheet first: switching the locale recreates the activity, and this state outlives it.
        sheet.value = null
        viewModelScope.launch {
            val shown = appLanguage.value
            guarded(onFailure = { appLanguage.value = shown }) {
                // Choosing the language in use again would only recreate the activity.
                if (language == (shown ?: prefs.snapshot().appLanguage)) return@guarded
                appLanguage.value = language
                prefs.setAppLanguage(language)
                localeApplier.setLanguage(language)
            }
        }
    }

    private fun openLink(link: ProfileLink) {
        if (link !in linkTargets.visible) return
        when (link) {
            ProfileLink.ABOUT -> send(ProfileEffect.NavigateAbout)
            ProfileLink.RATE -> send(ProfileEffect.OpenUrl(linkTargets.playStore))
            ProfileLink.SHARE -> uiState.value?.let { send(ProfileEffect.ShareApp(linkTargets.playStore, it.dictionary)) }
            ProfileLink.MORE_APPS -> sheet.value = ProfileSheet.MORE_APPS
            ProfileLink.SUPPORT -> send(ProfileEffect.OpenUrl(linkTargets.support))
            ProfileLink.PRIVACY -> send(ProfileEffect.OpenUrl(linkTargets.privacy))
        }
    }

    private fun state(
        prefs: UserPrefs,
        capabilities: DictionaryCapabilities,
        language: AppLanguage,
        sheet: ProfileSheet?,
    ): ProfileUiState {
        val visible = capabilities.directions.filter { it.entryCount > 0 }.sortedBy(Direction::position)
        return ProfileUiState(
            account = null,
            textSize = prefs.textSize,
            lineSpacing = prefs.lineSpacing,
            appearance = prefs.appearance,
            dictionary = capabilities.pack,
            defaultDirection = defaultDirection(visible, prefs.defaultDirectionId),
            directions = visible,
            language = language,
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
        viewModelScope.launch { guarded { prefs.block() } }
    }

    /**
     * Runs [block]; a failure is recorded, [onFailure] undoes what the screen already shows, and a generic error
     * toast says the change was not saved. Cancellation still propagates.
     */
    private suspend fun guarded(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.record(t)
            onFailure()
            effectChannel.send(ProfileEffect.Toast(UiText(R.string.entry_action_failed)))
        }
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
