package com.kharagedition.tibetandictionary.ui.translate

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.TextFieldValue
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.EnglishTranslateSamples
import com.kharagedition.tibetandictionary.data.model.resources
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.ui.model.UiText

/** Open-in-Translate handoff (Search, Saved → Translate tab). */
data class TranslateRequest(val pair: LangPair, val text: String, val result: TranslatedText? = null)

/** The character counter's colour: NEAR from 85% of maxChars, OVER above maxChars. */
enum class CounterLevel { NORMAL, NEAR, OVER }

enum class InlineHint {
    /** Romanised (Wylie) input with a Tibetan-script source: it isn't translated. */
    NOT_TIBETAN_SCRIPT,

    /** Tibetan script with English as the source: offers to switch direction. */
    LOOKS_TIBETAN,
}

enum class TranslatePhase { IDLE, LOADING, DONE }

/** The card shown in place of a result. */
sealed interface TranslateBlock {
    /** A guest out of translations; [more] is how many signing in adds today (null when unknown). */
    data class GuestLimit(val used: Int, val more: Int?) : TranslateBlock
    data object DailyLimit : TranslateBlock

    /** Pro at its fair-use limit: no upgrade, it resets at midnight. */
    data object FairUseLimit : TranslateBlock
    data object Offline : TranslateBlock
    data object Busy : TranslateBlock
    data object Disabled : TranslateBlock
    data object Unexpected : TranslateBlock
}

/** "7 of 10 left"; [low] from 3 left. Pro is [unlimited]: "Unlimited today", low only at its fair-use limit. */
data class UsagePillUi(val remaining: Int, val limit: Int, val low: Boolean, val unlimited: Boolean = false) {
    companion object {
        /** [quota]'s pill, low from [lowFrom] left (Pro: at 0). */
        fun of(quota: QuotaState, lowFrom: Int): UsagePillUi {
            val pro = quota.quota.plan == Plan.PRO
            return UsagePillUi(
                remaining = quota.remaining,
                limit = quota.quota.limit,
                low = if (pro) quota.remaining == 0 else quota.remaining <= lowFrom,
                unlimited = pro,
            )
        }
    }
}

data class TranslationResultUi(
    val text: String,
    val words: List<OutputWord>,
    val cached: Boolean,
    val provider: Provider,
    val saved: Boolean,
)

data class RecentTranslationUi(val id: Long, val source: String, val translation: String, val pair: LangPair)

enum class TranslateSheet { TODAY, PRO }

@Immutable
data class TranslateUiState(
    /** Null only when the backend offers none of the pack's directions. */
    val pair: LangPair? = null,
    val canSwap: Boolean = false,
    val input: TextFieldValue = TextFieldValue(),
    /** Code points of the normalised input, as the quota counts them. */
    val length: Int = 0,
    val maxChars: Int = 100,
    val counter: CounterLevel = CounterLevel.NORMAL,
    val hint: InlineHint? = null,
    val phase: TranslatePhase = TranslatePhase.IDLE,
    val result: TranslationResultUi? = null,
    val block: TranslateBlock? = null,
    /** Null until the quota is known. */
    val pill: UsagePillUi? = null,
    val plan: Plan? = null,
    /** The free plan's daily limit, for the guest's "Sign in to get 10 a day"; null until known. */
    val freeLimit: Int? = null,
    val samples: List<String> = emptyList(),
    val recents: List<RecentTranslationUi> = emptyList(),
    val sheet: TranslateSheet? = null,
    /** Where the Pro sheet was opened from, while [sheet] is PRO. */
    val proSource: PromptSource? = null,
    /** "Need longer text? Pro allows 500 characters." */
    val showProHint: Boolean = false,
    val canTranslate: Boolean = false,
    val inputEnabled: Boolean = true,
)

sealed interface TranslateEvent {
    data object Shown : TranslateEvent
    data class InputChanged(val value: TextFieldValue) : TranslateEvent
    data class Paste(val text: String) : TranslateEvent
    data object Clear : TranslateEvent
    data object Swap : TranslateEvent
    data object Translate : TranslateEvent
    data object Retry : TranslateEvent
    data class SampleTap(val text: String) : TranslateEvent
    data class RecentTap(val id: Long) : TranslateEvent
    data object ClearRecents : TranslateEvent
    data object Copy : TranslateEvent
    data object Share : TranslateEvent
    data object ToggleSave : TranslateEvent
    data class WordTap(val word: OutputWord) : TranslateEvent
    data object OpenToday : TranslateEvent
    data class OpenPro(val source: PromptSource) : TranslateEvent
    data object DismissSheet : TranslateEvent
    data object LookUpWords : TranslateEvent

    /** The LOOKS_TIBETAN hint's [Switch]. */
    data object SwitchDirection : TranslateEvent
    data class Load(val request: TranslateRequest) : TranslateEvent
}

sealed interface TranslateEffect {
    data class Copy(val text: String) : TranslateEffect
    data class Share(val text: String) : TranslateEffect
    data class OpenSearch(val query: String) : TranslateEffect
    data class Toast(val message: UiText) : TranslateEffect
}

/** The "Try" chips for [source] in this pack. */
internal fun DictionaryPack.translationSamplesFor(source: Lang): List<String> =
    if (source == Lang.EN) EnglishTranslateSamples else resources.translateSamples
