package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import kotlinx.coroutines.flow.MutableStateFlow

/** [DictionaryInstaller] whose [ensureInstalled] moves [state] to [outcome]; tests may also set [state] directly. */
class FakeInstaller(
    var outcome: InstallState = InstallState.Ready(SampleData.capabilities.totalEntries),
) : DictionaryInstaller {
    override val state = MutableStateFlow<InstallState>(InstallState.Checking)

    var ensureInstalledCalls = 0
        private set

    override suspend fun ensureInstalled() {
        ensureInstalledCalls++
        state.value = outcome
    }
}
