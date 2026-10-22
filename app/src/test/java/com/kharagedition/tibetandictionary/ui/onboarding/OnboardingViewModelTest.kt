package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionarySwitcher
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val prefs = FakeUserPreferencesRepository()
    private val dictionary = FakeDictionaryRepository()

    private val errors = RecordingErrorReporter()

    /** The pack the app reads from; the switcher fake activates into it, as the real switcher does. */
    private val databases = DictionaryDatabases { error("no database is opened in these tests") }.apply {
        activate(DictionaryPack.TIBETAN)
    }

    /** A switch that succeeds: activates the pack, stores the choice and swaps the capabilities. */
    private val switcher = FakeDictionarySwitcher { pack -> succeed(pack) }

    private fun succeed(pack: DictionaryPack) {
        databases.activate(pack)
        runBlocking { prefs.setDictionaryPack(pack) }
    }

    /** A view model whose state is collected for the whole test, as the screen does. */
    private fun TestScope.viewModel(
        prefs: UserPreferencesRepository = this@OnboardingViewModelTest.prefs,
        savedState: SavedStateHandle = SavedStateHandle(),
        switcher: DictionarySwitcher = this@OnboardingViewModelTest.switcher,
    ): OnboardingViewModel {
        val vm = OnboardingViewModel(savedState, prefs, dictionary, switcher, databases, errors)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun OnboardingViewModel.state(): OnboardingUiState = checkNotNull(uiState.value)

    /** Continue on the step shown now. */
    private fun OnboardingViewModel.next() = onEvent(OnboardingEvent.Next(state().step))

    /** Continue past the dictionary step, keeping the stored pack. */
    private fun OnboardingViewModel.pastDictionary() = next()

    /** A switcher whose switch ends in [SwitchState.Failed] without activating anything. */
    private fun failingSwitcher() = FakeDictionarySwitcher().also { fake ->
        fake.onSwitch = { pack -> fake.state.value = SwitchState.Failed(pack, IllegalStateException("no space")) }
    }

    // Dictionary step

    @Test
    fun `starts on the dictionary step with the stored pack selected`() = runTest {
        prefs.prefs.value = UserPrefs(dictionaryPack = DictionaryPack.DZONGKHA)

        val state = viewModel().state()

        assertThat(state.step).isEqualTo(OnboardingStep.DICTIONARY)
        assertThat(state.packs).containsExactly(DictionaryPack.TIBETAN, DictionaryPack.DZONGKHA).inOrder()
        assertThat(state.selectedPack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(state.switching).isFalse()
    }

    @Test
    fun `Continue on the dictionary step switches, then advances`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))
        assertThat(switcher.requests).isEmpty()

        vm.next()

        assertThat(switcher.requests).containsExactly(DictionaryPack.DZONGKHA)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
    }

    @Test
    fun `Continue with the pack already stored still persists the choice`() = runTest {
        val vm = viewModel()
        assertThat(prefs.prefs.value.dictionaryPackChosen).isFalse()

        vm.next()

        assertThat(switcher.requests).containsExactly(DictionaryPack.TIBETAN)
        assertThat(prefs.prefs.value.dictionaryPackChosen).isTrue()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
    }

    @Test
    fun `a restored draft whose pack is already active still records the choice on Continue`() = runTest {
        val savedState = SavedStateHandle(mapOf("onboarding_pack" to DictionaryPack.TIBETAN.id))
        val vm = viewModel(savedState = savedState)
        assertThat(databases.active.value).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(prefs.prefs.value.dictionaryPackChosen).isFalse()

        vm.next()

        assertThat(switcher.requests).containsExactly(DictionaryPack.TIBETAN)
        assertThat(prefs.prefs.value.dictionaryPackChosen).isTrue()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
    }

    @Test
    fun `a switch that fails keeps the step on the dictionary and onboarding unfinished`() = runTest {
        val failing = failingSwitcher()
        val vm = viewModel(switcher = failing)
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        vm.effects.test {
            vm.next()

            assertThat(vm.state().step).isEqualTo(OnboardingStep.DICTIONARY)
            // The switching overlay shows the failure, so it is not also recorded as a generic error.
            assertThat(errors.recorded).isEmpty()
            expectNoEvents()
        }
        assertThat(prefs.prefs.value.onboarded).isFalse()
        assertThat(prefs.prefs.value.dictionaryPackChosen).isFalse()
    }

    @Test
    fun `a switch that is rejected and ends idle without activating also keeps the dictionary step`() = runTest {
        val rejecting = FakeDictionarySwitcher { /* ends Idle, nothing activated */ }
        val vm = viewModel(switcher = rejecting)
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        vm.next()

        assertThat(rejecting.requests).containsExactly(DictionaryPack.DZONGKHA)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.DICTIONARY)
        assertThat(errors.recorded).hasSize(1)
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun `Skip after a failed switch does not finish and can be tapped again`() = runTest {
        val failing = failingSwitcher()
        val vm = viewModel(switcher = failing)
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Skip)
            expectNoEvents()
            assertThat(prefs.prefs.value.onboarded).isFalse()
            assertThat(errors.recorded).isEmpty()

            // The user retries after the failure is dismissed; this time the switch works.
            failing.onSwitch = { succeed(it) }
            vm.onEvent(OnboardingEvent.Skip)

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(failing.requests).containsExactly(DictionaryPack.DZONGKHA, DictionaryPack.DZONGKHA)
        assertThat(prefs.prefs.value.onboarded).isTrue()
    }

    @Test
    fun `Skip on the dictionary step switches before onboarding is marked done and finished is sent`() = runTest {
        val order = mutableListOf<String>()
        val real = prefs
        val recording = object : UserPreferencesRepository by real {
            override suspend fun setOnboarded(value: Boolean) {
                order += "onboarded"
                real.setOnboarded(value)
            }
        }
        switcher.onSwitch = { pack ->
            order += "switch ${pack.id}"
            succeed(pack)
        }
        val vm = viewModel(recording)
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Skip)
            order += "effect"
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }

        assertThat(order).containsExactly("switch dz", "onboarded", "effect").inOrder()
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `Skip while Continue's switch is running does not start a second switch`() = runTest {
        val running = Job()
        val slow = object : DictionarySwitcher {
            val requests = mutableListOf<DictionaryPack>()
            override val state: StateFlow<SwitchState> = MutableStateFlow(SwitchState.Idle)
            override fun switchTo(pack: DictionaryPack): Job {
                requests += pack
                return running
            }
            override fun retry() = Unit
            override fun dismiss() = Unit
        }
        val vm = viewModel(switcher = slow)
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        vm.effects.test {
            vm.next()
            vm.onEvent(OnboardingEvent.Skip)
            vm.next()

            assertThat(slow.requests).containsExactly(DictionaryPack.DZONGKHA)
            expectNoEvents()

            succeed(DictionaryPack.DZONGKHA)
            running.complete()

            assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
            expectNoEvents()
        }
        assertThat(prefs.prefs.value.onboarded).isFalse()
    }

    @Test
    fun `the selected pack survives process death`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState = savedState).onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        val restored =
            viewModel(savedState = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) }))

        assertThat(restored.state().selectedPack).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `the direction step shows the new pack's directions`() = runTest {
        val dzEn = SampleData.boEn.copy(id = "dz-en", position = 0)
        val enDz = SampleData.enBo.copy(id = "en-dz", position = 1)
        switcher.onSwitch = { pack ->
            succeed(pack)
            dictionary.capabilities.value = SampleData.capabilities.copy(directions = listOf(dzEn, enDz))
        }
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA))

        vm.next()

        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
        assertThat(vm.state().directions).containsExactly(dzEn, enDz).inOrder()
        assertThat(vm.state().selectedDirection).isEqualTo(dzEn)
    }

    // Failures

    @Test
    fun `writes that fail are recorded and onboarding still finishes`() = runTest {
        val failing = object : UserPreferencesRepository by prefs {
            override suspend fun setDefaultDirectionId(id: String) = throw IOException("disk full")
            override suspend fun setOnboarded(value: Boolean) = throw IOException("disk full")
        }
        val vm = viewModel(failing)

        vm.effects.test {
            vm.pastDictionary()
            vm.onEvent(OnboardingEvent.SelectDirection(SampleData.enBo.id))
            vm.next()
            assertThat(vm.state().step).isEqualTo(OnboardingStep.READING)
            vm.onEvent(OnboardingEvent.SetTextSize(AppTextSize.LARGE))
            vm.next()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(errors.recorded.map { it.message }).containsExactly("disk full", "disk full")
        // The writes that worked are kept.
        assertThat(prefs.prefs.value.textSize).isEqualTo(AppTextSize.LARGE)
    }

    // Process death

    @Test
    fun `the step and the choices not yet stored survive process death`() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        vm.pastDictionary()
        vm.onEvent(OnboardingEvent.SelectDirection(SampleData.enBo.id))
        vm.next()
        vm.onEvent(OnboardingEvent.SetTextSize(AppTextSize.LARGE))
        vm.onEvent(OnboardingEvent.SetLineSpacing(LineSpacing.MAX))

        // What the system restores into the view model it recreates.
        val restored =
            viewModel(savedState = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) }))

        with(restored.state()) {
            assertThat(step).isEqualTo(OnboardingStep.READING)
            assertThat(selectedDirection).isEqualTo(SampleData.enBo)
            assertThat(textSize).isEqualTo(AppTextSize.LARGE)
            assertThat(lineSpacing).isEqualTo(LineSpacing.MAX)
        }
        // The reading step's choices are still unsaved until Continue.
        assertThat(prefs.prefs.value.textSize).isEqualTo(AppTextSize.DEFAULT)
    }

    // Steps

    @Test
    fun `three steps while accounts are off, starting on the dictionary step`() = runTest {
        assumeFalse(Features.ACCOUNTS)

        val state = viewModel().state()

        assertThat(state.stepCount).isEqualTo(3)
        assertThat(state.step).isEqualTo(OnboardingStep.DICTIONARY)
    }

    @Test
    fun `the last step hides Skip`() = runTest {
        val vm = viewModel()
        assertThat(vm.state().showSkip).isTrue()

        repeat(vm.state().stepCount - 1) { vm.next() }

        assertThat(vm.state().step).isEqualTo(vm.state().stepCount)
        assertThat(vm.state().isLastStep).isTrue()
        assertThat(vm.state().showSkip).isFalse()
    }

    @Test
    fun `Back returns to the previous step and keeps what was chosen there`() = runTest {
        val vm = viewModel()
        vm.pastDictionary()
        vm.onEvent(OnboardingEvent.SelectDirection(SampleData.enBo.id))
        vm.next()
        vm.onEvent(OnboardingEvent.SetTextSize(AppTextSize.LARGE))

        vm.onEvent(OnboardingEvent.Back)

        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
        assertThat(vm.state().selectedDirection).isEqualTo(SampleData.enBo)
        assertThat(vm.state().textSize).isEqualTo(AppTextSize.LARGE)
    }

    @Test
    fun `Back on the first step stays there`() = runTest {
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.Back)

        assertThat(vm.state().step).isEqualTo(OnboardingStep.DICTIONARY)
    }

    // Initial values

    @Test
    fun `the stored default direction is selected and the sliders start from the stored settings`() = runTest {
        prefs.prefs.value = UserPrefs(defaultDirectionId = SampleData.enBo.id, textSize = AppTextSize.LARGE, lineSpacing = 2.1f)

        val state = viewModel().state()

        assertThat(state.directions).containsExactly(SampleData.boEn, SampleData.boBo, SampleData.enBo).inOrder()
        assertThat(state.selectedDirection).isEqualTo(SampleData.enBo)
        assertThat(state.textSize).isEqualTo(AppTextSize.LARGE)
        assertThat(state.lineSpacing).isEqualTo(2.1f)
    }

    @Test
    fun `a stored direction that is not visible falls back to the first visible one`() = runTest {
        // bo-en emptied by a data release: bo-bo (position 1) is now the first visible direction.
        dictionary.capabilities.value = SampleData.capabilities.copy(
            directions = listOf(SampleData.enBo, SampleData.boEn.copy(entryCount = 0), SampleData.boBo),
        )
        prefs.prefs.value = UserPrefs(defaultDirectionId = SampleData.boEn.id)

        val state = viewModel().state()

        assertThat(state.directions).containsExactly(SampleData.boBo, SampleData.enBo).inOrder()
        assertThat(state.selectedDirection).isEqualTo(SampleData.boBo)
    }

    @Test
    fun `a stored direction missing from the data falls back to the first visible one`() = runTest {
        prefs.prefs.value = UserPrefs(defaultDirectionId = "zh-bo")

        assertThat(viewModel().state().selectedDirection).isEqualTo(SampleData.boEn)
    }

    @Test
    fun `with no visible direction nothing is selected and Continue moves on without storing a direction`() =
        runTest {
            dictionary.capabilities.value = SampleData.capabilities.copy(
                directions = listOf(SampleData.boEn.copy(entryCount = 0)),
            )
            prefs.prefs.value = UserPrefs(defaultDirectionId = "zh-bo")
            prefs.setDefaultDirectionId("zh-bo") // the fake restores a pack's own direction on every pack switch
            val vm = viewModel()
            vm.pastDictionary()

            assertThat(vm.state().directions).isEmpty()
            assertThat(vm.state().selectedDirection).isNull()

            vm.next()

            assertThat(vm.state().step).isEqualTo(OnboardingStep.READING)
            assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo("zh-bo")
        }

    @Test
    fun `choices change the state but are not stored before Continue`() = runTest {
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.SelectDirection(SampleData.boBo.id))
        vm.onEvent(OnboardingEvent.SetTextSize(AppTextSize.LARGEST))
        vm.onEvent(OnboardingEvent.SetLineSpacing(2.3f))

        assertThat(vm.state().selectedDirection).isEqualTo(SampleData.boBo)
        assertThat(vm.state().textSize).isEqualTo(AppTextSize.LARGEST)
        assertThat(vm.state().lineSpacing).isEqualTo(2.3f)
        assertThat(prefs.prefs.value).isEqualTo(UserPrefs())
    }

    @Test
    fun `selecting a direction that is not visible is ignored`() = runTest {
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.SelectDirection("zh-bo"))

        assertThat(vm.state().selectedDirection).isEqualTo(SampleData.boEn)
    }

    // Continue and Skip

    @Test
    fun `Continue stores each step's values as it leaves the step, then finishes`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.pastDictionary()
            vm.onEvent(OnboardingEvent.SelectDirection(SampleData.enBo.id))
            vm.next()

            assertThat(vm.state().step).isEqualTo(OnboardingStep.READING)
            assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo(SampleData.enBo.id)
            assertThat(prefs.prefs.value.onboarded).isFalse()

            vm.onEvent(OnboardingEvent.SetTextSize(AppTextSize.LARGE))
            vm.onEvent(OnboardingEvent.SetLineSpacing(2.1f))
            assertThat(prefs.prefs.value.textSize).isEqualTo(AppTextSize.DEFAULT)
            expectNoEvents()

            vm.next()

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
            assertThat(prefs.prefs.value.textSize).isEqualTo(AppTextSize.LARGE)
            assertThat(prefs.prefs.value.lineSpacing).isEqualTo(2.1f)
            assertThat(prefs.prefs.value.onboarded).isTrue()
        }
    }

    @Test
    fun `Skip stores the selection and reading settings shown, marks onboarding done and finishes`() = runTest {
        // The stored direction is gone, so the first visible one is shown selected and is what Skip stores.
        prefs.prefs.value = UserPrefs(defaultDirectionId = "zh-bo", textSize = AppTextSize.LARGE, lineSpacing = 2.1f)
        prefs.setDefaultDirectionId("zh-bo") // the fake restores a pack's own direction on every pack switch
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Skip)

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(prefs.prefs.value).isEqualTo(
            UserPrefs(
                onboarded = true,
                dictionaryPackChosen = true,
                defaultDirectionId = SampleData.boEn.id,
                textSize = AppTextSize.LARGE,
                lineSpacing = 2.1f,
            ),
        )
    }

    @Test
    fun `Skip also stores what was chosen on a later step before going back`() = runTest {
        val vm = viewModel()
        vm.next()
        vm.next()
        vm.onEvent(OnboardingEvent.SetTextSize(AppTextSize.SMALL))
        vm.onEvent(OnboardingEvent.SetLineSpacing(LineSpacing.MIN))
        vm.onEvent(OnboardingEvent.Back)

        vm.onEvent(OnboardingEvent.Skip)

        assertThat(prefs.prefs.value.textSize).isEqualTo(AppTextSize.SMALL)
        assertThat(prefs.prefs.value.lineSpacing).isEqualTo(LineSpacing.MIN)
        assertThat(prefs.prefs.value.onboarded).isTrue()
    }

    @Test
    fun `Finished is emitted exactly once`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Skip)
            vm.onEvent(OnboardingEvent.Skip)
            vm.next()
            vm.onEvent(OnboardingEvent.ContinueWithoutAccount(vm.state().step))

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
            expectNoEvents()
        }
    }

    @Test
    fun `a double tap on the dictionary step's Continue moves on one step and does not finish`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            // Both taps were made on step 1's button; the second lands after the step moved on.
            vm.onEvent(OnboardingEvent.Next(OnboardingStep.DICTIONARY))
            vm.onEvent(OnboardingEvent.Next(OnboardingStep.DICTIONARY))

            assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
            assertThat(switcher.requests).hasSize(1)
            expectNoEvents()
        }
        assertThat(prefs.prefs.value.onboarded).isFalse()
    }

    @Test
    fun `a double tap on the last Continue finishes once`() = runTest {
        val vm = viewModel()
        repeat(vm.state().stepCount - 1) { vm.next() }
        val last = vm.state().step

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Next(last))
            vm.onEvent(OnboardingEvent.Next(last))

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
            expectNoEvents()
        }
    }

    // Accounts (off in release 1)

    @Test
    fun `Sign in does nothing while accounts are off`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.SignIn)

            expectNoEvents()
        }
        assertThat(vm.state().step).isEqualTo(OnboardingStep.DICTIONARY)
        assertThat(prefs.prefs.value.onboarded).isFalse()
    }

    @Test
    fun `Continue without account on the last step finishes like Continue`() = runTest {
        val vm = viewModel()
        repeat(vm.state().stepCount - 1) { vm.next() }

        vm.effects.test {
            vm.onEvent(OnboardingEvent.ContinueWithoutAccount(vm.state().step))

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(prefs.prefs.value.onboarded).isTrue()
    }
}
