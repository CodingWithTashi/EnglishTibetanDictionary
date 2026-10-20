package com.kharagedition.tibetandictionary.ui.profile

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val prefs = FakeUserPreferencesRepository(UserPrefs(onboarded = true))
    private val dictionary = FakeDictionaryRepository()
    private val appliedLocales = mutableListOf<AppLanguage>()

    /** A view model whose state is collected for the whole test, as the screen does. */
    private fun TestScope.viewModel(): ProfileViewModel {
        val vm = ProfileViewModel(prefs, dictionary) { appliedLocales += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun ProfileViewModel.state(): ProfileUiState = checkNotNull(uiState.value)

    // Reading settings

    @Test
    fun `state carries the stored reading settings, the visible directions and the app version`() = runTest {
        prefs.prefs.value = UserPrefs(
            textSize = AppTextSize.LARGE,
            lineSpacing = 2.1f,
            appearance = Appearance.DARK,
            defaultDirectionId = SampleData.enBo.id,
            appLanguage = AppLanguage.BO,
        )

        val state = viewModel().state()

        assertThat(state.textSize).isEqualTo(AppTextSize.LARGE)
        assertThat(state.lineSpacing).isEqualTo(2.1f)
        assertThat(state.appearance).isEqualTo(Appearance.DARK)
        assertThat(state.defaultDirection).isEqualTo(SampleData.enBo)
        assertThat(state.directions).containsExactly(SampleData.boEn, SampleData.boBo, SampleData.enBo).inOrder()
        assertThat(state.language).isEqualTo(AppLanguage.BO)
        assertThat(state.versionName).isEqualTo(BuildConfig.VERSION_NAME)
        assertThat(state.sheet).isNull()
    }

    @Test
    fun `theme change persists`() = runTest {
        val vm = viewModel()

        vm.onEvent(ProfileEvent.SetAppearance(Appearance.DARK))

        assertThat(prefs.prefs.value.appearance).isEqualTo(Appearance.DARK)
        assertThat(vm.state().appearance).isEqualTo(Appearance.DARK)
    }

    @Test
    fun `text size and line spacing changes persist`() = runTest {
        val vm = viewModel()

        vm.onEvent(ProfileEvent.SetTextSize(AppTextSize.LARGEST))
        vm.onEvent(ProfileEvent.SetLineSpacing(2.3f))

        assertThat(prefs.prefs.value.textSize).isEqualTo(AppTextSize.LARGEST)
        assertThat(prefs.prefs.value.lineSpacing).isEqualTo(2.3f)
        assertThat(vm.state().textSize).isEqualTo(AppTextSize.LARGEST)
        assertThat(vm.state().lineSpacing).isEqualTo(2.3f)
    }

    // Default direction

    @Test
    fun `defaultDirectionFallback - a stored direction that is not visible falls back to the first visible one`() =
        runTest {
            // bo-en emptied by a data release: bo-bo (position 1) is now the first visible direction.
            val emptied = SampleData.boEn.copy(entryCount = 0)
            dictionary.capabilities.value = SampleData.capabilities.copy(
                directions = listOf(SampleData.enBo, emptied, SampleData.boBo),
            )
            prefs.prefs.value = UserPrefs(defaultDirectionId = SampleData.boEn.id)

            val state = viewModel().state()

            assertThat(state.defaultDirection).isEqualTo(SampleData.boBo)
            assertThat(state.directions).containsExactly(SampleData.boBo, SampleData.enBo).inOrder()
            // The fallback is for display only; the stored preference is left alone.
            assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo(SampleData.boEn.id)
        }

    @Test
    fun `defaultDirectionFallback - a stored direction missing from the data falls back to the first visible one`() =
        runTest {
            prefs.prefs.value = UserPrefs(defaultDirectionId = "zh-bo")

            val state = viewModel().state()

            assertThat(state.defaultDirection).isEqualTo(SampleData.boEn)
            assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo("zh-bo")
        }

    @Test
    fun `no default direction when no direction has entries`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(directions = emptyList())

        val state = viewModel().state()

        assertThat(state.defaultDirection).isNull()
        assertThat(state.directions).isEmpty()
    }

    @Test
    fun `picking a default direction persists it and closes the sheet`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.DIRECTION))
        assertThat(vm.state().sheet).isEqualTo(ProfileSheet.DIRECTION)

        vm.onEvent(ProfileEvent.SetDefaultDirection(SampleData.enBo))

        assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo(SampleData.enBo.id)
        assertThat(vm.state().defaultDirection).isEqualTo(SampleData.enBo)
        assertThat(vm.state().sheet).isNull()
    }

    // Language

    @Test
    fun `language change applies the app locale and persists`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.LANGUAGE))

        vm.onEvent(ProfileEvent.SetLanguage(AppLanguage.BO))

        assertThat(appliedLocales).containsExactly(AppLanguage.BO)
        assertThat(prefs.prefs.value.appLanguage).isEqualTo(AppLanguage.BO)
        assertThat(vm.state().language).isEqualTo(AppLanguage.BO)
        assertThat(vm.state().sheet).isNull()
    }

    // Version line

    @Test
    fun `the version line counts the entries of every direction`() = runTest {
        val total = SampleData.boEn.entryCount + SampleData.boBo.entryCount + SampleData.enBo.entryCount

        assertThat(viewModel().state().entryCount).isEqualTo(total)
    }

    @Test
    fun `version line omits count when null`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(directions = emptyList())

        val state = viewModel().state()

        assertThat(state.entryCount).isNull()
        assertThat(state.versionName).isEqualTo(BuildConfig.VERSION_NAME)
    }

    // Footer links

    @Test
    fun `Privacy link hidden when Links PRIVACY_URL blank`() = runTest {
        assertThat(Links.PRIVACY_URL).isEmpty()

        assertThat(viewModel().state().links).doesNotContain(ProfileLink.PRIVACY)
    }

    @Test
    fun `footer links follow their targets - each is hidden while its link is blank`() {
        val all = ProfileLinkTargets(
            playStore = "https://play.example/app",
            support = "https://support.example",
            privacy = "https://privacy.example",
            otherApps = OtherApp.entries,
        )
        assertThat(all.visible).containsExactly(
            ProfileLink.ABOUT,
            ProfileLink.RATE,
            ProfileLink.SHARE,
            ProfileLink.MORE_APPS,
            ProfileLink.SUPPORT,
            ProfileLink.PRIVACY,
        ).inOrder()

        val none = ProfileLinkTargets(playStore = "", support = "", privacy = " ", otherApps = emptyList())
        assertThat(none.visible).containsExactly(ProfileLink.ABOUT)
    }

    @Test
    fun `the shipped links show About, Rate, Share app and More apps`() = runTest {
        assertThat(viewModel().state().links).containsExactly(
            ProfileLink.ABOUT,
            ProfileLink.RATE,
            ProfileLink.SHARE,
            ProfileLink.MORE_APPS,
        ).inOrder()
    }

    @Test
    fun `About navigates, Rate opens the Play listing and Share app shares the listing`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.ABOUT))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.NavigateAbout)

            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.RATE))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.OpenUrl(Links.PLAY_STORE_URL))

            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.SHARE))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.ShareApp(Links.PLAY_STORE_URL))
        }
    }

    @Test
    fun `a hidden link does nothing`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.PRIVACY))
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.SUPPORT))
            expectNoEvents()
        }
    }

    @Test
    fun `More apps opens the sheet and an app there opens its listing`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.MORE_APPS))
            assertThat(vm.state().sheet).isEqualTo(ProfileSheet.MORE_APPS)
            assertThat(vm.state().otherApps).containsExactly(OtherApp.PRAYER, OtherApp.CALENDAR).inOrder()

            vm.onEvent(ProfileEvent.OpenOtherApp(OtherApp.CALENDAR))

            assertThat(awaitItem()).isEqualTo(ProfileEffect.OpenUrl(Links.CALENDAR_URL))
            assertThat(vm.state().sheet).isNull()
        }
    }

    // Sheets and unreleased features

    @Test
    fun `sheets open and dismiss`() = runTest {
        val vm = viewModel()

        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.TEXT_SIZE))
        assertThat(vm.state().sheet).isEqualTo(ProfileSheet.TEXT_SIZE)

        vm.onEvent(ProfileEvent.DismissSheet)
        assertThat(vm.state().sheet).isNull()
    }

    @Test
    fun `no account and no word-of-the-day notification while those features are off`() = runTest {
        val state = viewModel().state()

        assertThat(state.account).isNull()
        assertThat(state.wotdNotification).isNull()
    }

    @Test
    fun `toggling the word-of-the-day notification keeps its time`() = runTest {
        prefs.prefs.value = UserPrefs(wotdNotificationEnabled = false, wotdNotificationMinute = 8 * 60)
        val vm = viewModel()

        vm.onEvent(ProfileEvent.ToggleWotdNotification)

        assertThat(prefs.prefs.value.wotdNotificationEnabled).isTrue()
        assertThat(prefs.prefs.value.wotdNotificationMinute).isEqualTo(8 * 60)
    }
}
