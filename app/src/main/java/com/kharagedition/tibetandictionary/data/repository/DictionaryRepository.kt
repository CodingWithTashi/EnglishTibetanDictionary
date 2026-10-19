package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Read-only access to the bundled dictionary data. */
interface DictionaryRepository {
    /** Directions (only those with entries), data version and attribution. */
    val capabilities: Flow<DictionaryCapabilities>

    suspend fun direction(id: String): Direction?

    /** Exact → prefix → full-text headword → full-text gloss; did-you-mean [SearchResult.suggestions] when empty. */
    suspend fun search(query: String, directionId: String, limit: Int = 50, offset: Int = 0): SearchResult

    /** Entries to suggest before anything is typed; stable for a given [date]. */
    suspend fun suggestionsForEmptyQuery(directionId: String, date: LocalDate, count: Int = 6): List<EntrySummary>

    fun observeEntry(id: Long): Flow<Entry?>

    suspend fun entry(id: Long): Entry?

    /** Summaries for [ids], in the order of [ids]; unknown ids are skipped. */
    suspend fun summaries(ids: List<Long>): List<EntrySummary>

    /** Closest entries to [text] (did-you-mean), each tagged with the direction it was found in. */
    suspend fun closest(text: String, directionId: String?, limit: Int = 5): List<Suggestion>
}
