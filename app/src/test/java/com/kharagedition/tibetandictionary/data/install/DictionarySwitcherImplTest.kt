package com.kharagedition.tibetandictionary.data.install

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeInstaller
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DictionarySwitcherImplTest {
    private val dbs = mutableListOf<DictionaryDatabase>()
    private val databases = DictionaryDatabases { inMemoryDictionaryDb().also(dbs::add) }
    private val prefs = FakeUserPreferencesRepository(UserPrefs(dictionaryPack = DictionaryPack.TIBETAN))
    private val errors = RecordingErrorReporter()

    @After
    fun tearDown() = dbs.forEach { it.close() }

    private fun TestScope.switcher(installer: FakeInstaller) =
        DictionarySwitcherImpl(installer, databases, prefs, errors, backgroundScope)

    private fun installer(
        outcome: (DictionaryPack) -> InstallState = { InstallState.Ready(it, 10) },
    ) = FakeInstaller(outcome, databases).also { databases.activate(DictionaryPack.TIBETAN) }

    @Test
    fun alreadyActivePackOnlyPersistsPref() = runTest {
        val installer = installer()
        val switcher = switcher(installer)
        switcher.switchTo(DictionaryPack.TIBETAN).join()

        assertThat(installer.ensureInstalledCalls).isEmpty()
        assertThat(prefs.prefs.value.dictionaryPackChosen).isTrue()
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
    }

    @Test
    fun switchInstallsActivatesThenPersists() = runTest {
        val installer = installer()
        val switcher = switcher(installer)
        switcher.state.test {
            assertThat(awaitItem()).isEqualTo(SwitchState.Idle)
            switcher.switchTo(DictionaryPack.DZONGKHA)
            assertThat(awaitItem()).isEqualTo(SwitchState.InProgress(DictionaryPack.DZONGKHA, null))
            assertThat(awaitItem()).isEqualTo(SwitchState.Idle)
        }
        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.DZONGKHA)
        assertThat(databases.active.value).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(prefs.prefs.value.dictionaryPackChosen).isTrue()
    }

    @Test
    fun prefIsNotWrittenBeforeTheInstallEnds() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = installer().apply { beforeOutcome = { gate.await() } }
        val switcher = switcher(installer)
        val job = switcher.switchTo(DictionaryPack.DZONGKHA)
        runCurrent()
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        gate.complete(Unit)
        job.join()
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun progressMirrorsInstaller() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = installer()
        installer.beforeOutcome = { pack ->
            installer.state.value = InstallState.Copying(InstallStatePacks.other(pack), 0.9f)
            installer.state.value = InstallState.Copying(pack, 0.5f)
            gate.await()
        }
        val switcher = switcher(installer)
        val job = switcher.switchTo(DictionaryPack.DZONGKHA)
        runCurrent()
        assertThat(switcher.state.value).isEqualTo(SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.5f))

        // another pack's progress is ignored
        installer.state.value = InstallState.Copying(DictionaryPack.TIBETAN, 0.9f)
        runCurrent()
        assertThat(switcher.state.value).isEqualTo(SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.5f))

        gate.complete(Unit)
        job.join()
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
        // no leaked collector: progress after the switch ended changes nothing
        installer.state.value = InstallState.Copying(DictionaryPack.DZONGKHA, 0.7f)
        runCurrent()
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
    }

    @Test
    fun failedSwitchLeavesPrefAndActivePack() = runTest {
        val boom = RuntimeException("disk full")
        val switcher = switcher(installer { InstallState.Failed(it, boom) })
        switcher.switchTo(DictionaryPack.DZONGKHA).join()

        assertThat(switcher.state.value).isEqualTo(SwitchState.Failed(DictionaryPack.DZONGKHA, boom))
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(prefs.prefs.value.dictionaryPackChosen).isFalse()
        assertThat(databases.active.value).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun thrownInstallIsRecordedAndFails() = runTest {
        val boom = IllegalStateException("boom")
        val switcher = switcher(installer { throw boom })
        switcher.switchTo(DictionaryPack.DZONGKHA).join()

        // coroutine stack-trace recovery may hand back a copy of the exception, so compare type and message
        val failed = switcher.state.value as SwitchState.Failed
        assertThat(failed.pack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(failed.cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(failed.cause).hasMessageThat().isEqualTo("boom")
        assertThat(errors.recorded.map { it.message }).containsExactly("boom")
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun cancellationEndsIdleNotStuck() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = installer().apply { beforeOutcome = { gate.await() } }
        val switcher = switcher(installer)
        val job = switcher.switchTo(DictionaryPack.DZONGKHA)
        runCurrent()
        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.DZONGKHA)
        job.cancel()
        job.join()

        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
        assertThat(errors.recorded).isEmpty()
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun unsavableChoiceRestoresThePreviousPack() = runTest {
        val boom = IllegalStateException("datastore")
        prefs.beforeSetDictionaryPack = { throw boom }
        val switcher = switcher(installer())
        switcher.switchTo(DictionaryPack.DZONGKHA).join()

        val failed = switcher.state.value as SwitchState.Failed
        assertThat(failed.pack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(failed.cause).hasMessageThat().isEqualTo("datastore")
        assertThat(databases.active.value).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(errors.recorded.map { it.message }).containsExactly("datastore")
    }

    @Test
    fun cancellationDuringTheSaveRestoresThePreviousPack() = runTest {
        val gate = CompletableDeferred<Unit>()
        prefs.beforeSetDictionaryPack = { gate.await() }
        val switcher = switcher(installer())
        val job = switcher.switchTo(DictionaryPack.DZONGKHA)
        runCurrent()
        assertThat(databases.active.value).isEqualTo(DictionaryPack.DZONGKHA)
        job.cancel()
        job.join()

        assertThat(databases.active.value).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun cancellationAfterActivationBeforeTheSaveRestoresThePreviousPack() = runTest {
        lateinit var job: Job
        val installer = installer().apply { afterOutcome = { job.cancel() } }
        val switcher = switcher(installer)
        job = switcher.switchTo(DictionaryPack.DZONGKHA)
        job.join()

        assertThat(databases.active.value).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun retryRunsTheFailedPackAgain() = runTest {
        var fail = true
        val installer = installer { if (fail) InstallState.Failed(it, RuntimeException("x")) else InstallState.Ready(it, 1) }
        val switcher = switcher(installer)
        switcher.switchTo(DictionaryPack.DZONGKHA).join()
        assertThat(switcher.state.value).isInstanceOf(SwitchState.Failed::class.java)

        fail = false
        switcher.retry()
        runCurrent()

        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.DZONGKHA, DictionaryPack.DZONGKHA)
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun retryAndDismissAreNoOpsOutsideFailed() = runTest {
        val installer = installer()
        val switcher = switcher(installer)
        switcher.retry()
        switcher.dismiss()
        runCurrent()
        assertThat(installer.ensureInstalledCalls).isEmpty()
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
    }

    @Test
    fun dismissReturnsToIdle() = runTest {
        val switcher = switcher(installer { InstallState.Failed(it, RuntimeException("x")) })
        switcher.switchTo(DictionaryPack.DZONGKHA).join()
        switcher.dismiss()

        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun secondRequestWhileRunningIsRejected() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = installer().apply { beforeOutcome = { gate.await() } }
        val switcher = switcher(installer)
        val first = switcher.switchTo(DictionaryPack.DZONGKHA)
        runCurrent()

        val rejected = switcher.switchTo(DictionaryPack.TIBETAN)
        assertThat(rejected.isCompleted).isTrue()
        assertThat(switcher.state.value).isEqualTo(SwitchState.InProgress(DictionaryPack.DZONGKHA, null))

        gate.complete(Unit)
        first.join()
        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun sameRequestWhileRunningReturnsSameJob() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = installer().apply { beforeOutcome = { gate.await() } }
        val switcher = switcher(installer)
        val first = switcher.switchTo(DictionaryPack.DZONGKHA)
        runCurrent()

        assertThat(switcher.switchTo(DictionaryPack.DZONGKHA)).isSameInstanceAs(first)
        gate.complete(Unit)
        first.join()
        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.DZONGKHA)
    }

    private object InstallStatePacks {
        fun other(pack: DictionaryPack) = DictionaryPack.entries.first { it != pack }
    }
}
