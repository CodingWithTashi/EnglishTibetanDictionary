package com.kharagedition.tibetandictionary.data.install

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stub so the Hilt graph compiles and the app shell runs; L1 (T1.8) replaces the bodies. Until then every install
 * fails, so start-up ends on the install-failed screen.
 */
@Singleton
class DictionaryInstallerImpl @Inject constructor() : DictionaryInstaller {
    private val _state = MutableStateFlow<InstallState>(InstallState.Checking)
    override val state: StateFlow<InstallState> = _state.asStateFlow()

    override suspend fun ensureInstalled() {
        _state.value = InstallState.Failed(NotImplementedError("L1"))
    }
}
