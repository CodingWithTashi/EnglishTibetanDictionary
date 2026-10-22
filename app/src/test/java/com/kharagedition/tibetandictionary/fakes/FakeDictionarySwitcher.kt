package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * [DictionarySwitcher] that records [requests] and completes at once, after calling [onSwitch] (tests wire it to the
 * fake preferences). Tests drive [state] directly.
 */
class FakeDictionarySwitcher(var onSwitch: (DictionaryPack) -> Unit = {}) : DictionarySwitcher {
    override val state = MutableStateFlow<SwitchState>(SwitchState.Idle)

    private val calls = mutableListOf<DictionaryPack>()

    /** The packs [switchTo] was called with, in order. */
    val requests: List<DictionaryPack> get() = calls.toList()

    override fun switchTo(pack: DictionaryPack): Job {
        calls += pack
        onSwitch(pack)
        return Job().apply { complete() }
    }

    override fun retry() {
        (state.value as? SwitchState.Failed)?.let { switchTo(it.pack) }
    }

    override fun dismiss() {
        state.update { if (it is SwitchState.Failed) SwitchState.Idle else it }
    }
}
