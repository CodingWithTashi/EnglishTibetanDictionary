package com.kharagedition.tibetandictionary.ui.translate

import android.app.Activity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.repository.SavedTranslationsRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationHistoryRepository
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslateResult
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.data.translation.TranslationRefusal
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import com.kharagedition.tibetandictionary.data.translation.translationPairs
import com.kharagedition.tibetandictionary.domain.ScriptCheck
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.ui.model.SignInAction
import com.kharagedition.tibetandictionary.ui.model.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Translate tab: the input, the direction, one translation at a time, its save state, and the recents. The
 * quota, the backend's directions and whether translation is on come from [TranslationRepository]; the limit card
 * is derived from the quota, so it stays while the input is edited.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val translation: TranslationRepository,
    private val history: TranslationHistoryRepository,
    private val saved: SavedTranslationsRepository,
    prefs: UserPreferencesRepository,
    private val signInAction: SignInAction,
    private val analytics: Analytics,
    private val errors: ErrorReporter,
) : ViewModel() {

    /** A translation on screen; [source] is the normalised text it translates. */
    private data class Shown(val pair: LangPair, val source: String, val text: TranslatedText)

    /** What the screen holds itself; everything else comes from the repositories. */
    private data class Local(
        val input: TextFieldValue,
        /** The direction the user picked; null, or one the pack or backend doesn't offer, means the default. */
        val chosenPair: LangPair?,
        val phase: TranslatePhase = TranslatePhase.IDLE,
        val shown: Shown? = null,
        /** The last call's failure, until the input changes. */
        val failure: TranslateBlock? = null,
        val sheet: TranslateSheet? = null,
    )

    private val local = MutableStateFlow(
        Local(
            input = (savedState.get<String>(KEY_TEXT) ?: "").toFieldValue(),
            chosenPair = savedState.get<String>(KEY_PAIR)?.let(LangPair::fromKey),
        ),
    )

    private val pack: StateFlow<DictionaryPack?> = prefs.prefs.map { it.dictionaryPack }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Bumped by every change that makes a running translation's result stale; that result is then not shown. */
    private var generation = 0

    private val effectChannel = Channel<TranslateEffect>(Channel.BUFFERED)
    val effects: Flow<TranslateEffect> = effectChannel.receiveAsFlow()

    private val isSaved: Flow<Boolean> = local.map { it.shown }.distinctUntilChanged().flatMapLatest { shown ->
        if (shown == null) {
            flowOf(false)
        } else {
            saved.observeIsSaved(shown.pair, shown.source).catch { t ->
                errors.record(t)
                emit(false)
            }
        }
    }

    private val recents: Flow<List<RecentTranslationUi>> = history.observe()
        .map { rows -> rows.map { RecentTranslationUi(it.id, it.source, it.translation, it.pair) } }
        .catch { t ->
            errors.record(t)
            emit(emptyList())
        }

    val uiState: StateFlow<TranslateUiState> = combine(
        combine(local, isSaved, ::Pair),
        pack.filterNotNull(),
        translation.quota,
        translation.availability,
        recents,
    ) { (state, isSaved), pack, quota, availability, recents ->
        view(state, pack, quota, availability, isSaved, recents)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        TranslateUiState(input = local.value.input),
    )

    init {
        // The text and direction survive process death; the result does not.
        local.onEach { state ->
            savedState[KEY_TEXT] = state.input.text.forSavedState()
            savedState[KEY_PAIR] = state.chosenPair?.key
        }.launchIn(viewModelScope)
        // A pack switch (not the first read) starts over in the new pack's default direction.
        pack.filterNotNull().drop(1).onEach {
            generation++
            local.update { it.copy(chosenPair = null, phase = TranslatePhase.IDLE, shown = null, failure = null) }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: TranslateEvent) {
        when (event) {
            TranslateEvent.Shown -> viewModelScope.launch { translation.prepare() }
            is TranslateEvent.InputChanged -> changeInput(event.value)
            is TranslateEvent.Paste -> paste(event.text)
            TranslateEvent.Clear -> changeInput(TextFieldValue())
            TranslateEvent.Swap -> swap(moveResult = true)
            TranslateEvent.SwitchDirection -> swap(moveResult = false)
            TranslateEvent.Translate, TranslateEvent.Retry -> translate()
            is TranslateEvent.SampleTap -> changeInput(event.text.toFieldValue())
            is TranslateEvent.RecentTap -> restoreRecent(event.id)
            TranslateEvent.ClearRecents -> viewModelScope.launch { recorded { history.clear() } }
            TranslateEvent.Copy -> local.value.shown?.let {
                effectChannel.trySend(TranslateEffect.Copy(it.text.translation))
            }
            TranslateEvent.Share -> local.value.shown?.let {
                effectChannel.trySend(TranslateEffect.Share("${it.source}\n\n${it.text.translation}"))
            }
            TranslateEvent.ToggleSave -> toggleSave()
            is TranslateEvent.WordTap -> event.word.query?.let { effectChannel.trySend(TranslateEffect.OpenSearch(it)) }
            TranslateEvent.LookUpWords -> TranslationText.normalize(local.value.input.text)
                .takeIf { it.isNotEmpty() }
                ?.let { effectChannel.trySend(TranslateEffect.OpenSearch(it)) }
            TranslateEvent.OpenToday -> local.update { it.copy(sheet = TranslateSheet.TODAY) }
            is TranslateEvent.OpenPro -> {
                analytics.log(AnalyticsEvent.ProInterest(event.source))
                local.update { it.copy(sheet = TranslateSheet.PRO) }
            }
            TranslateEvent.DismissSheet -> local.update { it.copy(sheet = null) }
            is TranslateEvent.Load -> load(event.request)
        }
    }

    /** Google sign-in from the guest limit card or the Today sheet. */
    fun signIn(activity: Activity, source: PromptSource) {
        viewModelScope.launch {
            signInAction.run(activity, source)?.let { effectChannel.trySend(TranslateEffect.Toast(it)) }
        }
    }

    /** A text edit returns to IDLE and drops the result and the failure; a cursor move keeps them. */
    private fun changeInput(value: TextFieldValue) {
        if (value.text == local.value.input.text) {
            local.update { it.copy(input = value) }
            return
        }
        generation++
        local.update { it.copy(input = value, phase = TranslatePhase.IDLE, shown = null, failure = null) }
    }

    private fun paste(text: String) {
        val input = local.value.input
        val start = minOf(input.selection.start, input.selection.end)
        val end = maxOf(input.selection.start, input.selection.end)
        changeInput(TextFieldValue(input.text.replaceRange(start, end, text), TextRange(start + text.length)))
    }

    /** [moveResult]: the shown translation becomes the input, so it can be translated back. */
    private fun swap(moveResult: Boolean) {
        val view = current() ?: return
        val pair = view.pair ?: return
        if (!view.canSwap) return
        generation++
        local.update { state ->
            val moved = state.shown?.takeIf { moveResult && it.pair == pair }?.text?.translation
            state.copy(
                input = moved?.toFieldValue() ?: state.input,
                chosenPair = pair.swapped(),
                phase = TranslatePhase.IDLE,
                shown = null,
                failure = null,
            )
        }
    }

    private fun translate() {
        val view = current() ?: return
        val pair = view.pair ?: return
        if (!view.canTranslate) return
        val text = local.value.input.text
        val source = TranslationText.normalize(text)
        val request = ++generation
        local.update { it.copy(phase = TranslatePhase.LOADING, shown = null, failure = null) }
        viewModelScope.launch {
            val result = translation.translate(text, pair, TranslateSurface.TRANSLATE_TAB)
            if (request == generation) {
                when (result) {
                    is TranslateResult.Translated -> local.update {
                        it.copy(phase = TranslatePhase.DONE, shown = Shown(pair, source, result.text))
                    }
                    is TranslateResult.Refused -> {
                        local.update { it.copy(phase = TranslatePhase.IDLE, failure = result.refusal.toBlock()) }
                        if (result.refusal is TranslationRefusal.RateLimited) toast(R.string.translate_rate_limited)
                    }
                }
            }
            // Kept in Recent even when the input changed meanwhile: it was translated, and maybe counted.
            if (result is TranslateResult.Translated) recorded { history.record(pair, source, result.text) }
        }
    }

    private fun restoreRecent(id: Long) {
        viewModelScope.launch {
            val record = recorded { history.observe().first().firstOrNull { it.id == id } } ?: return@launch
            // Not "from cache": that label is about the server's cache, and no call was made.
            val text = TranslatedText(record.translation, cached = false, provider = record.provider)
            show(record.pair, record.source, text)
        }
    }

    private fun load(request: TranslateRequest) {
        val result = request.result
        if (result == null) {
            generation++
            local.update {
                it.copy(
                    input = request.text.toFieldValue(),
                    chosenPair = request.pair,
                    phase = TranslatePhase.IDLE,
                    shown = null,
                    failure = null,
                )
            }
        } else {
            show(request.pair, request.text, result)
        }
    }

    /** Puts [text] on screen as the translation of [source], with no call. */
    private fun show(pair: LangPair, source: String, text: TranslatedText) {
        generation++
        local.update {
            it.copy(
                input = source.toFieldValue(),
                chosenPair = pair,
                phase = TranslatePhase.DONE,
                shown = Shown(pair, TranslationText.normalize(source), text),
                failure = null,
            )
        }
    }

    private fun toggleSave() {
        val shown = local.value.shown ?: return
        viewModelScope.launch {
            recorded {
                if (saved.observeIsSaved(shown.pair, shown.source).first()) {
                    saved.observe().first()
                        .firstOrNull { it.pair == shown.pair && it.source == shown.source }
                        ?.let { saved.remove(it.id) }
                    toast(R.string.translation_removed)
                } else {
                    saved.save(shown.pair, shown.source, shown.text)
                    toast(R.string.translation_saved)
                }
            }
        }
    }

    /** The state as it is now, for event handlers; null until the pack is read. */
    private fun current(): TranslateUiState? = pack.value?.let { pack ->
        view(local.value, pack, translation.quota.value, translation.availability.value, isSaved = false, emptyList())
    }

    private fun view(
        state: Local,
        pack: DictionaryPack,
        quota: QuotaState?,
        availability: TranslationAvailability?,
        isSaved: Boolean,
        recents: List<RecentTranslationUi>,
    ): TranslateUiState {
        // Before the backend's directions are known, all the pack's are offered.
        val open = pack.translationPairs().filter { availability == null || it in availability.pairs }
        val pair = state.chosenPair?.takeIf { it in open } ?: open.firstOrNull()
        val normalized = TranslationText.normalize(state.input.text)
        val length = TranslationText.length(normalized)
        val maxChars = quota?.quota?.maxChars ?: DEFAULT_MAX_CHARS
        val near = length * PERCENT >= maxChars * NEAR_PERCENT
        val hint = when (pair?.let { TranslationText.check(normalized, it.source) }) {
            ScriptCheck.NOT_TIBETAN_SCRIPT -> InlineHint.NOT_TIBETAN_SCRIPT
            ScriptCheck.WRONG_SCRIPT -> InlineHint.LOOKS_TIBETAN
            else -> null
        }
        val disabled = availability?.enabled == false
        val limit = quota?.takeIf { it.remaining == 0 }?.let {
            if (it.quota.plan == Plan.GUEST) {
                TranslateBlock.GuestLimit(it.quota.used, it.moreWhenSignedIn)
            } else {
                TranslateBlock.DailyLimit
            }
        }
        val plan = quota?.quota?.plan
        return TranslateUiState(
            pair = pair,
            canSwap = pair != null && pair.swapped() in open,
            input = state.input,
            length = length,
            maxChars = maxChars,
            counter = when {
                length > maxChars -> CounterLevel.OVER
                near -> CounterLevel.NEAR
                else -> CounterLevel.NORMAL
            },
            hint = hint,
            phase = state.phase,
            result = state.shown?.takeIf { it.pair == pair }?.let {
                TranslationResultUi(
                    text = it.text.translation,
                    words = TranslationText.words(it.text.translation, it.pair.target),
                    cached = it.text.cached,
                    provider = it.text.provider,
                    saved = isSaved,
                )
            },
            block = if (disabled) TranslateBlock.Disabled else limit ?: state.failure,
            pill = quota?.let { UsagePillUi(it.remaining, it.quota.limit, low = it.remaining <= LOW_REMAINING) },
            plan = plan,
            freeLimit = quota?.freeLimit,
            samples = pair?.let { pack.translationSamplesFor(it.source) }.orEmpty(),
            recents = recents,
            sheet = state.sheet,
            showProHint = plan == Plan.FREE && near,
            canTranslate = pair != null &&
                normalized.isNotEmpty() &&
                hint == null &&
                length <= maxChars &&
                state.phase != TranslatePhase.LOADING &&
                !disabled &&
                limit == null,
            inputEnabled = !disabled,
        )
    }

    /** The card for a refusal; null when the quota, the counter or a toast already shows it. */
    private fun TranslationRefusal.toBlock(): TranslateBlock? = when (this) {
        TranslationRefusal.Offline -> TranslateBlock.Offline
        TranslationRefusal.Busy -> TranslateBlock.Busy
        TranslationRefusal.Disabled -> TranslateBlock.Disabled
        // The client checks these before calling, so the server refusing them is unexpected too.
        TranslationRefusal.NotTibetanScript, TranslationRefusal.WrongScript, TranslationRefusal.EmptyText,
        TranslationRefusal.Unauthenticated, TranslationRefusal.AccountGone, TranslationRefusal.GuestAccount,
        TranslationRefusal.Unexpected,
        -> TranslateBlock.Unexpected
        // The repository updates the quota (limit, maxChars) or the directions for these; a rate limit is a toast.
        is TranslationRefusal.DailyLimit, is TranslationRefusal.TooLong, TranslationRefusal.UnsupportedPair,
        is TranslationRefusal.RateLimited,
        -> null
    }

    private fun toast(message: Int) {
        effectChannel.trySend(TranslateEffect.Toast(UiText(message)))
    }

    /** What [block] returns, or null when it fails; any failure but cancellation is recorded. */
    private inline fun <T> recorded(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        errors.record(t)
        null
    }

    private fun String.toFieldValue() = TextFieldValue(this, TextRange(length))

    /** Only the start of a huge paste: a saved state over about 1 MB crashes the app when it goes to the background. */
    private fun String.forSavedState(): String = when {
        length <= MAX_SAVED_CHARS -> this
        this[MAX_SAVED_CHARS - 1].isHighSurrogate() -> take(MAX_SAVED_CHARS - 1)
        else -> take(MAX_SAVED_CHARS)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_TEXT = "translate_text"
        const val KEY_PAIR = "translate_pair"
        const val MAX_SAVED_CHARS = 20_000
        const val DEFAULT_MAX_CHARS = 100
        const val PERCENT = 100
        const val NEAR_PERCENT = 85
        const val LOW_REMAINING = 3
    }
}
