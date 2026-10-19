package com.kharagedition.tibetandictionary.data.install

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/** Stub so the Hilt graph compiles; L1 (T1.8) replaces the bodies. */
@Singleton
class DictionaryInstallerImpl @Inject constructor() : DictionaryInstaller {
    override val state: StateFlow<InstallState> get() = TODO("L1")

    override suspend fun ensureInstalled(): Unit = TODO("L1")
}
