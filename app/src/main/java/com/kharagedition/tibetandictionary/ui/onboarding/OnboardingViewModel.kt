package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * First-run setup: the default direction, then text size and line spacing (then sign-in, with accounts). Choices
 * start from the stored [prefs] and the visible directions; each step's values are stored when the user leaves it
 * with Continue, and Skip stores what the remaining steps show. The last step also stores onboarding as done and
 * sends [OnboardingEffect.Finished], once. [uiState] is null until prefs and capabilities load. A write that fails
 * (DataStore's IOException) is recorded through [errors] and onboarding carries on: the choices still apply for this
 * session, and the user is not stuck on the first-run screens. The step and the choices not yet stored live in
 * [savedState], so a process death mid-setup comes back to the same step.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val prefs: UserPreferencesRepository,
    dictionary: DictionaryRepository,
    private val errors: ErrorReporter,
) : ViewModel() {

    /** Where the user is and what they changed; null choices still show the stored value. */
    private data class Draft(
        val step: Int = OnboardingStep.DIRECTION,
        val directionId: String? = null,
        val textSize: AppTextSize? = null,
        val lineSpacing: Float? = null,
    )

    private val draft = MutableStateFlow(restoredDraft())

    /** Set by the first finish, so a double tap or a late Skip cannot finish twice. */
    private var finished = false

    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<OnboardingUiState?> =
        combine(prefs.prefs, dictionary.capabilities, draft) { prefs, capabilities, draft ->
            state(prefs, capabilities, draft)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onEvent(event: OnboardingEvent) {
        val state = uiState.value ?: return
        if (finished) return
        when (event) {
            is OnboardingEvent.Next -> next(state, event.fromStep)
            is OnboardingEvent.ContinueWithoutAccount -> next(state, event.fromStep)
            OnboardingEvent.Skip -> finish(state)
            OnboardingEvent.Back -> updateDraft { it.copy(step = (it.step - 1).coerceAtLeast(OnboardingStep.DIRECTION)) }
            is OnboardingEvent.SelectDirection -> if (state.directions.any { it.id == event.id }) {
                updateDraft { it.copy(directionId = event.id) }
            }
            is OnboardingEvent.SetTextSize -> updateDraft { it.copy(textSize = event.size) }
            is OnboardingEvent.SetLineSpacing -> updateDraft { it.copy(lineSpacing = LineSpacing.clamp(event.spacing)) }
            // Accounts ship behind Features.ACCOUNTS; there is nothing to sign in to yet.
            OnboardingEvent.SignIn -> Unit
        }
    }

    /** Leaves [fromStep]; ignored once the user has moved on from it (a double tap on Continue). */
    private fun next(state: OnboardingUiState, fromStep: Int) {
        if (fromStep != state.step) return
        if (state.isLastStep) {
            finish(state)
        } else {
            viewModelScope.launch { recorded { prefs.store(state.step, state) } }
            updateDraft { it.copy(step = state.step + 1) }
        }
    }

    /** Stores the current and later steps' values and onboarding as done, then sends [OnboardingEffect.Finished]. */
    private fun finish(state: OnboardingUiState) {
        finished = true
        viewModelScope.launch {
            for (step in state.step..state.stepCount) recorded { prefs.store(step, state) }
            recorded { prefs.setOnboarded(true) }
            effectChannel.send(OnboardingEffect.Finished)
        }
    }

    /** Applies [transform] to the draft and keeps the result in [savedState]. */
    private fun updateDraft(transform: (Draft) -> Draft) {
        val updated = draft.updateAndGet(transform)
        savedState[KEY_STEP] = updated.step
        savedState[KEY_DIRECTION] = updated.directionId
        savedState[KEY_TEXT_SIZE] = updated.textSize?.name
        savedState[KEY_LINE_SPACING] = updated.lineSpacing
    }

    /** The draft kept in [savedState] by a view model the system killed, or a fresh one. */
    private fun restoredDraft() = Draft(
        step = (savedState.get<Int>(KEY_STEP) ?: OnboardingStep.DIRECTION).coerceIn(OnboardingStep.DIRECTION, STEP_COUNT),
        directionId = savedState.get<String>(KEY_DIRECTION),
        textSize = savedState.get<String>(KEY_TEXT_SIZE)?.let { name -> AppTextSize.entries.firstOrNull { it.name == name } },
        lineSpacing = savedState.get<Float>(KEY_LINE_SPACING),
    )

    /** Runs [block]; a failure is recorded and swallowed, so onboarding continues. Cancellation still propagates. */
    private suspend fun recorded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.record(t)
        }
    }

    private fun state(prefs: UserPrefs, capabilities: DictionaryCapabilities, draft: Draft): OnboardingUiState {
        val visible = capabilities.directions.filter { it.entryCount > 0 }.sortedBy(Direction::position)
        val selectedId = draft.directionId ?: prefs.defaultDirectionId
        return OnboardingUiState(
            step = draft.step,
            stepCount = STEP_COUNT,
            directions = visible,
            // The stored default may be hidden or gone after a data release: fall back to the first visible one.
            selectedDirection = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull(),
            textSize = draft.textSize ?: prefs.textSize,
            lineSpacing = draft.lineSpacing ?: prefs.lineSpacing,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** [savedState] keys of the draft. */
        const val KEY_STEP = "onboarding_step"
        const val KEY_DIRECTION = "onboarding_direction"
        const val KEY_TEXT_SIZE = "onboarding_text_size"
        const val KEY_LINE_SPACING = "onboarding_line_spacing"

        /** Stores what [step] of [state] sets; the account step stores nothing. */
        suspend fun UserPreferencesRepository.store(step: Int, state: OnboardingUiState) {
            when (step) {
                OnboardingStep.DIRECTION -> state.selectedDirection?.let { setDefaultDirectionId(it.id) }
                OnboardingStep.READING -> {
                    setTextSize(state.textSize)
                    setLineSpacing(state.lineSpacing)
                }
            }
        }
    }
}
