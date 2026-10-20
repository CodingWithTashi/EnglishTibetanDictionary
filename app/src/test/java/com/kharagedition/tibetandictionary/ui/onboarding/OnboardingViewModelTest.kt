package com.kharagedition.tibetandictionary.ui.onboarding

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    /** A view model whose state is collected for the whole test, as the screen does. */
    private fun TestScope.viewModel(
        prefs: UserPreferencesRepository = this@OnboardingViewModelTest.prefs,
    ): OnboardingViewModel {
        val vm = OnboardingViewModel(prefs, dictionary, errors)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun OnboardingViewModel.state(): OnboardingUiState = checkNotNull(uiState.value)

    /** Continue on the step shown now. */
    private fun OnboardingViewModel.next() = onEvent(OnboardingEvent.Next(state().step))

    // Failures

    @Test
    fun `writes that fail are recorded and onboarding still finishes`() = runTest {
        val failing = object : UserPreferencesRepository by prefs {
            override suspend fun setDefaultDirectionId(id: String) = throw IOException("disk full")
            override suspend fun setOnboarded(value: Boolean) = throw IOException("disk full")
        }
        val vm = viewModel(failing)

        vm.effects.test {
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

    // Steps

    @Test
    fun `two steps while accounts are off, starting on the direction step`() = runTest {
        assumeFalse(Features.ACCOUNTS)

        val state = viewModel().state()

        assertThat(state.stepCount).isEqualTo(2)
        assertThat(state.step).isEqualTo(OnboardingStep.DIRECTION)
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

        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
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
            val vm = viewModel()

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
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Skip)

            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(prefs.prefs.value).isEqualTo(
            UserPrefs(
                onboarded = true,
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
    fun `a double tap on step 1's Continue moves on one step and does not finish`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            // Both taps were made on step 1's button; the second lands after the step moved on.
            vm.onEvent(OnboardingEvent.Next(OnboardingStep.DIRECTION))
            vm.onEvent(OnboardingEvent.Next(OnboardingStep.DIRECTION))

            assertThat(vm.state().step).isEqualTo(OnboardingStep.READING)
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
        assertThat(vm.state().step).isEqualTo(OnboardingStep.DIRECTION)
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
