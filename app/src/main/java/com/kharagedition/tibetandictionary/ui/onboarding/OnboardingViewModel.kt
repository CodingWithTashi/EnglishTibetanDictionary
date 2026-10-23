package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
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
 * First-run setup: the dictionary pack, the default direction, then text size and line spacing (then sign-in, with
 * accounts). Choices start from the stored [prefs] and the visible directions; each step's values are stored when the
 * user leaves it with Continue, and Skip stores what the remaining steps show. Storing the dictionary step runs the
 * pack switch through [switcher] and checks that [databases] then has that pack active (the switcher also ends idle
 * when it rejects a request or is cancelled); a switch that did not take stays on the step (the switching overlay shows
 * a failed one; a rejected or cancelled one is recorded through [errors]), so Continue never moves on, and Skip never
 * finishes, without the chosen dictionary. The last step also stores onboarding as done and sends
 * [OnboardingEffect.Finished], once. [uiState] is null until prefs and capabilities load. A preferences write that
 * fails (DataStore's IOException) is recorded through [errors] and onboarding carries on: the choices still apply
 * for this session, and the user is not stuck on the first-run screens. The step and the choices not yet stored live in [savedState], so a process death mid-setup comes back to
 * the same step.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val prefs: UserPreferencesRepository,
    dictionary: DictionaryRepository,
    private val switcher: DictionarySwitcher,
    private val databases: DictionaryDatabases,
    private val errors: ErrorReporter,
) : ViewModel() {

    /** Where the user is and what they changed; null choices still show the stored value. */
    private data class Draft(
        val step: Int = OnboardingStep.DICTIONARY,
        val packId: String? = null,
        val directionId: String? = null,
        val textSize: AppTextSize? = null,
        val lineSpacing: Float? = null,
    )

    private val draft = MutableStateFlow(restoredDraft())

    /** Set by the first finish, so a double tap or a late Skip cannot finish twice; reset if its switch fails. */
    private var finished = false

    /** True while Continue stores a step. A second Continue, a Skip or a Back in that time is ignored. */
    private var advancing = false

    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<OnboardingUiState?> =
        combine(prefs.prefs, dictionary.capabilities, draft, switcher.state) { prefs, capabilities, draft, switch ->
            state(prefs, capabilities, draft, switching = switch !is SwitchState.Idle)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onEvent(event: OnboardingEvent) {
        val state = uiState.value ?: return
        if (finished) return
        when (event) {
            is OnboardingEvent.Next -> next(state, event.fromStep)
            is OnboardingEvent.ContinueWithoutAccount -> next(state, event.fromStep)
            OnboardingEvent.Skip -> finish(state)
            OnboardingEvent.Back -> if (!advancing) {
                updateDraft { it.copy(step = (it.step - 1).coerceAtLeast(OnboardingStep.DICTIONARY)) }
            }
            // A different pack makes the direction picked for the old one meaningless.
            is OnboardingEvent.SelectDictionary -> if (!advancing && event.pack in state.packs) {
                updateDraft {
                    if (event.pack == state.selectedPack) it else it.copy(packId = event.pack.id, directionId = null)
                }
            }
            is OnboardingEvent.SelectDirection -> if (state.directions.any { it.id == event.id }) {
                updateDraft { it.copy(directionId = event.id) }
            }
            is OnboardingEvent.SetTextSize -> updateDraft { it.copy(textSize = event.size) }
            is OnboardingEvent.SetLineSpacing -> updateDraft { it.copy(lineSpacing = LineSpacing.clamp(event.spacing)) }
            // The sign-in step ships behind Features.ONBOARDING_SIGN_IN; sign-in is offered after translating.
            OnboardingEvent.SignIn -> Unit
        }
    }

    /** Leaves [fromStep]; ignored once the user has moved on from it (a double tap on Continue). */
    private fun next(state: OnboardingUiState, fromStep: Int) {
        if (fromStep != state.step) return
        if (state.isLastStep) {
            finish(state)
        } else {
            viewModelScope.launch {
                if (advancing) return@launch
                advancing = true
                try {
                    // A failed preferences write is only recorded (the choice still applies this session); a dictionary
                    // switch that did not take keeps the user on the step.
                    val stored = store(state.step, state)
                    if (stored || state.step != OnboardingStep.DICTIONARY) {
                        updateDraft { it.copy(step = state.step + 1) }
                    }
                } finally {
                    advancing = false
                }
            }
        }
    }

    /**
     * Stores the current and later steps' values and onboarding as done, then sends [OnboardingEffect.Finished].
     * Ignored while a Continue is storing (its switch is the one running; a Skip tapped meanwhile must not start
     * another, and the user can tap again). If the dictionary step's switch does not take, nothing else is stored,
     * onboarding is not finished and Skip can be tapped again.
     */
    private fun finish(state: OnboardingUiState) {
        if (advancing) return
        finished = true
        viewModelScope.launch {
            for (step in state.step..state.stepCount) {
                if (step == OnboardingStep.DICTIONARY) {
                    if (!store(step, state)) {
                        finished = false
                        return@launch
                    }
                } else {
                    store(step, state)
                }
            }
            recordedOk { prefs.setOnboarded(true) }
            effectChannel.send(OnboardingEffect.Finished)
        }
    }

    /** Applies [transform] to the draft and keeps the result in [savedState]. */
    private fun updateDraft(transform: (Draft) -> Draft) {
        val updated = draft.updateAndGet(transform)
        savedState[KEY_STEP] = updated.step
        savedState[KEY_PACK] = updated.packId
        savedState[KEY_DIRECTION] = updated.directionId
        savedState[KEY_TEXT_SIZE] = updated.textSize?.name
        savedState[KEY_LINE_SPACING] = updated.lineSpacing
    }

    /** The draft kept in [savedState] by a view model the system killed, or a fresh one. */
    private fun restoredDraft() = Draft(
        step = (savedState.get<Int>(KEY_STEP) ?: OnboardingStep.DICTIONARY)
            .coerceIn(OnboardingStep.DICTIONARY, STEP_COUNT),
        packId = savedState.get<String>(KEY_PACK),
        directionId = savedState.get<String>(KEY_DIRECTION),
        textSize = savedState.get<String>(KEY_TEXT_SIZE)?.let { name -> AppTextSize.entries.firstOrNull { it.name == name } },
        lineSpacing = savedState.get<Float>(KEY_LINE_SPACING),
    )

    /**
     * Runs [block]; a failure is recorded and swallowed, so onboarding continues, and the result says whether [block]
     * ran without failing. Cancellation still propagates.
     */
    private suspend fun recordedOk(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        errors.record(t)
        false
    }

    /**
     * Stores what [step] of [state] sets and returns whether onboarding may move past it; the account step stores
     * nothing. A failed preferences write is recorded and does not hold the user back. The dictionary step switches the
     * pack and returns false unless that pack is active afterwards, because a rejected or cancelled switch also ends
     * idle. A switch that ended [SwitchState.Failed] is shown by the switching overlay, so only the other ways of not
     * taking are recorded.
     */
    private suspend fun store(step: Int, state: OnboardingUiState): Boolean {
        if (step != OnboardingStep.DICTIONARY) {
            recordedOk { prefs.store(step, state) }
            return true
        }
        var took = false
        recordedOk {
            switcher.switchTo(state.selectedPack).join()
            took = databases.active.value == state.selectedPack
            check(took || switcher.state.value is SwitchState.Failed) {
                "Switching to ${state.selectedPack.id} did not take; active is ${databases.active.value?.id}"
            }
        }
        return took
    }

    private fun state(
        prefs: UserPrefs,
        capabilities: DictionaryCapabilities,
        draft: Draft,
        switching: Boolean,
    ): OnboardingUiState {
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
            selectedPack = DictionaryPack.fromId(draft.packId) ?: prefs.dictionaryPack,
            switching = switching,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** [savedState] keys of the draft. */
        const val KEY_STEP = "onboarding_step"
        const val KEY_PACK = "onboarding_pack"
        const val KEY_DIRECTION = "onboarding_direction"
        const val KEY_TEXT_SIZE = "onboarding_text_size"
        const val KEY_LINE_SPACING = "onboarding_line_spacing"

        /** Stores what [step] of [state] sets in preferences; the dictionary and account steps store nothing here. */
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
