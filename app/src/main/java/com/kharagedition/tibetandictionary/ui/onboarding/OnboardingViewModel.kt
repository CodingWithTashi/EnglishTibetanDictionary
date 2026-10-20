package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * First-run setup: the default direction, then text size and line spacing (then sign-in, with accounts). Choices
 * start from the stored [prefs] and the visible directions; each step's values are stored when the user leaves it
 * with Continue, and Skip stores what the remaining steps show. The last step also stores onboarding as done and
 * sends [OnboardingEffect.Finished], once. [uiState] is null until prefs and capabilities load.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    dictionary: DictionaryRepository,
) : ViewModel() {

    /** Where the user is and what they changed; null choices still show the stored value. */
    private data class Draft(
        val step: Int = OnboardingStep.DIRECTION,
        val directionId: String? = null,
        val textSize: AppTextSize? = null,
        val lineSpacing: Float? = null,
    )

    private val draft = MutableStateFlow(Draft())

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
            OnboardingEvent.Next, OnboardingEvent.ContinueWithoutAccount -> next(state)
            OnboardingEvent.Skip -> finish(state)
            OnboardingEvent.Back -> draft.update { it.copy(step = (it.step - 1).coerceAtLeast(OnboardingStep.DIRECTION)) }
            is OnboardingEvent.SelectDirection -> if (state.directions.any { it.id == event.id }) {
                draft.update { it.copy(directionId = event.id) }
            }
            is OnboardingEvent.SetTextSize -> draft.update { it.copy(textSize = event.size) }
            is OnboardingEvent.SetLineSpacing -> draft.update { it.copy(lineSpacing = LineSpacing.clamp(event.spacing)) }
            // Accounts ship behind Features.ACCOUNTS; there is nothing to sign in to yet.
            OnboardingEvent.SignIn -> Unit
        }
    }

    private fun next(state: OnboardingUiState) {
        if (state.isLastStep) {
            finish(state)
        } else {
            viewModelScope.launch { prefs.store(state.step, state) }
            draft.update { it.copy(step = state.step + 1) }
        }
    }

    /** Stores the current and later steps' values and onboarding as done, then sends [OnboardingEffect.Finished]. */
    private fun finish(state: OnboardingUiState) {
        finished = true
        viewModelScope.launch {
            for (step in state.step..state.stepCount) prefs.store(step, state)
            prefs.setOnboarded(true)
            effectChannel.send(OnboardingEffect.Finished)
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

        /** Direction and reading; the sign-in step only with accounts. */
        val STEP_COUNT = if (Features.ACCOUNTS) OnboardingStep.ACCOUNT else OnboardingStep.READING

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
