package com.kharagedition.tibetandictionary.ui.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.AppLocale
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppRootState {
    /** Before the installer has started copying: the system splash stays up. */
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

    data class Ready(
        val onboarded: Boolean,
        val appearance: Appearance,
        val textSize: AppTextSize,
        val lineSpacing: Float,
        val language: AppLanguage,
    ) : AppRootState
}

/** What the activity shows at the root: the splash, the installing screen, or the app with the user's settings. */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val installer: DictionaryInstaller,
    prefsRepository: UserPreferencesRepository,
    private val errors: ErrorReporter,
) : ViewModel() {

    val uiState: StateFlow<AppRootState> =
        combine(installer.state, prefsRepository.prefs) { install, prefs ->
            when (install) {
                InstallState.Checking -> AppRootState.Loading
                is InstallState.Copying -> AppRootState.Installing(progress = install.progress, totalEntries = null)
                is InstallState.Failed -> AppRootState.Installing(progress = null, totalEntries = null, failure = install.cause)
                is InstallState.Ready -> prefs.toReady()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppRootState.Loading)

    /** "Try again" on the install-failed screen. */
    fun retryInstall() {
        viewModelScope.launch {
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
        // A per-app locale chosen in system settings wins until AppInitializer copies it into prefs.
        language = if (AppCompatDelegate.getApplicationLocales().isEmpty) appLanguage else AppLocale.current(),
    )
}
