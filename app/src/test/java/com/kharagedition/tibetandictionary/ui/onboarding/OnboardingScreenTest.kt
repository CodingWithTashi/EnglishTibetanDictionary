package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val prefs = FakeUserPreferencesRepository()
    private val events = mutableListOf<OnboardingEvent>()

    private val directionStep = OnboardingUiState(
        step = OnboardingStep.DIRECTION,
        stepCount = 2,
        directions = SampleData.directions,
        selectedDirection = SampleData.boEn,
    )

    /** The stateless screen showing [state]; events are recorded in [events]. */
    private fun show(state: OnboardingUiState) {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                OnboardingScreen(state, onEvent = { events += it })
            }
        }
    }

    /** The screen on a real view model over the fakes, as the route shows it. */
    private fun showWithViewModel() {
        val viewModel = OnboardingViewModel(prefs, FakeDictionaryRepository())
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                val state by viewModel.uiState.collectAsState()
                state?.let { OnboardingScreen(it, viewModel::onEvent) }
            }
        }
    }

    /** Taps a pinned button (top bar or bottom actions, outside the scrolling step). */
    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
    }

    /** Scrolls the step to a direction card and taps it. */
    private fun tapDirection(id: String) {
        compose.onNodeWithTag("onboarding_direction_$id").performScrollTo().performClick()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `step 1 offers a card per visible direction and Continue moves on to step 2`() {
        showWithViewModel()

        compose.onNodeWithTag(ONBOARDING_SCREEN_TAG).assertExists()
        compose.onNodeWithTag("onboarding_step_1").assertExists()
        compose.onNodeWithText("བཀྲ་ཤིས་བདེ་ལེགས།").assertIsDisplayed()
        compose.onNodeWithText("Which way do you look up words most?").assertIsDisplayed()
        compose.onNodeWithText("Tibetan definitions").assertExists()
        compose.onNodeWithTag("onboarding_direction_bo-en").assertIsSelected()
        compose.onNodeWithTag("onboarding_direction_bo-bo").assertIsNotSelected()
        compose.onNodeWithTag("onboarding_direction_en-bo").assertIsNotSelected()

        tapDirection(SampleData.enBo.id)
        compose.onNodeWithTag("onboarding_direction_en-bo").assertIsSelected()
        compose.onNodeWithTag("onboarding_direction_bo-en").assertIsNotSelected()

        tap("onboarding_continue")

        compose.onNodeWithTag("onboarding_step_2").assertExists()
        compose.onNodeWithTag("onboarding_step_1").assertDoesNotExist()
        compose.onNodeWithText("Comfortable to read?").assertIsDisplayed()
        assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo(SampleData.enBo.id)
    }

    @Test
    fun `Skip is offered on step 1 only`() {
        showWithViewModel()
        compose.onNodeWithTag("onboarding_skip").assertIsDisplayed()

        tap("onboarding_continue")

        compose.onNodeWithTag("onboarding_step_2").assertExists()
        compose.onNodeWithTag("onboarding_skip").assertDoesNotExist()
    }

    @Test
    fun `Skip, Continue and the direction cards send their events`() {
        show(directionStep)

        tapDirection(SampleData.boBo.id)
        tap("onboarding_continue")
        tap("onboarding_skip")

        assertThat(events).containsExactly(
            OnboardingEvent.SelectDirection(SampleData.boBo.id),
            OnboardingEvent.Next,
            OnboardingEvent.Skip,
        ).inOrder()
    }

    @Test
    fun `step 2 shows the reading settings and its sliders change them`() {
        show(directionStep.copy(step = OnboardingStep.READING, textSize = AppTextSize.LARGE, lineSpacing = 2.1f))

        compose.onNodeWithTag("onboarding_step_2").assertExists()
        compose.onNodeWithText("Tibetan is shown larger than English so stacked letters stay clear.").assertIsDisplayed()
        compose.onNodeWithText("Text size").assertExists()
        compose.onNodeWithText("Large").assertExists()
        compose.onNodeWithText("Line spacing").assertExists()
        compose.onNodeWithText("2.1").assertExists()

        val canSetProgress = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        compose.onNode(canSetProgress and hasAnyAncestor(hasTestTag("onboarding_text_size")))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(AppTextSize.LARGEST.ordinal.toFloat()) }
        compose.onNode(canSetProgress and hasTestTag("onboarding_line_spacing"))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2.3f) }

        assertThat(events).containsExactly(
            OnboardingEvent.SetTextSize(AppTextSize.LARGEST),
            OnboardingEvent.SetLineSpacing(2.3f),
        ).inOrder()
    }

    @Test
    fun `back on step 2 returns to step 1`() {
        show(directionStep.copy(step = OnboardingStep.READING))

        pressBack()

        assertThat(events).containsExactly(OnboardingEvent.Back)
        assertThat(compose.activity.isFinishing).isFalse()
    }

    @Test
    fun `back on step 1 is left to the system`() {
        show(directionStep)

        pressBack()

        assertThat(events).isEmpty()
        assertThat(compose.activity.isFinishing).isTrue()
    }

    @Test
    fun `the account step offers sign-in or continuing without an account, and no Skip`() {
        show(directionStep.copy(step = OnboardingStep.ACCOUNT, stepCount = 3))

        compose.onNodeWithTag("onboarding_step_3").assertExists()
        compose.onNodeWithText("Keep your words on every device").assertIsDisplayed()
        compose.onNodeWithText("You can sign in later from Profile.").assertExists()
        compose.onNodeWithTag("onboarding_skip").assertDoesNotExist()

        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Continue without account").performClick()

        assertThat(events).containsExactly(OnboardingEvent.SignIn, OnboardingEvent.ContinueWithoutAccount).inOrder()
    }
}
