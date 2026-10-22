package com.kharagedition.tibetandictionary.core.init

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInitializerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val prefs = FakeUserPreferencesRepository()
    private val errors = RecordingErrorReporter()

    /** Records whether the legacy cleanup had finished when [ensureInstalled] ran; throws [failure] if set. */
    private class ProbeInstaller(
        private val prefs: FakeUserPreferencesRepository,
        private val failure: Throwable? = null,
    ) : DictionaryInstaller {
        override val state = MutableStateFlow<InstallState>(InstallState.Checking)
        val cleanupDoneAtInstall = mutableListOf<Boolean>()
        val installedPacks = mutableListOf<DictionaryPack>()

        override suspend fun ensureInstalled(pack: DictionaryPack) {
            installedPacks += pack
            cleanupDoneAtInstall += prefs.prefs.value.legacyCleanupDone
            failure?.let { throw it }
            state.value = InstallState.Ready(pack, totalEntries = 1)
        }
    }

    private fun initializer(installer: DictionaryInstaller) =
        AppInitializer(LegacyCleanup(context, prefs), installer, prefs, errors, scope, dispatcher)

    @Test
    fun `cleans up, then installs, then reports ready`() {
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)
        assertThat(init.ready.value).isFalse()

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(installer.cleanupDoneAtInstall).containsExactly(true)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, totalEntries = 1))
        assertThat(init.ready.value).isTrue()
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `an installer that throws is reported and does not stop start-up`() {
        val bug = NotImplementedError("L1")
        val init = initializer(ProbeInstaller(prefs, failure = bug))

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(init.ready.value).isTrue()
        assertThat(errors.recorded).containsExactly(bug)
    }

    @Test
    fun `starting twice runs the steps once`() {
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)

        init.start()
        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(installer.cleanupDoneAtInstall).hasSize(1)
    }

    @Test
    fun `start installs the stored pack`() {
        prefs.prefs.value = prefs.prefs.value.copy(dictionaryPack = DictionaryPack.DZONGKHA)
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(installer.installedPacks).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `an onboarded user without a stored pack is kept on Tibetan even when the default is Dzongkha`() {
        prefs.prefs.value = UserPrefs(onboarded = true, dictionaryPack = DictionaryPack.DZONGKHA)
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(prefs.prefs.value.dictionaryPackChosen).isTrue()
        assertThat(installer.installedPacks).containsExactly(DictionaryPack.TIBETAN)
    }

    @Test
    fun `a user who is not onboarded gets no pack stored`() {
        prefs.prefs.value = UserPrefs(onboarded = false, dictionaryPack = DictionaryPack.DZONGKHA)
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(prefs.prefs.value.dictionaryPackChosen).isFalse()
        assertThat(installer.installedPacks).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `a stored pack choice is left untouched`() {
        prefs.prefs.value =
            UserPrefs(onboarded = true, dictionaryPack = DictionaryPack.DZONGKHA, dictionaryPackChosen = true)
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(installer.installedPacks).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `a failed write of the migrated pack is reported and start-up continues with the resolved pack`() {
        val bug = IllegalStateException("disk full")
        prefs.prefs.value = UserPrefs(onboarded = true, dictionaryPack = DictionaryPack.DZONGKHA)
        prefs.beforeSetDictionaryPack = { throw bug }
        val installer = ProbeInstaller(prefs)
        val init = initializer(installer)

        init.start()
        scope.testScheduler.advanceUntilIdle()

        assertThat(errors.recorded).containsExactly(bug)
        assertThat(installer.installedPacks).containsExactly(DictionaryPack.DZONGKHA)
        assertThat(init.ready.value).isTrue()
    }
}
