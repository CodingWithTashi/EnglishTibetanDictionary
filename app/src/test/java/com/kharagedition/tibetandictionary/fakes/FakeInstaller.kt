package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [DictionaryInstaller] whose [ensureInstalled] moves [state] to what [outcome] returns for the pack; tests may also
 * set [state] directly. When [databases] is given, a [InstallState.Ready] outcome also activates the pack there, as
 * the real installer does.
 */
class FakeInstaller(
    var outcome: (DictionaryPack) -> InstallState = { InstallState.Ready(it, SampleData.capabilities.totalEntries) },
    private val databases: DictionaryDatabases? = null,
) : DictionaryInstaller {
    override val state = MutableStateFlow<InstallState>(InstallState.Checking)

    /** Optional hook run inside [ensureInstalled] before [outcome] is applied; tests suspend here to hold an install. */
    var beforeOutcome: (suspend (DictionaryPack) -> Unit)? = null

    private val calls = mutableListOf<DictionaryPack>()

    /** The packs [ensureInstalled] was called with, in order. */
    val ensureInstalledCalls: List<DictionaryPack> get() = calls.toList()

    override suspend fun ensureInstalled(pack: DictionaryPack) {
        calls += pack
        beforeOutcome?.invoke(pack)
        val result = outcome(pack)
        if (result is InstallState.Ready) databases?.activate(result.pack)
        state.value = result
    }
}
