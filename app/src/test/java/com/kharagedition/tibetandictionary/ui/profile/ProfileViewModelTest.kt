package com.kharagedition.tibetandictionary.ui.profile

import android.app.Activity
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.LocaleApplier
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.auth.DeleteAccount
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.translation.ApiResult
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Quota
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslationRefusal
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionarySwitcher
import com.kharagedition.tibetandictionary.fakes.FakeTranslationApi
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.model.SignInAction
import com.kharagedition.tibetandictionary.ui.model.UiText
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
    private val locale = RecordingLocaleApplier()
    private val switcher = FakeDictionarySwitcher()

    /** Rate and Share app go to [PLAY_URL]; Support and Privacy have no URL, so they are hidden. */
    private var linkTargets = ProfileLinkTargets(
        playStore = PLAY_URL,
        support = "",
        privacy = "",
        otherApps = OtherApp.entries,
    )

    /** A per-app language that starts at [current] (null = none set) and records every switch. */
    private class RecordingLocaleApplier(var current: AppLanguage? = null) : LocaleApplier {
        val switched = mutableListOf<AppLanguage>()

        override fun setLanguage(language: AppLanguage) {
            switched += language
            current = language
        }

        override fun currentLanguage(): AppLanguage? = current
    }

    private val errors = RecordingErrorReporter()
    private val auth = FakeAuthRepository()
    private val translation = FakeTranslationRepository()
    private val api = FakeTranslationApi()
    private val analytics = RecordingAnalytics()

    private fun newViewModel(prefs: UserPreferencesRepository = this.prefs) =
        ProfileViewModel(
            prefs,
            dictionary,
            switcher,
            locale,
            linkTargets,
            errors,
            auth,
            translation,
            SignInAction(auth, translation, analytics),
            DeleteAccount(api, auth, analytics),
            analytics,
        )

    /** A view model whose state is collected for the whole test, as the screen does. */
    private fun TestScope.viewModel(prefs: UserPreferencesRepository = this@ProfileViewModelTest.prefs): ProfileViewModel {
        val vm = newViewModel(prefs)
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

    // Dictionary

    @Test
    fun `dictionaryRowShowsActivePack`() = runTest {
        val vm = viewModel()
        assertThat(vm.state().dictionary).isEqualTo(DictionaryPack.TIBETAN)

        dictionary.capabilities.value = SampleData.capabilities.copy(pack = DictionaryPack.DZONGKHA)

        assertThat(vm.state().dictionary).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `setDictionaryClosesSheetAndRequestsSwitch`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.DICTIONARY))
        assertThat(vm.state().sheet).isEqualTo(ProfileSheet.DICTIONARY)

        vm.onEvent(ProfileEvent.SetDictionary(DictionaryPack.DZONGKHA))

        assertThat(vm.state().sheet).isNull()
        assertThat(switcher.requests).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `setSameDictionaryDoesNotSwitch`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.DICTIONARY))

        vm.onEvent(ProfileEvent.SetDictionary(DictionaryPack.TIBETAN))

        assertThat(vm.state().sheet).isNull()
        assertThat(switcher.requests).isEmpty()
    }

    @Test
    fun `setDictionaryWhileASwitchRunsClosesTheSheetAndRecordsNothing`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.DICTIONARY))
        val running = SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.4f)
        switcher.state.value = running

        vm.onEvent(ProfileEvent.SetDictionary(DictionaryPack.TIBETAN))

        assertThat(vm.state().sheet).isNull()
        assertThat(errors.recorded).isEmpty()
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(switcher.state.value).isEqualTo(running)
        assertThat(switcher.requests).isEmpty()
    }

    @Test
    fun `setDictionaryToAnotherPackWhileASwitchRunsClosesTheSheetAndAsksForThatPack`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.DICTIONARY))
        val running = SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.4f)
        switcher.state.value = running

        // The capabilities still report Tibetan, so the view model forwards the request; the switcher decides.
        vm.onEvent(ProfileEvent.SetDictionary(DictionaryPack.DZONGKHA))

        assertThat(vm.state().sheet).isNull()
        assertThat(errors.recorded).isEmpty()
        assertThat(switcher.requests).containsExactly(DictionaryPack.DZONGKHA)
    }

    // Dictionary pack

    @Test
    fun `the pack and its entry count both come from the capabilities`() = runTest {
        val dzCapabilities = SampleData.capabilities.copy(
            pack = DictionaryPack.DZONGKHA,
            directions = listOf(SampleData.boEn.copy(entryCount = 81_474)),
        )
        val vm = newViewModel()

        vm.uiState.test {
            dictionary.capabilities.value = dzCapabilities
            val seen = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item)?.value }
                .filterNotNull()

            assertThat(seen.first().dictionary).isEqualTo(DictionaryPack.TIBETAN)
            for (state in seen) {
                val isDz = state.dictionary == DictionaryPack.DZONGKHA
                val count = if (isDz) 81_474 else SampleData.capabilities.totalEntries
                assertThat(state.entryCount).isEqualTo(count)
            }
            assertThat(seen.last().dictionary).isEqualTo(DictionaryPack.DZONGKHA)
        }
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
    fun `a default direction left over from the other pack shows the active pack's default`() = runTest {
        val dzEn = SampleData.boEn.copy(id = "dz-en", position = 1)
        val enDz = SampleData.enBo.copy(id = "en-dz", position = 0)
        dictionary.capabilities.value = SampleData.capabilities.copy(
            pack = DictionaryPack.DZONGKHA,
            directions = listOf(enDz, dzEn),
        )
        prefs.prefs.value = UserPrefs(defaultDirectionId = "bo-en")

        val state = viewModel().state()

        assertThat(state.defaultDirection).isEqualTo(dzEn)
        assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo("bo-en")
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

        assertThat(locale.switched).containsExactly(AppLanguage.BO)
        assertThat(prefs.prefs.value.appLanguage).isEqualTo(AppLanguage.BO)
        assertThat(vm.state().language).isEqualTo(AppLanguage.BO)
        assertThat(vm.state().sheet).isNull()
    }

    @Test
    fun `a setting that cannot be stored is recorded and shown as an error toast`() = runTest {
        val failing = object : UserPreferencesRepository by prefs {
            override suspend fun setAppearance(appearance: Appearance) = throw IOException("disk full")
            override suspend fun setTextSize(size: AppTextSize) = throw IOException("disk full")
        }
        val vm = viewModel(failing)

        vm.effects.test {
            vm.onEvent(ProfileEvent.SetAppearance(Appearance.DARK))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.entry_action_failed)))
            vm.onEvent(ProfileEvent.SetTextSize(AppTextSize.LARGE))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.entry_action_failed)))
        }
        assertThat(errors.recorded.map { it.message }).containsExactly("disk full", "disk full")
        // The screen keeps showing what is stored.
        assertThat(vm.state().appearance).isEqualTo(Appearance.SYSTEM)
        assertThat(vm.state().textSize).isEqualTo(AppTextSize.DEFAULT)
    }

    @Test
    fun `a language that cannot be stored is not switched to, and the toast says so`() = runTest {
        val failing = object : UserPreferencesRepository by prefs {
            override suspend fun setAppLanguage(language: AppLanguage) = throw IOException("disk full")
        }
        val vm = viewModel(failing)

        vm.effects.test {
            vm.onEvent(ProfileEvent.SetLanguage(AppLanguage.BO))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.entry_action_failed)))
        }
        assertThat(errors.recorded).hasSize(1)
        assertThat(locale.switched).isEmpty()
        assertThat(vm.state().language).isEqualTo(AppLanguage.EN)
    }

    @Test
    fun `choosing the language already in use does nothing`() = runTest {
        val vm = viewModel()
        vm.onEvent(ProfileEvent.OpenSheet(ProfileSheet.LANGUAGE))

        vm.onEvent(ProfileEvent.SetLanguage(AppLanguage.EN))

        assertThat(locale.switched).isEmpty()
        assertThat(prefs.prefs.value.appLanguage).isEqualTo(AppLanguage.EN)
        assertThat(vm.state().sheet).isNull()
    }

    @Test
    fun `the language shown is the app's language even when the stored one is stale`() = runTest {
        // Changed to བོད་ཡིག in system settings; prefs catch up only at the next start.
        locale.current = AppLanguage.BO
        prefs.prefs.value = UserPrefs(appLanguage = AppLanguage.EN)
        val vm = viewModel()

        assertThat(vm.state().language).isEqualTo(AppLanguage.BO)

        vm.onEvent(ProfileEvent.SetLanguage(AppLanguage.BO))
        assertThat(locale.switched).isEmpty()
    }

    @Test
    fun `the stored language is shown while no app language is set`() = runTest {
        prefs.prefs.value = UserPrefs(appLanguage = AppLanguage.BO)

        assertThat(viewModel().state().language).isEqualTo(AppLanguage.BO)
    }

    @Test
    fun `the app language is read again each time the screen is shown`() = runTest {
        val vm = viewModel()
        assertThat(vm.state().language).isEqualTo(AppLanguage.EN)

        locale.current = AppLanguage.BO
        vm.onEvent(ProfileEvent.Shown)

        assertThat(vm.state().language).isEqualTo(AppLanguage.BO)
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
    fun `Privacy link hidden when the privacy URL is blank`() = runTest {
        assertThat(viewModel().state().links).doesNotContain(ProfileLink.PRIVACY)
    }

    @Test
    fun `Privacy link shown and opened once the privacy URL is set`() = runTest {
        linkTargets = linkTargets.copy(privacy = "https://privacy.example")
        val vm = viewModel()

        assertThat(vm.state().links).contains(ProfileLink.PRIVACY)
        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.PRIVACY))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.OpenUrl("https://privacy.example"))
        }
    }

    @Test
    fun `footer links follow their targets - each is hidden while its link is blank`() {
        val all = ProfileLinkTargets(
            playStore = "https://play.example/app",
            support = "https://support.example",
            privacy = "https://privacy.example",
            otherApps = OtherApp.entries,
        )
        assertThat(all.visibleFor(DictionaryPack.TIBETAN)).containsExactly(
            ProfileLink.ABOUT,
            ProfileLink.RATE,
            ProfileLink.SHARE,
            ProfileLink.MORE_APPS,
            ProfileLink.SUPPORT,
            ProfileLink.PRIVACY,
        ).inOrder()

        val none = ProfileLinkTargets(playStore = "", support = "", privacy = " ", otherApps = emptyList())
        assertThat(none.visibleFor(DictionaryPack.TIBETAN)).containsExactly(ProfileLink.ABOUT)
    }

    @Test
    fun `More apps lists the active dictionary's apps and hides when it has none`() {
        val targets = ProfileLinkTargets(otherApps = OtherApp.entries)
        assertThat(targets.otherAppsFor(DictionaryPack.TIBETAN))
            .containsExactly(OtherApp.PRAYER, OtherApp.CALENDAR).inOrder()
        assertThat(targets.otherAppsFor(DictionaryPack.DZONGKHA))
            .containsExactly(OtherApp.DZONGKHA_KEYBOARD, OtherApp.BHUTANESE_CALENDAR).inOrder()

        val tibetanOnly = ProfileLinkTargets(otherApps = listOf(OtherApp.PRAYER))
        assertThat(tibetanOnly.visibleFor(DictionaryPack.TIBETAN)).contains(ProfileLink.MORE_APPS)
        assertThat(tibetanOnly.visibleFor(DictionaryPack.DZONGKHA)).doesNotContain(ProfileLink.MORE_APPS)
    }

    @Test
    fun `the footer shows the links whose targets are set`() = runTest {
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
            assertThat(awaitItem()).isEqualTo(ProfileEffect.OpenUrl(PLAY_URL))

            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.SHARE))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.ShareApp(PLAY_URL, DictionaryPack.TIBETAN))
        }
    }

    @Test
    fun `Share app names the active pack`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(pack = DictionaryPack.DZONGKHA)
        val vm = viewModel()
        vm.state()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.SHARE))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.ShareApp(PLAY_URL, DictionaryPack.DZONGKHA))
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

            assertThat(awaitItem()).isEqualTo(ProfileEffect.OpenUrl(OtherApp.CALENDAR.url))
            assertThat(vm.state().sheet).isNull()
        }
    }

    @Test
    fun `with the Dzongkha dictionary active More apps lists the Dzongkha apps`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(pack = DictionaryPack.DZONGKHA)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenLink(ProfileLink.MORE_APPS))
            assertThat(vm.state().otherApps)
                .containsExactly(OtherApp.DZONGKHA_KEYBOARD, OtherApp.BHUTANESE_CALENDAR).inOrder()

            vm.onEvent(ProfileEvent.OpenOtherApp(OtherApp.DZONGKHA_KEYBOARD))

            assertThat(awaitItem()).isEqualTo(
                ProfileEffect.OpenUrl(
                    "https://play.google.com/store/apps/details?id=com.kharagedition.tibetankeyboard&listing=dzongkha",
                ),
            )
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
    fun `no account while signed out, and no word-of-the-day notification while that feature is off`() = runTest {
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

    // Account and plan (1z)

    private val tenzin = Account("u1", isGuest = false, name = "Tenzin Dolma", email = "tenzin@example.com")
    private val guest = Account("g1", isGuest = true, name = null, email = null)

    private fun quota(plan: Plan, used: Int, limit: Int, maxChars: Int = 100, freeLimit: Int? = 10) =
        QuotaState(Quota(plan, used, limit, Instant.parse("2026-10-20T00:00:00Z"), maxChars), freeLimit)

    @Test
    fun `a google account shows the account card`() = runTest {
        auth.account.value = tenzin
        translation.quota.value = quota(Plan.FREE, used = 3, limit = 10)
        val state = viewModel().state()

        assertThat(state.account)
            .isEqualTo(AccountUi(name = "Tenzin Dolma", email = "tenzin@example.com", initials = "TD"))
        assertThat(state.plan).isEqualTo(PlanCardUi.Free(used = 3, limit = 10, maxChars = 100))
    }

    @Test
    fun `a guest shows the plan card Guest 1 of 3`() = runTest {
        auth.account.value = guest
        translation.quota.value = quota(Plan.GUEST, used = 1, limit = 3)
        val state = viewModel().state()

        assertThat(state.account).isNull()
        assertThat(state.plan).isEqualTo(PlanCardUi.Guest(used = 1, limit = 3, freeLimit = 10))
    }

    @Test
    fun `no user shows NoUser`() = runTest {
        // A quota left from a signed-out user is not this install's count.
        translation.quota.value = quota(Plan.FREE, used = 3, limit = 10)
        val state = viewModel().state()

        assertThat(state.account).isNull()
        assertThat(state.plan).isEqualTo(PlanCardUi.NoUser)
    }

    @Test
    fun `a google account whose usage is not known yet shows no plan card`() = runTest {
        auth.account.value = tenzin
        val state = viewModel().state()

        assertThat(state.plan).isNull()
    }

    @Test
    fun `initials come from the name, or from the email without one`() = runTest {
        auth.account.value = tenzin.copy(name = "tenzin")
        val vm = viewModel()
        assertThat(vm.state().account?.initials).isEqualTo("T")

        auth.account.value = tenzin.copy(name = null, email = "dolma@example.com")
        assertThat(vm.state().account).isEqualTo(AccountUi(name = null, email = "dolma@example.com", initials = "D"))
    }

    @Test
    fun `toggle smart translate persists`() = runTest {
        val vm = viewModel()
        assertThat(vm.state().smartTranslate).isTrue()

        vm.onEvent(ProfileEvent.ToggleSmartTranslate)

        assertThat(prefs.prefs.value.smartTranslate).isFalse()
        assertThat(vm.state().smartTranslate).isFalse()
    }

    @Test
    fun `confirm delete calls DeleteAccount and toasts`() = runTest {
        auth.account.value = tenzin
        api.deleteResults += ApiResult.Ok(Unit)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenAccount)
            vm.onEvent(ProfileEvent.AskDeleteAccount)
            assertThat(vm.state().sheet).isNull()
            assertThat(vm.state().confirmDelete).isTrue()

            vm.onEvent(ProfileEvent.ConfirmDeleteAccount)

            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.account_deleted)))
            assertThat(vm.state().confirmDelete).isFalse()
            assertThat(api.deleteCalls).isEqualTo(1)
            assertThat(auth.account.value).isNull()
            assertThat(analytics.events).contains(AnalyticsEvent.AccountDeleted)
        }
    }

    @Test
    fun `a running delete keeps the dialog open with progress until it finishes`() = runTest {
        auth.account.value = tenzin
        api.deleteResults += ApiResult.Ok(Unit)
        val gate = CompletableDeferred<Unit>().also { api.deleteGate = it }
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.AskDeleteAccount)
            vm.onEvent(ProfileEvent.ConfirmDeleteAccount)
            vm.onEvent(ProfileEvent.DismissDelete)
            assertThat(vm.state().confirmDelete).isTrue()
            assertThat(vm.state().deleting).isTrue()

            gate.complete(Unit)

            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.account_deleted)))
            assertThat(vm.state().confirmDelete).isFalse()
            assertThat(vm.state().deleting).isFalse()
        }
    }

    @Test
    fun `a failed delete keeps the account and says so`() = runTest {
        auth.account.value = tenzin
        api.deleteResults += ApiResult.Refused(TranslationRefusal.Offline)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.AskDeleteAccount)
            vm.onEvent(ProfileEvent.ConfirmDeleteAccount)

            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.account_delete_failed)))
            assertThat(auth.account.value).isEqualTo(tenzin)
        }
    }

    @Test
    fun `cancelling the delete closes the dialog`() = runTest {
        auth.account.value = tenzin
        val vm = viewModel()

        vm.onEvent(ProfileEvent.AskDeleteAccount)
        vm.onEvent(ProfileEvent.DismissDelete)

        assertThat(vm.state().confirmDelete).isFalse()
        assertThat(api.deleteCalls).isEqualTo(0)
    }

    @Test
    fun `sign out toasts`() = runTest {
        auth.account.value = tenzin
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(ProfileEvent.OpenAccount)
            assertThat(vm.state().sheet).isEqualTo(ProfileSheet.ACCOUNT)

            vm.onEvent(ProfileEvent.SignOut)

            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.signed_out)))
            assertThat(vm.state().sheet).isNull()
            assertThat(auth.signOutCalls).isEqualTo(1)
            assertThat(vm.state().account).isNull()
        }
    }

    @Test
    fun `OpenPro logs profile_plan and shows the sheet`() = runTest {
        val vm = viewModel()

        vm.onEvent(ProfileEvent.OpenPro)
        assertThat(analytics.events).containsExactly(AnalyticsEvent.ProInterest(PromptSource.PROFILE_PLAN))
        assertThat(vm.state().showProSheet).isTrue()

        vm.onEvent(ProfileEvent.DismissProSheet)
        assertThat(vm.state().showProSheet).isFalse()
    }

    @Test
    fun `signIn logs its source and toasts the result`() = runTest {
        auth.account.value = guest
        val vm = viewModel()

        vm.effects.test {
            vm.signIn(mockk<Activity>(relaxed = true), PromptSource.PROFILE_PLAN)

            assertThat(awaitItem()).isEqualTo(ProfileEffect.Toast(UiText(R.string.signed_in)))
            assertThat(analytics.events.first()).isEqualTo(AnalyticsEvent.SignInPrompt(PromptSource.PROFILE_PLAN))
            assertThat(auth.signInCalls).isEqualTo(1)
        }
    }

    @Test
    fun `Shown refreshes translation`() = runTest {
        val vm = viewModel()

        vm.onEvent(ProfileEvent.Shown)

        assertThat(translation.refreshCalls).containsExactly(false)
    }

    private companion object {
        const val PLAY_URL = "https://play.example/app"
    }
}
