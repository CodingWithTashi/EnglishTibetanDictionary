package com.kharagedition.tibetandictionary.data.install

import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import kotlinx.coroutines.flow.StateFlow

/** Copies (or upgrades) a bundled dictionary pack database from assets before first use. */
interface DictionaryInstaller {
    val state: StateFlow<InstallState>

    /**
     * Installs [pack]'s asset if the on-device copy is missing or outdated and activates the pack; safe to call
     * again after [InstallState.Failed]. A failure never touches another pack's installed database.
     */
    suspend fun ensureInstalled(pack: DictionaryPack)
}

sealed interface InstallState {
    data object Checking : InstallState

    /** [progress] runs from 0 to 1. */
    data class Copying(val pack: DictionaryPack, val progress: Float) : InstallState

    data class Ready(val pack: DictionaryPack, val totalEntries: Int) : InstallState

    data class Failed(val pack: DictionaryPack, val cause: Throwable) : InstallState
}
