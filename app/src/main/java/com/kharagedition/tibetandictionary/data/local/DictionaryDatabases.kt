package com.kharagedition.tibetandictionary.data.local

import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One Room instance of [DictionaryDatabase] per [DictionaryPack], each over its own file, opened lazily by [open].
 *
 * Every opened instance is kept for the life of the process: Room 2.8 forbids reopening a closed instance, so a pack
 * that was switched away from stays open and a switch back reuses it. Only the installer calls [get] for a pack that
 * is not [active] (to verify a freshly copied file); everything else reads [activeDb] or observes [active].
 *
 * [active] is null until [activate] is called after the first successful install. Callers that run before that (a
 * repository built by Hilt, a flow collected early) must not read [activeDb] when they are constructed.
 */
class DictionaryDatabases(private val open: (DictionaryPack) -> DictionaryDatabase) {

    private val opened = mutableMapOf<DictionaryPack, DictionaryDatabase>()
    private val _active = MutableStateFlow<DictionaryPack?>(null)

    /** The pack the app reads from, or null until [activate]. */
    val active: StateFlow<DictionaryPack?> = _active.asStateFlow()

    /** The database of the [active] pack; throws [IllegalStateException] before [activate]. */
    val activeDb: DictionaryDatabase get() = get(checkNotNull(active.value) { "No active dictionary" })

    /** The pack's database, opened on first use and then returned as the same instance. */
    @Synchronized
    fun get(pack: DictionaryPack): DictionaryDatabase = opened.getOrPut(pack) { open(pack) }

    /** Makes [pack] the active one; collectors of [active] see it. */
    fun activate(pack: DictionaryPack) {
        _active.value = pack
    }

    /** Whether [get] has already opened [pack]. */
    @Synchronized
    fun isOpen(pack: DictionaryPack): Boolean = pack in opened
}
