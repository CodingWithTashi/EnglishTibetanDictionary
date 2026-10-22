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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionarySwitcher
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val prefs = FakeUserPreferencesRepository()
    private val events = mutableListOf<OnboardingEvent>()

    private val dictionaryStep = OnboardingUiState(
        step = OnboardingStep.DICTIONARY,
        stepCount = 3,
        directions = SampleData.directions,
        selectedDirection = SampleData.boEn,
    )

    private val directionStep = OnboardingUiState(
        step = OnboardingStep.DIRECTION,
        stepCount = 3,
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
    private fun showWithViewModel(dictionary: FakeDictionaryRepository = FakeDictionaryRepository()) {
        val databases = DictionaryDatabases { error("no database is opened in these tests") }
            .apply { activate(DictionaryPack.TIBETAN) }
        val switcher = FakeDictionarySwitcher { pack ->
            databases.activate(pack)
            runBlocking { prefs.setDictionaryPack(pack) }
        }
        val viewModel = OnboardingViewModel(SavedStateHandle(), prefs, dictionary, switcher, databases, RecordingErrorReporter())
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

    /** Continue past the dictionary step on the view model screen. */
    private fun continuePastDictionary() {
        compose.onNodeWithTag("onboarding_step_1").assertExists()
        tap("onboarding_continue")
        compose.onNodeWithTag("onboarding_step_2").assertExists()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `dictionary step shows both packs and Continue switches to the chosen one`() {
        showWithViewModel()

        compose.onNodeWithTag(ONBOARDING_SCREEN_TAG).assertExists()
        compose.onNodeWithTag("onboarding_step_1").assertExists()
        compose.onNodeWithText("བཀྲ་ཤིས་བདེ་ལེགས།").assertIsDisplayed()
        compose.onNodeWithText("Which dictionary do you want?").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_pack_bo").assertIsSelected()
        compose.onNodeWithTag("onboarding_pack_dz").assertIsNotSelected()
        compose.onNodeWithText("Dzongkha Dictionary", substring = true).assertExists()
        compose.onNodeWithText("Tibetan Dictionary", substring = true).assertExists()

        compose.onNodeWithTag("onboarding_pack_dz").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_pack_dz").assertIsSelected()
        compose.onNodeWithTag("onboarding_pack_bo").assertIsNotSelected()

        tap("onboarding_continue")

        compose.onNodeWithTag("onboarding_step_2").assertExists()
        assertThat(prefs.prefs.value.dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `dictionary step cards and Continue send their events`() {
        show(dictionaryStep)

        compose.onNodeWithTag("onboarding_pack_dz").performScrollTo().performClick()
        tap("onboarding_continue")

        assertThat(events).containsExactly(
            OnboardingEvent.SelectDictionary(DictionaryPack.DZONGKHA),
            OnboardingEvent.Next(OnboardingStep.DICTIONARY),
        ).inOrder()
    }

    @Test
    fun `step 2 offers a card per visible direction and Continue moves on to step 3`() {
        showWithViewModel()
        continuePastDictionary()

        compose.onNodeWithText("Which way do you look up words most?").assertIsDisplayed()
        compose.onNodeWithText("Tibetan definitions").assertExists()
        compose.onNodeWithTag("onboarding_direction_bo-en").assertIsSelected()
        compose.onNodeWithTag("onboarding_direction_bo-bo").assertIsNotSelected()
        compose.onNodeWithTag("onboarding_direction_en-bo").assertIsNotSelected()

        tapDirection(SampleData.enBo.id)
        compose.onNodeWithTag("onboarding_direction_en-bo").assertIsSelected()
        compose.onNodeWithTag("onboarding_direction_bo-en").assertIsNotSelected()

        tap("onboarding_continue")

        compose.onNodeWithTag("onboarding_step_3").assertExists()
        compose.onNodeWithTag("onboarding_step_2").assertDoesNotExist()
        compose.onNodeWithText("Comfortable to read?").assertIsDisplayed()
        assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo(SampleData.enBo.id)
    }

    @Test
    fun `with no visible direction step 1 has no cards and Continue still moves on`() {
        val emptied = SampleData.capabilities.copy(directions = listOf(SampleData.boEn.copy(entryCount = 0)))
        showWithViewModel(FakeDictionaryRepository(capabilities = emptied))
        continuePastDictionary()

        compose.onNodeWithTag("onboarding_step_2").assertExists()
        compose.onAllNodes(isSelectable()).assertCountEquals(0)

        tap("onboarding_continue")

        compose.onNodeWithTag("onboarding_step_3").assertExists()
    }

    @Test
    fun `Skip is offered before the last step only`() {
        showWithViewModel()
        compose.onNodeWithTag("onboarding_skip").assertIsDisplayed()
        continuePastDictionary()
        compose.onNodeWithTag("onboarding_skip").assertIsDisplayed()

        tap("onboarding_continue")

        compose.onNodeWithTag("onboarding_step_3").assertExists()
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
            OnboardingEvent.Next(OnboardingStep.DIRECTION),
            OnboardingEvent.Skip,
        ).inOrder()
    }

    @Test
    fun `step 3 shows the reading settings and its sliders change them`() {
        show(directionStep.copy(step = OnboardingStep.READING, textSize = AppTextSize.LARGE, lineSpacing = 2.1f))

        compose.onNodeWithTag("onboarding_step_3").assertExists()
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
    fun `back on step 3 returns to step 2`() {
        show(directionStep.copy(step = OnboardingStep.READING))

        pressBack()

        assertThat(events).containsExactly(OnboardingEvent.Back)
        assertThat(compose.activity.isFinishing).isFalse()
    }

    @Test
    fun `back on the direction step returns to the dictionary step`() {
        show(directionStep)

        pressBack()

        assertThat(events).containsExactly(OnboardingEvent.Back)
        assertThat(compose.activity.isFinishing).isFalse()
    }

    @Test
    fun `back on step 1 is left to the system`() {
        show(dictionaryStep)

        pressBack()

        assertThat(events).isEmpty()
        assertThat(compose.activity.isFinishing).isTrue()
    }

    @Test
    fun `the account step offers sign-in or continuing without an account, and no Skip`() {
        show(directionStep.copy(step = OnboardingStep.ACCOUNT, stepCount = 4))

        compose.onNodeWithTag("onboarding_step_4").assertExists()
        compose.onNodeWithText("Keep your words on every device").assertIsDisplayed()
        compose.onNodeWithText("You can sign in later from Profile.").assertExists()
        compose.onNodeWithTag("onboarding_skip").assertDoesNotExist()

        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Continue without account").performClick()

        assertThat(events).containsExactly(
            OnboardingEvent.SignIn,
            OnboardingEvent.ContinueWithoutAccount(OnboardingStep.ACCOUNT),
        ).inOrder()
    }
}
