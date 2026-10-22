package com.kharagedition.tibetandictionary.ui.app

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.core.init.AppInitializer
import com.kharagedition.tibetandictionary.core.init.LegacyCleanup
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.local.databasesOf
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeDictionarySwitcher
import com.kharagedition.tibetandictionary.fakes.FakeInstaller
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Robolectric so the real [AppInitializer] (legacy cleanup, then install, then ready) drives start-up. */
@RunWith(AndroidJUnit4::class)
class AppRootViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val installer = FakeInstaller()
    private val prefs = FakeUserPreferencesRepository(
        UserPrefs(onboarded = true, appearance = Appearance.DARK, textSize = AppTextSize.LARGE, lineSpacing = 2.1f),
    )
    private val errors = RecordingErrorReporter()
    private val databases = databasesOf()
    private val switcher = FakeDictionarySwitcher()
    private val startup = StandardTestDispatcher()

    /** An installer whose [ensureInstalled] throws [error] without ever leaving [initial]. */
    private class ThrowingInstaller(initial: InstallState, private val error: Throwable) : DictionaryInstaller {
        override val state = MutableStateFlow(initial)
        override suspend fun ensureInstalled(pack: DictionaryPack) = throw error
    }

    private fun initializer(installer: DictionaryInstaller) =
        AppInitializer(LegacyCleanup(context, prefs), installer, prefs, errors, TestScope(startup), startup)

    /** The process-wide scope retries run in; it outlives every view model. */
    private val appScope = CoroutineScope(SupervisorJob() + main.dispatcher)

    private fun viewModel(
        installer: DictionaryInstaller = this.installer,
        initializer: AppInitializer = initializer(installer),
    ) = AppRootViewModel(installer, initializer, prefs, databases, switcher, errors, appScope)

    /** Runs start-up (cleanup, install, ready) to the end. */
    private fun AppInitializer.runStartup() {
        start()
        startup.scheduler.advanceUntilIdle()
    }

    @Test
    fun `Loading while start-up runs and the installer is checking`() = runTest {
        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Loading)
        }
    }

    @Test
    fun `Installing with the progress while the asset is copied`() = runTest {
        installer.state.value = InstallState.Copying(DictionaryPack.TIBETAN, 0.4f)

        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Installing(DictionaryPack.TIBETAN, progress = 0.4f, totalEntries = null))
        }
    }

    @Test
    fun `Ready carries the prefs once start-up has installed the dictionary`() = runTest {
        installer.outcome = { InstallState.Ready(it, totalEntries = 180_412).also { _ -> databases.activate(it) } }
        val init = initializer(installer)

        viewModel(installer, init).uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Loading)

            init.runStartup()

            assertThat(awaitItem()).isEqualTo(
                AppRootState.Ready(
                    pack = DictionaryPack.TIBETAN,
                    onboarded = true,
                    appearance = Appearance.DARK,
                    textSize = AppTextSize.LARGE,
                    lineSpacing = 2.1f,
                ),
            )
        }
    }

    @Test
    fun `Ready follows later pref changes`() = runTest {
        databases.activate(DictionaryPack.TIBETAN)
        installer.state.value = InstallState.Ready(DictionaryPack.TIBETAN, totalEntries = 10)

        viewModel().uiState.test {
            assertThat((awaitItem() as AppRootState.Ready).appearance).isEqualTo(Appearance.DARK)

            prefs.setAppearance(Appearance.LIGHT)
            assertThat((awaitItem() as AppRootState.Ready).appearance).isEqualTo(Appearance.LIGHT)
        }
    }

    @Test
    fun `a failed install shows the installing screen with the failure`() = runTest {
        val cause = IOException("disk full")
        installer.state.value = InstallState.Failed(DictionaryPack.TIBETAN, cause)

        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Installing(DictionaryPack.TIBETAN, progress = null, totalEntries = null, failure = cause))
        }
    }

    @Test
    fun `first install of Dzongkha shows installing for that pack`() = runTest {
        prefs.setDictionaryPack(DictionaryPack.DZONGKHA)
        installer.state.value = InstallState.Copying(DictionaryPack.DZONGKHA, 0.3f)

        viewModel().uiState.test {
            assertThat(awaitItem())
                .isEqualTo(AppRootState.Installing(DictionaryPack.DZONGKHA, progress = 0.3f, totalEntries = null))
        }
    }

    @Test
    fun `a switch shows the overlay state, then the new pack once it is active`() = runTest {
        databases.activate(DictionaryPack.TIBETAN)
        installer.state.value = InstallState.Ready(DictionaryPack.TIBETAN, totalEntries = 10)
        val inProgress = SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.5f)

        viewModel().uiState.test {
            assertThat((awaitItem() as AppRootState.Ready).switching).isEqualTo(SwitchState.Idle)

            switcher.state.value = inProgress
            val switching = awaitItem() as AppRootState.Ready
            assertThat(switching.pack).isEqualTo(DictionaryPack.TIBETAN)
            assertThat(switching.switching).isEqualTo(inProgress)

            databases.activate(DictionaryPack.DZONGKHA)
            switcher.state.value = SwitchState.Idle
            val done = expectMostRecentItem() as AppRootState.Ready
            assertThat(done.pack).isEqualTo(DictionaryPack.DZONGKHA)
            assertThat(done.switching).isEqualTo(SwitchState.Idle)
        }
    }

    @Test
    fun `a failed switch keeps the old pack and carries the failure`() = runTest {
        databases.activate(DictionaryPack.TIBETAN)
        // The installer stays Failed(DZONGKHA) after a failed switch; that must not bring back the installer screen.
        val cause = IOException("disk full")
        installer.state.value = InstallState.Failed(DictionaryPack.DZONGKHA, cause)
        switcher.state.value = SwitchState.Failed(DictionaryPack.DZONGKHA, cause)

        viewModel().uiState.test {
            val state = awaitItem() as AppRootState.Ready
            assertThat(state.pack).isEqualTo(DictionaryPack.TIBETAN)
            assertThat(state.switching).isEqualTo(SwitchState.Failed(DictionaryPack.DZONGKHA, cause))
        }
    }

    @Test
    fun `keeping the current dictionary dismisses the failure`() = runTest {
        databases.activate(DictionaryPack.TIBETAN)
        switcher.state.value = SwitchState.Failed(DictionaryPack.DZONGKHA, IOException("disk full"))
        val vm = viewModel()

        vm.uiState.test {
            assertThat((awaitItem() as AppRootState.Ready).switching).isInstanceOf(SwitchState.Failed::class.java)

            vm.keepCurrentDictionary()

            assertThat((awaitItem() as AppRootState.Ready).switching).isEqualTo(SwitchState.Idle)
        }
    }

    @Test
    fun `retrySwitch retries the failed switch`() = runTest {
        databases.activate(DictionaryPack.TIBETAN)
        switcher.state.value = SwitchState.Failed(DictionaryPack.DZONGKHA, IOException("disk full"))

        viewModel().retrySwitch()

        assertThat(switcher.requests).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `retryInstall uses the stored pack`() = runTest {
        prefs.setDictionaryPack(DictionaryPack.DZONGKHA)
        installer.state.value = InstallState.Failed(DictionaryPack.DZONGKHA, IOException("disk full"))

        viewModel().retryInstall()

        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `an installer that throws while checking shows the retry screen instead of the splash`() = runTest {
        val throwing = ThrowingInstaller(InstallState.Checking, IllegalStateException("installer bug"))
        val init = initializer(throwing)

        viewModel(throwing, init).uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Loading)

            init.runStartup()

            val state = awaitItem() as AppRootState.Installing
            assertThat(state.progress).isNull()
            assertThat(state.failure).isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Test
    fun `retryInstall runs the installer again`() = runTest {
        installer.state.value = InstallState.Failed(DictionaryPack.TIBETAN, IOException("disk full"))
        installer.outcome = { InstallState.Ready(it, totalEntries = 10).also { _ -> databases.activate(it) } }
        val vm = viewModel()

        vm.uiState.test {
            assertThat(awaitItem()).isInstanceOf(AppRootState.Installing::class.java)

            vm.retryInstall()

            assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.TIBETAN)
            assertThat(awaitItem()).isInstanceOf(AppRootState.Ready::class.java)
        }
    }

    @Test
    fun `a retry keeps running after its screen is gone`() = runTest {
        val copying = CompletableDeferred<Unit>()
        val slow = object : DictionaryInstaller by installer {
            override suspend fun ensureInstalled(pack: DictionaryPack) {
                copying.await()
                installer.ensureInstalled(pack)
            }
        }
        installer.state.value = InstallState.Failed(DictionaryPack.TIBETAN, IOException("disk full"))
        installer.outcome = { InstallState.Ready(it, totalEntries = 10) }
        val vm = viewModel(slow)
        val store = ViewModelStore().apply { put("root", vm) }

        vm.retryInstall()
        // The activity finishes mid-copy: its view models are cleared.
        store.clear()
        copying.complete(Unit)

        assertThat(installer.ensureInstalledCalls).containsExactly(DictionaryPack.TIBETAN)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, totalEntries = 10))
    }

    @Test
    fun `retryInstall reports an installer that throws instead of crashing`() = runTest {
        val bug = IllegalStateException("installer bug")
        val throwing = ThrowingInstaller(InstallState.Failed(DictionaryPack.TIBETAN, IOException("disk full")), bug)

        viewModel(throwing).retryInstall()

        assertThat(errors.recorded).containsExactly(bug)
    }
}
