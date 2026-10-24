package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.TextFieldValue
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.domain.InputScript
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest

/** What the Search route shows below its header. */
enum class SearchPhase {
    /** Empty query: recent searches and the Wylie tip. */
    Suggestions,

    /** A search has taken longer than the skeleton delay: placeholder rows. */
    Loading,

    /** The result list (empty only while the first search for a query is still under the skeleton delay). */
    Results,

    /** Nothing found: did-you-mean rows and "Try X instead". */
    NoResults,
}

/** The explanation under "No match for …", by the script the query was written in. */
enum class NoMatchHint { Wylie, English, Tibetan }

/**
 * A recent search: the [query] as recorded and, when a result was opened from it and differs from the query,
 * that entry's [openedHeadword]. Tapping it searches [query] again in [directionId].
 */
@Immutable
data class RecentRowUi(
    val id: Long,
    val query: String,
    val queryIsTibetan: Boolean,
    val directionId: String,
    val openedHeadword: String?,
    val openedIsTibetan: Boolean,
)

/** A did-you-mean row: the entry's headword and a [detail] line ("bkra shis · auspicious"). */
@Immutable
data class SuggestionUi(
    val entryId: Long,
    val directionId: String,
    val headword: String,
    val headwordIsTibetan: Boolean,
    val detail: String,
)

/**
 * The Search route (1g/3b/1k). [selectedDirection] is the direction searched (null until the dictionary's
 * directions are known); [autoSelected] when detection moved it away from the user's selection. [highlightFirst]
 * marks the first result as an exact match of the query. [resultCount] counts the rows loaded so far; more can
 * be paged in while [hasMore]. [alternateDirection] is only offered with [SearchPhase.NoResults].
 *
 * [settledQuery] is the (normalised) query the shown results or "No match for …" are for; it trails [query]
 * while the next search is pending. [searchId] identifies that search and changes with every new one, but not
 * with paging; [pagesLoaded] counts the pages it has fetched.
 *
 * [smartTranslate] is the quick translation of the settled query (null = no translate UI), [phraseWords] the
 * phrase's words found as headwords ("In the dictionary"); [showProSheet] while the Pro sheet is open.
 */
@Immutable
data class SearchUiState(
    val query: TextFieldValue = TextFieldValue(),
    val script: InputScript = InputScript.LATIN,
    val slots: DirectionSlots = DirectionSlots(fixed = emptyList(), overflow = emptyList()),
    val selectedDirection: Direction? = null,
    val autoSelected: Boolean = false,
    val phase: SearchPhase = SearchPhase.Suggestions,
    val results: List<SearchResultUi> = emptyList(),
    val highlightFirst: Boolean = false,
    val resultCount: Int = 0,
    val hasMore: Boolean = false,
    val recents: List<RecentRowUi> = emptyList(),
    val showTip: Boolean = false,
    val suggestions: List<SuggestionUi> = emptyList(),
    val alternateDirection: Direction? = null,
    val noMatchHint: NoMatchHint = NoMatchHint.English,
    val micAvailable: Boolean = false,
    val settledQuery: String = "",
    val searchId: Int = 0,
    val pagesLoaded: Int = 0,
    val smartTranslate: SmartTranslateUi? = null,
    val phraseWords: List<SearchResultUi> = emptyList(),
    val showProSheet: Boolean = false,
)

/** User actions on the Search route. */
sealed interface SearchEvent {
    data class QueryChanged(val value: TextFieldValue) : SearchEvent

    /** The field's clear button. */
    data object Clear : SearchEvent

    data object Back : SearchEvent

    /** A tapped switcher segment; it pins the direction until the query is cleared. */
    data class SelectDirection(val directionId: String) : SearchEvent

    data class OpenResult(val entryId: Long) : SearchEvent

    data class RecentTap(val query: String, val directionId: String) : SearchEvent

    data object ClearRecents : SearchEvent

    data class SuggestionTap(val entryId: Long, val directionId: String) : SearchEvent

    /** "Try X instead": the same query, pinned to [SearchUiState.alternateDirection]. */
    data object TryAlternateDirection : SearchEvent

    data object VoiceSearch : SearchEvent

    /** The end of the result list came into view. */
    data object LoadMore : SearchEvent

    /** The quick-translate row's Translate, or Retry. */
    data object TranslateQuery : SearchEvent

    data object OpenInTranslate : SearchEvent

    /** A word of the quick translation: searched in its own direction. */
    data class SmartWordTap(val word: OutputWord) : SearchEvent

    /** "Sign in to translate"; the route runs sign-in, which needs the activity. */
    data object SmartSignIn : SearchEvent

    /** "Daily limit reached · Upgrade": the Pro sheet. */
    data object SmartUpgrade : SearchEvent

    data object DismissProSheet : SearchEvent
}

/** One-shot effects the Search route performs. */
sealed interface SearchEffect {
    data class NavigateEntry(val entryId: Long) : SearchEffect

    data object NavigateBack : SearchEffect

    /** Launch the system speech recogniser; its first result comes back as [SearchEvent.QueryChanged]. */
    data object StartVoiceSearch : SearchEffect

    /** Focus the search field (and so show the keyboard). */
    data object FocusField : SearchEffect

    /** Switch to the Translate tab with [request]. */
    data class OpenTranslate(val request: TranslateRequest) : SearchEffect

    data class Toast(val message: UiText) : SearchEffect
}
