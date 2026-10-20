package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing

/** The onboarding steps, numbered from 1 in the order they are shown. */
object OnboardingStep {
    /** 2b: the default direction. */
    const val DIRECTION = 1

    /** 2c: text size and line spacing. */
    const val READING = 2

    /** 2d: optional sign-in; only while `Features.ACCOUNTS`. */
    const val ACCOUNT = 3
}

/** How many steps onboarding has: direction and reading, plus sign-in while `Features.ACCOUNTS`. */
internal val STEP_COUNT: Int = if (Features.ACCOUNTS) 3 else 2

/**
 * First-run setup (2b/2c/2d). [step] runs from 1 to [stepCount] (see [OnboardingStep]). [directions] are the
 * visible ones in switcher order; [selectedDirection] is the user's pick, else the stored default when visible,
 * else the first visible direction (null only when no direction has entries). [textSize] and [lineSpacing] start
 * from the stored settings. Nothing is stored until the user leaves a step with Continue or Skip.
 */
@Immutable
data class OnboardingUiState(
    val step: Int = OnboardingStep.DIRECTION,
    val stepCount: Int = STEP_COUNT,
    val directions: List<Direction> = emptyList(),
    val selectedDirection: Direction? = null,
    val textSize: AppTextSize = AppTextSize.DEFAULT,
    val lineSpacing: Float = LineSpacing.DEFAULT,
) {
    val isLastStep: Boolean get() = step >= stepCount

    /** Skip is offered on every step but the last, where Continue already finishes. */
    val showSkip: Boolean get() = !isLastStep
}

/**
 * Buttons that leave a step carry the step they were shown on ([Next.fromStep],
 * [ContinueWithoutAccount.fromStep]); a tap that lands after the step already moved on is ignored, so a double tap
 * cannot skip a step.
 */
sealed interface OnboardingEvent {
    /** Continue: store this step's values and go on, or finish on the last step. */
    data class Next(val fromStep: Int) : OnboardingEvent

    /** Store what this and the later steps show and finish. */
    data object Skip : OnboardingEvent

    /** System back on a later step: return to the previous one. */
    data object Back : OnboardingEvent

    data class SelectDirection(val id: String) : OnboardingEvent
    data class SetTextSize(val size: AppTextSize) : OnboardingEvent
    data class SetLineSpacing(val spacing: Float) : OnboardingEvent

    /** The account step's primary button; that step is the last, so this finishes like [Next]. */
    data class ContinueWithoutAccount(val fromStep: Int) : OnboardingEvent

    data object SignIn : OnboardingEvent
}

sealed interface OnboardingEffect {
    /** Onboarding is stored as done: move on to the main screen. */
    data object Finished : OnboardingEffect
}
