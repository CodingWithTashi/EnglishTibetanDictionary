package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.ExtremeScale
import com.kharagedition.tibetandictionary.ui.components.ExtremeScalePreview
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.OutlinedButton
import com.kharagedition.tibetandictionary.ui.components.PageIndicator
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.flow.Flow

/** Top bar with the page indicator and Skip. */
private val TopBarHeight = 56.dp

/** The account step only ships with accounts, which make onboarding three steps long. */
private const val STEP_COUNT_WITH_ACCOUNTS = 3

/** Space between the scrolling step content and the bottom buttons; 10 between stacked buttons. */
private val ActionsTop = 16.dp
private val ActionGap = 10.dp

/** The "sign in later" note sits 4 further below the last button. */
private val NoteTop = 4.dp

internal const val ONBOARDING_SCREEN_TAG = "onboarding_screen"

/** First-run setup (2b/2c, plus 2d with accounts); [onFinished] moves on to the main scaffold. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val viewModel = hiltViewModel<OnboardingViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingEffects(viewModel.effects, onFinished)
    val loaded = state
    if (loaded != null) {
        OnboardingScreen(loaded, viewModel::onEvent)
    } else {
        // Prefs and capabilities arrive within a frame; show the background instead of default values.
        Box(
            Modifier
                .fillMaxSize()
                .background(DictTheme.colors.background),
        )
    }
}

/**
 * Performs [effects] only while this destination is resumed: the nav host ignores callbacks during a transition,
 * so a [OnboardingEffect.Finished] sent then waits in the channel instead of being lost.
 */
@Composable
private fun OnboardingEffects(effects: Flow<OnboardingEffect>, onFinished: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            effects.collect { effect ->
                when (effect) {
                    OnboardingEffect.Finished -> finish()
                }
            }
        }
    }
}

/**
 * The stateless onboarding screen: page indicator and Skip (not on the last step), the current step scrolling
 * between them and the bottom buttons. System back on a later step returns to the previous one; on the first step
 * it is left to the system.
 *
 * Test tags: `onboarding_screen`, `onboarding_skip`, `onboarding_continue`, plus the steps' tags.
 */
@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler(enabled = state.step > OnboardingStep.DIRECTION) { onEvent(OnboardingEvent.Back) }
    val padding = DictTheme.dimens.onboardingPadding
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .testTag(ONBOARDING_SCREEN_TAG)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = padding, end = padding, bottom = padding),
    ) {
        OnboardingTopBar(state, onEvent)
        // Keyed so each step starts scrolled to the top.
        key(state.step) {
            val stepModifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
            when (state.step) {
                OnboardingStep.DIRECTION -> DirectionStep(state, onEvent, stepModifier)
                OnboardingStep.READING -> ReadingStep(state, onEvent, stepModifier)
                else -> AccountStep(stepModifier)
            }
        }
        OnboardingActions(state, onEvent, Modifier.padding(top = ActionsTop))
    }
}

/** The page indicator and, on every step but the last, Skip at the end. */
@Composable
private fun OnboardingTopBar(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PageIndicator(count = state.stepCount, current = state.step - 1)
        if (state.showSkip) {
            TextButton(
                stringResource(R.string.skip),
                onClick = { onEvent(OnboardingEvent.Skip) },
                modifier = Modifier.testTag("onboarding_skip"),
                size = ButtonSize.Small,
            )
        }
    }
}

/** Continue; on the account step, sign-in and continuing without an account with equal weight, and a note. */
@Composable
private fun OnboardingActions(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ActionGap)) {
        if (state.step == OnboardingStep.ACCOUNT) {
            OutlinedButton(
                stringResource(R.string.sign_in_google),
                onClick = { onEvent(OnboardingEvent.SignIn) },
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Cta,
            )
            FilledButton(
                stringResource(R.string.continue_without_account),
                onClick = { onEvent(OnboardingEvent.ContinueWithoutAccount(state.step)) },
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Cta,
            )
            MixedText(
                stringResource(R.string.sign_in_later_note),
                DictTheme.type.tiny,
                DictTheme.type.tibetanInline14,
                Modifier
                    .fillMaxWidth()
                    .padding(top = NoteTop),
                color = DictTheme.colors.muted,
                textAlign = TextAlign.Center,
            )
        } else {
            FilledButton(
                stringResource(R.string.continue_),
                onClick = { onEvent(OnboardingEvent.Next(state.step)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_continue"),
                size = ButtonSize.Cta,
            )
        }
    }
}

/** Preview state: the 2b mockup (three directions, Tibetan → English selected, default reading settings). */
private val previewState = OnboardingUiState(
    step = OnboardingStep.DIRECTION,
    directions = PreviewSamples.directions,
    selectedDirection = PreviewSamples.boEn,
)

@PreviewLightDark
@BoPreview
@Composable
private fun OnboardingDirectionPreview() {
    ComponentPreview(edgeToEdge = true) { OnboardingScreen(previewState, onEvent = {}) }
}

@PreviewLightDark
@BoPreview
@Composable
private fun OnboardingReadingPreview() {
    ComponentPreview(edgeToEdge = true) { OnboardingScreen(previewState.copy(step = OnboardingStep.READING), onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun OnboardingAccountPreview() {
    ComponentPreview(edgeToEdge = true) {
        OnboardingScreen(previewState.copy(step = OnboardingStep.ACCOUNT, stepCount = STEP_COUNT_WITH_ACCOUNTS), onEvent = {})
    }
}

@ExtremeScalePreview
@Composable
private fun OnboardingExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        OnboardingScreen(
            previewState.copy(step = OnboardingStep.READING, textSize = AppTextSize.LARGEST, lineSpacing = LineSpacing.MAX),
            onEvent = {},
        )
    }
}
