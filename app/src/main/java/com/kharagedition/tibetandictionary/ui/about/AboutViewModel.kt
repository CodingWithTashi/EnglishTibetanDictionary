package com.kharagedition.tibetandictionary.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Reads what About shows from the installed dictionary; [uiState] is null until its capabilities load. */
@HiltViewModel
class AboutViewModel @Inject constructor(dictionary: DictionaryRepository) : ViewModel() {

    val uiState: StateFlow<AboutUiState?> = dictionary.capabilities
        .map { capabilities ->
            AboutUiState(
                versionName = BuildConfig.VERSION_NAME,
                entryCount = capabilities.totalEntries.takeIf { it > 0 },
                attribution = capabilities.attribution.text.takeIf { it.isNotBlank() },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
