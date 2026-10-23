package com.kharagedition.tibetandictionary.ui.search

import com.kharagedition.tibetandictionary.core.text.DOT_SEPARATOR
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import com.kharagedition.tibetandictionary.data.model.SearchHit
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.domain.InputScript
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi

// Exact-match keys and the mappings from repository models to the Search route's rows.

private val TrailingMarks = charArrayOf(TibetanScript.TSHEG, TibetanScript.NON_BREAKING_TSHEG, '།', '༎')

/** [text] without trailing tsheg and shad, lower-cased, so "བཀྲ་ཤིས་" matches "བཀྲ་ཤིས". */
internal fun exactKey(text: String): String = text.trimEnd(*TrailingMarks).lowercase()

/** True when the headword or the Wylie of the hit is the whole [query]. */
internal fun SearchHit.isExact(query: String): Boolean {
    val key = exactKey(query)
    return exactKey(entry.headword) == key || entry.wylie?.let(::exactKey) == key
}

/** The explanation under "No match for …" for a query written in [script]. */
internal fun noMatchHint(script: InputScript?): NoMatchHint = when (script) {
    InputScript.WYLIE -> NoMatchHint.Wylie
    InputScript.TIBETAN, InputScript.HAN -> NoMatchHint.Tibetan
    InputScript.LATIN, null -> NoMatchHint.English
}

/**
 * The result row, or null when the hit's direction is not among [directions]; rows from another direction than
 * [searchedId] carry its badge.
 */
internal fun SearchHit.toUi(directions: List<Direction>, searchedId: String?): SearchResultUi? = SearchResultUi(
    entryId = entry.id,
    direction = directions.firstOrNull { it.id == entry.directionId } ?: return null,
    headword = entry.headword,
    headwordIsTibetan = TibetanScript.hasTibetan(entry.headword),
    wylie = entry.wylie,
    matchRange = wylieHighlight,
    headwordMatchRange = headwordHighlight,
    gloss = entry.glossPreview,
    showBadge = entry.directionId != searchedId,
)

/** An "In the dictionary" row of a phrase, or null when its direction is not among [directions]. */
internal fun EntrySummary.toPhraseRow(directions: List<Direction>): SearchResultUi? = SearchResultUi(
    entryId = id,
    direction = directions.firstOrNull { it.id == directionId } ?: return null,
    headword = headword,
    headwordIsTibetan = TibetanScript.hasTibetan(headword),
    wylie = wylie,
    matchRange = null,
    headwordMatchRange = null,
    gloss = glossPreview,
    showBadge = false,
)

/** A did-you-mean row. */
internal fun Suggestion.toUi(): SuggestionUi = SuggestionUi(
    entryId = hit.entry.id,
    directionId = directionId,
    headword = hit.entry.headword,
    headwordIsTibetan = TibetanScript.hasTibetan(hit.entry.headword),
    detail = hit.entry.detail(),
)

/** "wylie · gloss", leaving out whichever is missing. */
internal fun EntrySummary.detail(): String =
    listOfNotNull(wylie, glossPreview.takeIf { it.isNotBlank() }).joinToString(DOT_SEPARATOR)

/** A recent-search row; the opened headword is shown only when it differs from the query. */
internal fun RecentSearch.toRow(): RecentRowUi {
    val opened = headword?.takeUnless { exactKey(it) == exactKey(query) }
    return RecentRowUi(
        id = id,
        query = query,
        queryIsTibetan = TibetanScript.hasTibetan(query),
        directionId = directionId,
        openedHeadword = opened,
        openedIsTibetan = opened != null && TibetanScript.hasTibetan(opened),
    )
}
