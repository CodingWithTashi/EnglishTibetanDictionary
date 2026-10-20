package com.kharagedition.tibetandictionary.data.install

import kotlinx.coroutines.flow.StateFlow

/** Copies (or upgrades) the bundled dictionary database from assets before first use. */
interface DictionaryInstaller {
    val state: StateFlow<InstallState>

    /** Installs the asset if the on-device copy is missing or outdated; safe to call again after [InstallState.Failed]. */
    suspend fun ensureInstalled()
}

sealed interface InstallState {
    data object Checking : InstallState

    /** [progress] runs from 0 to 1. */
    data class Copying(val progress: Float) : InstallState

    data class Ready(val totalEntries: Int) : InstallState

    data class Failed(val cause: Throwable) : InstallState
}
