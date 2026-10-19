package com.kharagedition.tibetandictionary.data.model

/** One page of search results for [query] in [directionId]; [suggestions] (did-you-mean) only when [hits] is empty. */
data class SearchResult(
    val query: String,
    val directionId: String,
    val script: Script,
    val hits: List<SearchHit>,
    val hasMore: Boolean,
    val suggestions: List<Suggestion>,
)

/** A matching entry, the field that matched and the ranges to highlight in each displayed field. */
data class SearchHit(
    val entry: EntrySummary,
    val field: MatchField,
    val headwordHighlight: IntRange?,
    val wylieHighlight: IntRange?,
    val glossHighlight: IntRange?,
)

enum class MatchField { HEADWORD, WYLIE, GLOSS }

/** A did-you-mean hit, tagged with the direction it was found in (may differ from the searched one). */
data class Suggestion(
    val hit: SearchHit,
    val directionId: String,
)
