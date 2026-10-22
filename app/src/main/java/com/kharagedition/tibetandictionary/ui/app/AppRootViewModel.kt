package com.kharagedition.tibetandictionary.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.init.AppInitializer
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppRootState {
    /** Start-up is still running and the installer has not started copying: the system splash stays up. */
    data object Loading : AppRootState

    /**
     * The first-install screen for [pack]: [progress] 0..1 (null = indeterminate) and [totalEntries] when known. A
     * non-null [failure] shows the retry state instead of the progress bar.
     */
    data class Installing(
        val pack: DictionaryPack,
        val progress: Float?,
        val totalEntries: Int?,
        val failure: Throwable? = null,
    ) : AppRootState

    /**
     * The app on the [pack] that is active, with the user's display settings (the UI language follows the
     * configuration, not prefs). [switching] is the state of a user-requested switch to another pack; the app stays
     * mounted behind it.
     */
    data class Ready(
        val pack: DictionaryPack,
        val onboarded: Boolean,
        val appearance: Appearance,
        val textSize: AppTextSize,
        val lineSpacing: Float,
        val switching: SwitchState = SwitchState.Idle,
    ) : AppRootState
}

/**
 * What the activity shows at the root: the splash, the installing screen, or the app with the user's settings.
 * The gate keys on [DictionaryDatabases.active]: while it is null (first install) the installer's state decides;
 * once a pack is active the app is shown whatever the installer says (after a failed switch it stays `Failed` for
 * the pack that was not activated) and [DictionarySwitcher.state] drives the switching overlay.
 * Start-up that finishes ([AppInitializer.ready]) while the installer still reports [InstallState.Checking] means
 * the installer threw before reporting anything; that shows the retry screen instead of keeping the splash up. A
 * retry runs in the process-wide [appScope], like the first install, so leaving the screen does not cut it off
 * (which would show the "interrupted" screen on the next open).
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val installer: DictionaryInstaller,
    initializer: AppInitializer,
    private val prefsRepository: UserPreferencesRepository,
    databases: DictionaryDatabases,
    private val switcher: DictionarySwitcher,
    private val errors: ErrorReporter,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val installerDidNotStart = IllegalStateException("installer did not start")

    val uiState: StateFlow<AppRootState> =
        combine(
            installer.state,
            initializer.ready,
            prefsRepository.prefs,
            databases.active,
            switcher.state,
        ) { install, startupDone, prefs, active, switching ->
            if (active != null) return@combine prefs.toReady(active, switching)
            val pack = prefs.dictionaryPack
            when (install) {
                InstallState.Checking ->
                    if (startupDone) {
                        AppRootState.Installing(pack, null, null, failure = installerDidNotStart)
                    } else {
                        AppRootState.Loading
                    }
                is InstallState.Copying ->
                    AppRootState.Installing(install.pack, progress = install.progress, totalEntries = null)
                is InstallState.Failed ->
                    AppRootState.Installing(install.pack, progress = null, totalEntries = null, failure = install.cause)
                // Ready is reported just before the pack is activated; the next emission is the app.
                is InstallState.Ready -> AppRootState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppRootState.Loading)

    /** "Try again" on the install-failed screen. */
    fun retryInstall() {
        appScope.launch {
            try {
                installer.ensureInstalled(prefsRepository.snapshot().dictionaryPack)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // The installer reports failures through its state; a throw is a bug, so keep the app alive.
                errors.record(t)
            }
        }
    }

    /** "Try again" on the switching overlay's failure. */
    fun retrySwitch() = switcher.retry()

    /** "Keep current dictionary" on the switching overlay's failure. */
    fun keepCurrentDictionary() = switcher.dismiss()

    private fun UserPrefs.toReady(pack: DictionaryPack, switching: SwitchState) = AppRootState.Ready(
        pack = pack,
        onboarded = onboarded,
        appearance = appearance,
        textSize = textSize,
        lineSpacing = lineSpacing,
        switching = switching,
    )
}
