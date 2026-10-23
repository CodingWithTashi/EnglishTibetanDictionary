package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationHistoryRepository
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslateResult
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.data.translation.TranslationRefusal
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import com.kharagedition.tibetandictionary.data.translation.translationPair
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.domain.PhraseSegmenter
import com.kharagedition.tibetandictionary.domain.ScriptCheck
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import com.kharagedition.tibetandictionary.ui.translate.UsagePillUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A search the Search route has shown: [query] as typed and searched in [direction]; [hasExact] when its first
 * result is an exact match, [noResults] when it found nothing. The detected script is left out on purpose: an
 * English phrase can detect as Wylie ("bus station" opens like a syllable), and Wylie in a bo or dz direction fails
 * the translation's own script check.
 */
data class Settled(
    val query: String,
    val direction: Direction,
    val hasExact: Boolean,
    val noResults: Boolean,
)

/** Search's quick translation of the settled query (1l–1r); every state carries the usage line's [pill]. */
@Immutable
sealed interface SmartTranslateUi {
    val pill: UsagePillUi?

    /** A tap row into [target]: [instead] is the no-results "Translate instead", [phrase] the 1o card. */
    data class Offer(
        val query: String,
        val target: Lang,
        val instead: Boolean,
        override val pill: UsagePillUi?,
        val phrase: Boolean,
    ) : SmartTranslateUi

    data class Running(override val pill: UsagePillUi?) : SmartTranslateUi

    data class Result(
        val text: TranslatedText,
        val words: List<OutputWord>,
        override val pill: UsagePillUi?,
    ) : SmartTranslateUi

    /** Over [maxChars] for a quick translation; it opens in Translate instead. */
    data class TooLong(val length: Int, val maxChars: Int, override val pill: UsagePillUi?) : SmartTranslateUi

    /** No translations left today: a guest signs in, anyone else sees the Pro sheet. */
    data class Blocked(val plan: Plan, override val pill: UsagePillUi?) : SmartTranslateUi

    data class Failed(val offline: Boolean, override val pill: UsagePillUi?) : SmartTranslateUi
}

/**
 * Smart translate for the Search route (spec "Smart translate in search"). [onSettled] takes each shown search; the
 * block shows only for a bilingual direction, a non-Wylie query of 2+ characters in the source's script, and an
 * open pair. A phrase with no exact match auto-translates [AUTO_DELAY_MS] after it settles, once per (text, pair),
 * while the setting is on and more than [AUTO_MIN_LEFT] translations are known to be left; anything else is a tap
 * row. A call in flight is never cancelled: its result is kept for that text, recorded in the recents, and shown
 * if the text is shown again. [phraseWords] lists a phrase's words that are dictionary headwords.
 */
class SmartTranslateController(
    private val scope: CoroutineScope,
    private val translation: TranslationRepository,
    private val history: TranslationHistoryRepository,
    prefs: UserPreferencesRepository,
    private val dictionary: DictionaryRepository,
    private val errors: ErrorReporter,
) {
    /** A settled query that can be translated: its normalised [source], [length] in code points, [phrase]. */
    private data class Current(
        val settled: Settled,
        val pair: LangPair,
        val source: String,
        val length: Int,
        val phrase: Boolean,
    ) {
        val key: Key get() = Key(source, pair)
    }

    private data class Key(val source: String, val pair: LangPair)

    /** The current query's call, once there is one (or an auto one is pending). */
    private sealed interface Run {
        data object Running : Run
        data class Done(val text: TranslatedText) : Run
        data class Failed(val offline: Boolean) : Run
        data class Limited(val plan: Plan) : Run
        data class TooLong(val length: Int, val maxChars: Int) : Run
    }

    private var lastSettled: Settled? = null
    private val current = MutableStateFlow<Current?>(null)
    private val run = MutableStateFlow<Run?>(null)
    private val smartOn: StateFlow<Boolean?> =
        prefs.prefs.map { it.smartTranslate }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Translations made this visit, by text; the oldest are dropped past [MAX_REMEMBERED]. */
    private val results = object : LinkedHashMap<Key, TranslatedText>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TranslatedText>?) = size > MAX_REMEMBERED
    }
    private val autoTried = mutableSetOf<Key>()
    private val inFlight = mutableSetOf<Key>()
    private var pendingAuto: Job? = null
    private var phraseJob: Job? = null

    val state: StateFlow<SmartTranslateUi?> =
        combine(current, run, translation.quota, translation.availability, ::view)
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val words = MutableStateFlow<List<EntrySummary>>(emptyList())
    val phraseWords: StateFlow<List<EntrySummary>> = words.asStateFlow()

    init {
        // The usage line needs a fresh quota; this does nothing before the first translation made a user.
        scope.launch { safely { translation.refresh() } }
    }

    /** Called by SearchViewModel with every settled outcome (null when the query is empty). */
    fun onSettled(settled: Settled?) {
        if (settled == lastSettled) return
        lastSettled = settled
        pendingAuto?.cancel()
        phraseJob?.cancel()
        words.value = emptyList()
        val next = settled?.let(::currentFor)
        current.value = next
        run.value = next?.let(::knownRun)
        if (next == null) return
        lookUpPhraseWords(next)
        if (run.value == null && autoAllowed(next)) {
            run.value = Run.Running
            pendingAuto = scope.launch {
                delay(AUTO_DELAY_MS)
                if (autoAllowed(next)) {
                    autoTried += next.key
                    startCall(next)
                } else {
                    run.value = null
                }
            }
        }
    }

    /** The tap row's Translate, and Retry after a failure. */
    fun translateNow() {
        val cur = current.value ?: return
        val shown = currentView()
        if (shown is SmartTranslateUi.Offer || shown is SmartTranslateUi.Failed) startCall(cur)
    }

    /** "Open in Translate": the shown text and direction, with the translation when there is one. */
    fun requestFor(): TranslateRequest? {
        val cur = current.value ?: return null
        currentView() ?: return null
        return TranslateRequest(cur.pair, cur.settled.query, (run.value as? Run.Done)?.text)
    }

    private fun currentView(): SmartTranslateUi? =
        view(current.value, run.value, translation.quota.value, translation.availability.value)

    private fun currentFor(settled: Settled): Current? {
        val pair = settled.direction.translationPair() ?: return null
        val source = TranslationText.normalize(settled.query)
        val length = TranslationText.length(source)
        if (length < MIN_LENGTH || TranslationText.check(source, pair.source) != ScriptCheck.OK) return null
        return Current(settled, pair, source, length, TranslationText.isPhrase(source, pair.source))
    }

    /** A translation already made for [cur]'s text, or the call still running for it. */
    private fun knownRun(cur: Current): Run? =
        results[cur.key]?.let(Run::Done) ?: Run.Running.takeIf { cur.key in inFlight }

    private fun autoAllowed(cur: Current): Boolean {
        val quota = translation.quota.value ?: return false
        return cur.phrase &&
            !cur.settled.hasExact &&
            smartOn.value == true &&
            quota.remaining > AUTO_MIN_LEFT &&
            cur.length <= quota.quota.maxChars &&
            translation.availability.value.offers(cur.pair) &&
            cur.key !in autoTried
    }

    private fun startCall(cur: Current) {
        if (current.value == cur) run.value = Run.Running
        if (!inFlight.add(cur.key)) return
        scope.launch {
            val result = try {
                translation.translate(cur.settled.query, cur.pair, TranslateSurface.SEARCH)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errors.record(t)
                TranslateResult.Refused(TranslationRefusal.Unexpected)
            } finally {
                inFlight -= cur.key
            }
            val outcome = when (result) {
                is TranslateResult.Translated -> Run.Done(result.text).also { results[cur.key] = result.text }
                is TranslateResult.Refused -> result.refusal.toRun()
            }
            if (current.value?.key == cur.key) run.value = outcome
            if (result is TranslateResult.Translated) safely { history.record(cur.pair, cur.source, result.text) }
        }
    }

    private fun lookUpPhraseWords(cur: Current) {
        if (!cur.phrase || cur.length > maxChars(translation.quota.value)) return
        phraseJob = scope.launch {
            safely {
                val source = cur.pair.source
                val found = dictionary.exactMatches(
                    PhraseSegmenter.candidates(cur.source, source),
                    cur.settled.direction.id,
                )
                words.value = PhraseSegmenter.segment(cur.source, source, found.keys).mapNotNull(found::get)
            }
        }
    }

    private fun view(
        cur: Current?,
        run: Run?,
        quota: QuotaState?,
        availability: TranslationAvailability?,
    ): SmartTranslateUi? {
        if (cur == null || !availability.offers(cur.pair)) return null
        val pill = quota?.let { UsagePillUi(it.remaining, it.quota.limit, low = it.remaining <= AUTO_MIN_LEFT) }
        val maxChars = maxChars(quota)
        return when (run) {
            Run.Running -> SmartTranslateUi.Running(pill)
            is Run.Done -> {
                val output = TranslationText.words(run.text.translation, cur.pair.target)
                SmartTranslateUi.Result(run.text, output, pill)
            }
            is Run.Failed -> SmartTranslateUi.Failed(run.offline, pill)
            is Run.Limited -> SmartTranslateUi.Blocked(run.plan, pill)
            is Run.TooLong -> SmartTranslateUi.TooLong(run.length, run.maxChars, pill)
            null -> when {
                quota != null && quota.remaining == 0 -> SmartTranslateUi.Blocked(quota.quota.plan, pill)
                cur.length > maxChars -> SmartTranslateUi.TooLong(cur.length, maxChars, pill)
                else -> SmartTranslateUi.Offer(
                    cur.settled.query,
                    cur.pair.target,
                    instead = cur.settled.noResults,
                    pill,
                    phrase = cur.phrase,
                )
            }
        }
    }

    /** Unknown availability offers every pair; the first status call says which are open. */
    private fun TranslationAvailability?.offers(pair: LangPair): Boolean = this == null || (enabled && pair in pairs)

    private fun TranslationRefusal.toRun(): Run = when (this) {
        is TranslationRefusal.DailyLimit -> Run.Limited(plan)
        is TranslationRefusal.TooLong -> Run.TooLong(length, maxChars)
        TranslationRefusal.Offline -> Run.Failed(offline = true)
        else -> Run.Failed(offline = false)
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.record(t)
        }
    }

    private companion object {
        const val AUTO_DELAY_MS = 1_000L

        /** Auto-translate needs more than this many left; it is also where the usage line turns amber. */
        const val AUTO_MIN_LEFT = 3
        const val MIN_LENGTH = 2
        const val DEFAULT_MAX_CHARS = 100
        const val MAX_REMEMBERED = 20

        fun maxChars(quota: QuotaState?): Int = quota?.quota?.maxChars ?: DEFAULT_MAX_CHARS
    }
}
