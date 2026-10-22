package com.kharagedition.tibetandictionary.data.install

import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a user-requested dictionary switch stands. */
sealed interface SwitchState {
    /** No switch is running and none is waiting to be acknowledged. */
    data object Idle : SwitchState

    /** [pack] is being installed and activated; [progress] (0 to 1) is null until the installer reports a copy. */
    data class InProgress(val pack: DictionaryPack, val progress: Float?) : SwitchState

    /**
     * Switching to [pack] failed; the previous dictionary is still active and the stored choice is unchanged (if the
     * choice could not be saved, the previous pack is re-activated).
     * Leave it with [DictionarySwitcher.retry] or [DictionarySwitcher.dismiss].
     */
    data class Failed(val pack: DictionaryPack, val cause: Throwable) : SwitchState
}

/**
 * Switches the app between dictionary packs. The switch runs on the application scope, so leaving a screen cannot
 * cancel it, and only one switch runs at a time.
 *
 * The stored pack choice is written only after the installer has activated the new pack, so process death in the
 * middle of a switch lands on the old pack at the next start.
 *
 * If the job is cancelled (for example the application scope goes away) the state returns to [SwitchState.Idle], not
 * [SwitchState.Failed]: nothing went wrong, nobody is left to show an error to, and the stored choice is unchanged.
 * If it was cancelled after the installer activated the new pack, the previous pack is re-activated first.
 */
interface DictionarySwitcher {
    /** [SwitchState.Idle] at rest, [SwitchState.InProgress] while a switch runs, [SwitchState.Failed] after one fails. */
    val state: StateFlow<SwitchState>

    /**
     * Installs, activates, then persists [pack]; the returned job completes when the switch ended (success or
     * [SwitchState.Failed]). A request for the pack already being switched to returns the running job; a request
     * for another pack while a switch runs is rejected: it returns an already-completed job and changes nothing.
     *
     * Success, rejection and cancellation all end in [SwitchState.Idle], so after `join()` check that the active
     * pack is [pack]. The returned job is the switch itself: cancelling it aborts the switch.
     *
     * Only one switch runs at a time because the check and the launch share a lock; that atomicity is not covered by
     * a multithreaded test.
     */
    fun switchTo(pack: DictionaryPack): Job

    /** Runs [switchTo] again for the pack in [SwitchState.Failed]; no-op in any other state. */
    fun retry()

    /** Turns [SwitchState.Failed] into [SwitchState.Idle] ("keep the current dictionary"); no-op otherwise. */
    fun dismiss()
}

@Singleton
class DictionarySwitcherImpl @Inject constructor(
    private val installer: DictionaryInstaller,
    private val databases: DictionaryDatabases,
    private val prefs: UserPreferencesRepository,
    private val errors: ErrorReporter,
    @ApplicationScope private val scope: CoroutineScope,
) : DictionarySwitcher {

    private val _state = MutableStateFlow<SwitchState>(SwitchState.Idle)
    override val state: StateFlow<SwitchState> = _state.asStateFlow()

    private val lock = Any()
    private var running: Pair<DictionaryPack, Job>? = null // guarded by lock

    override fun switchTo(pack: DictionaryPack): Job = synchronized(lock) {
        running?.let { (runningPack, job) ->
            return if (runningPack == pack) job else Job().apply { complete() }
        }
        val previous = databases.active.value
        val alreadyActive = previous == pack
        val job = scope.launch(start = CoroutineStart.LAZY) {
            if (alreadyActive) persistOnly(pack) else run(pack, previous)
        }
        running = pack to job
        if (!alreadyActive) _state.value = SwitchState.InProgress(pack, null)
        job.invokeOnCompletion {
            synchronized(lock) {
                // Cancelled (the scope went away or the job was cancelled): nothing failed and nothing changed, so
                // go back to Idle rather than stay InProgress with nobody left to finish the switch.
                // Only if this job still owns the slot: after finish() a retry may already run its own job.
                if (running?.second === job) {
                    running = null
                    if (_state.value is SwitchState.InProgress) _state.value = SwitchState.Idle
                }
            }
        }
        job.start()
        job
    }

    override fun retry() {
        (_state.value as? SwitchState.Failed)?.let { switchTo(it.pack) }
    }

    override fun dismiss() {
        _state.update { if (it is SwitchState.Failed) SwitchState.Idle else it }
    }

    private suspend fun persistOnly(pack: DictionaryPack) {
        try {
            prefs.setDictionaryPack(pack)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.record(e)
            finish(SwitchState.Failed(pack, e))
        }
    }

    private suspend fun run(pack: DictionaryPack, previous: DictionaryPack?) {
        val outcome = try {
            coroutineScope {
                val mirror = launch {
                    installer.state.collect { s ->
                        if (s is InstallState.Copying && s.pack == pack) {
                            _state.value = SwitchState.InProgress(pack, s.progress)
                        }
                    }
                }
                try {
                    installer.ensureInstalled(pack)
                } finally {
                    mirror.cancel()
                }
            }
            currentCoroutineContext().ensureActive()
            when (val result = installer.state.value) {
                is InstallState.Ready ->
                    if (result.pack == pack && databases.active.value == pack) {
                        persistChoice(pack, previous)
                    } else {
                        SwitchState.Failed(pack, IllegalStateException("$pack was not activated: $result"))
                    }
                is InstallState.Failed ->
                    if (result.pack == pack) SwitchState.Failed(pack, result.cause)
                    else SwitchState.Failed(pack, IllegalStateException("Unexpected install result: $result"))
                else -> SwitchState.Failed(pack, IllegalStateException("Install of $pack ended in $result"))
            }
        } catch (e: CancellationException) {
            restoreIfActivated(pack, previous)
            throw e // invokeOnCompletion resets InProgress to Idle
        } catch (e: Exception) {
            errors.record(e)
            restoreIfActivated(pack, previous)
            SwitchState.Failed(pack, e)
        }
        finish(outcome)
    }

    /**
     * Saves [pack] as the stored choice. If that fails the installer has already activated [pack], so the previous
     * pack is re-activated to keep the active pack and the stored choice equal (also on cancellation).
     */
    private suspend fun persistChoice(pack: DictionaryPack, previous: DictionaryPack?): SwitchState {
        try {
            prefs.setDictionaryPack(pack)
            return SwitchState.Idle
        } catch (e: CancellationException) {
            restore(previous)
            throw e
        } catch (e: Exception) {
            errors.record(e)
            restore(previous)
            return SwitchState.Failed(pack, e)
        }
    }

    /** Undoes an activation of [pack] that a cancellation or failure left without a stored choice. */
    private fun restoreIfActivated(pack: DictionaryPack, previous: DictionaryPack?) {
        if (databases.active.value == pack) restore(previous)
    }

    /**
     * Re-activates [previous]. Normally only the installer activates, but [previous] was verified when it was
     * installed and its database is still open, so this is safe. Without a previous pack, [DictionaryDatabases.active]
     * stays as it is.
     */
    private fun restore(previous: DictionaryPack?) {
        if (previous != null) databases.activate(previous)
    }

    /** Publishes the final state and frees the switch slot together, so [retry] right after a failure is accepted. */
    private fun finish(outcome: SwitchState) = synchronized(lock) {
        running = null
        _state.value = outcome
    }
}
