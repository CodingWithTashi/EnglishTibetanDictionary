package com.kharagedition.tibetandictionary.data.model

/** A recent search; [entryId] and [headword] are set when the user opened a result from it. */
data class RecentSearch(
    val id: Long,
    val query: String,
    val directionId: String,
    val entryId: Long?,
    val headword: String?,
    /** Epoch milliseconds. */
    val searchedAt: Long,
)
