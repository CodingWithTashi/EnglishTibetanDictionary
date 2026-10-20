package com.kharagedition.tibetandictionary.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.init.AppInitializer
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
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
     * The installing screen: [progress] 0..1 (null = indeterminate) and [totalEntries] when known. A non-null
     * [failure] shows the retry state instead of the progress bar.
     */
    data class Installing(
        val progress: Float?,
        val totalEntries: Int?,
        val failure: Throwable? = null,
    ) : AppRootState

    /** The app, with the user's display settings. The UI language follows the configuration, not prefs. */
    data class Ready(
        val onboarded: Boolean,
        val appearance: Appearance,
        val textSize: AppTextSize,
        val lineSpacing: Float,
    ) : AppRootState
}

/**
 * What the activity shows at the root: the splash, the installing screen, or the app with the user's settings.
 * Start-up that finishes ([AppInitializer.ready]) while the installer still reports [InstallState.Checking] means
 * the installer threw before reporting anything; that shows the retry screen instead of keeping the splash up. A
 * retry runs in the process-wide [appScope], like the first install, so leaving the screen does not cut it off
 * (which would show the "interrupted" screen on the next open).
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val installer: DictionaryInstaller,
    initializer: AppInitializer,
    prefsRepository: UserPreferencesRepository,
    private val errors: ErrorReporter,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val installerDidNotStart = IllegalStateException("installer did not start")

    val uiState: StateFlow<AppRootState> =
        combine(installer.state, initializer.ready, prefsRepository.prefs) { install, startupDone, prefs ->
            when (install) {
                InstallState.Checking ->
                    if (startupDone) {
                        AppRootState.Installing(progress = null, totalEntries = null, failure = installerDidNotStart)
                    } else {
                        AppRootState.Loading
                    }
                is InstallState.Copying -> AppRootState.Installing(progress = install.progress, totalEntries = null)
                is InstallState.Failed -> AppRootState.Installing(progress = null, totalEntries = null, failure = install.cause)
                is InstallState.Ready -> prefs.toReady()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppRootState.Loading)

    /** "Try again" on the install-failed screen. */
    fun retryInstall() {
        appScope.launch {
            try {
                installer.ensureInstalled()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // The installer reports failures through its state; a throw is a bug, so keep the app alive.
                errors.record(t)
            }
        }
    }

    private fun UserPrefs.toReady(): AppRootState.Ready = AppRootState.Ready(
        onboarded = onboarded,
        appearance = appearance,
        textSize = textSize,
        lineSpacing = lineSpacing,
    )
}
