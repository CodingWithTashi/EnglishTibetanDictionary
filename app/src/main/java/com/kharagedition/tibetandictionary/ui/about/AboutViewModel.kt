package com.kharagedition.tibetandictionary.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

/** Reads what About shows from the active dictionary; [uiState] is null until a pack is active and its capabilities load. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    dictionary: DictionaryRepository,
    databases: DictionaryDatabases,
) : ViewModel() {

    val uiState: StateFlow<AboutUiState?> = combine(dictionary.capabilities, databases.active.filterNotNull()) { capabilities, pack ->
        AboutUiState(
            versionName = BuildConfig.VERSION_NAME,
            entryCount = capabilities.totalEntries.takeIf { it > 0 },
            pack = pack,
            sourceName = capabilities.attribution.sourceName,
            author = capabilities.attribution.author,
            licence = capabilities.attribution.license,
            attribution = capabilities.attribution.text.takeIf { it.isNotBlank() },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
