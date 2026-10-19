package com.kharagedition.tibetandictionary.ui.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeInstaller
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AppRootViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val installer = FakeInstaller()
    private val prefs = FakeUserPreferencesRepository(
        UserPrefs(
            onboarded = true,
            appLanguage = AppLanguage.BO,
            appearance = Appearance.DARK,
            textSize = AppTextSize.LARGE,
            lineSpacing = 2.1f,
        ),
    )
    private val errors = RecordingErrorReporter()

    private fun viewModel(installer: DictionaryInstaller = this.installer) = AppRootViewModel(installer, prefs, errors)

    @Test
    fun `Loading while the installer is checking`() = runTest {
        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Loading)
        }
    }

    @Test
    fun `Installing with the progress while the asset is copied`() = runTest {
        installer.state.value = InstallState.Copying(0.4f)

        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Installing(progress = 0.4f, totalEntries = null))
        }
    }

    @Test
    fun `Ready carries the prefs once the installer is ready`() = runTest {
        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Loading)

            installer.state.value = InstallState.Copying(0.9f)
            assertThat(awaitItem()).isInstanceOf(AppRootState.Installing::class.java)

            installer.state.value = InstallState.Ready(totalEntries = 180_412)
            assertThat(awaitItem()).isEqualTo(
                AppRootState.Ready(
                    onboarded = true,
                    appearance = Appearance.DARK,
                    textSize = AppTextSize.LARGE,
                    lineSpacing = 2.1f,
                    language = AppLanguage.BO,
                ),
            )
        }
    }

    @Test
    fun `Ready follows later pref changes`() = runTest {
        installer.state.value = InstallState.Ready(totalEntries = 10)

        viewModel().uiState.test {
            assertThat((awaitItem() as AppRootState.Ready).appearance).isEqualTo(Appearance.DARK)

            prefs.setAppearance(Appearance.LIGHT)
            assertThat((awaitItem() as AppRootState.Ready).appearance).isEqualTo(Appearance.LIGHT)
        }
    }

    @Test
    fun `a failed install shows the installing screen with the failure`() = runTest {
        val cause = IOException("disk full")
        installer.state.value = InstallState.Failed(cause)

        viewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(AppRootState.Installing(progress = null, totalEntries = null, failure = cause))
        }
    }

    @Test
    fun `retryInstall runs the installer again`() = runTest {
        installer.state.value = InstallState.Failed(IOException("disk full"))
        installer.outcome = InstallState.Ready(totalEntries = 10)
        val vm = viewModel()

        vm.uiState.test {
            assertThat(awaitItem()).isInstanceOf(AppRootState.Installing::class.java)

            vm.retryInstall()

            assertThat(installer.ensureInstalledCalls).isEqualTo(1)
            assertThat(awaitItem()).isInstanceOf(AppRootState.Ready::class.java)
        }
    }

    @Test
    fun `retryInstall reports an installer that throws instead of crashing`() = runTest {
        val bug = IllegalStateException("installer bug")
        val throwing = object : DictionaryInstaller {
            override val state = MutableStateFlow<InstallState>(InstallState.Failed(IOException("disk full")))
            override suspend fun ensureInstalled() = throw bug
        }

        viewModel(throwing).retryInstall()

        assertThat(errors.recorded).containsExactly(bug)
    }
}
