package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.MatchField
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.model.SearchHit
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DictionaryRepository] over [entries]. [search] returns a canned [searchResults] entry when one is set
 * for (query, directionId); otherwise it does a naive match in the direction (headword prefix, then Wylie prefix,
 * then gloss substring) without did-you-mean. Every search is logged in [searches].
 */
class FakeDictionaryRepository(
    capabilities: DictionaryCapabilities = SampleData.capabilities,
    entries: List<Entry> = SampleData.entries,
) : DictionaryRepository {
    data class SearchCall(val query: String, val directionId: String, val limit: Int, val offset: Int)

    override val capabilities = MutableStateFlow(capabilities)
    val entries = MutableStateFlow(entries.associateBy { it.summary.id })
    val searchResults = mutableMapOf<Pair<String, String>, SearchResult>()
    var closestResults: List<Suggestion> = emptyList()
    val searches = mutableListOf<SearchCall>()

    override suspend fun direction(id: String): Direction? = capabilities.value.directions.firstOrNull { it.id == id }

    override suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult {
        searches += SearchCall(query, directionId, limit, offset)
        searchResults[query to directionId]?.let { return it }
        val q = query.trim()
        val hits = if (q.isEmpty()) emptyList() else summariesIn(directionId).mapNotNull { naiveHit(it, q) }
        return SearchResult(
            query = query,
            directionId = directionId,
            script = scriptOf(q),
            hits = hits.drop(offset).take(limit),
            hasMore = hits.size > offset + limit,
            suggestions = emptyList(),
        )
    }

    override suspend fun suggestionsForEmptyQuery(directionId: String, date: LocalDate, count: Int): List<EntrySummary> =
        summariesIn(directionId).take(count)

    override fun observeEntry(id: Long): Flow<Entry?> = entries.map { it[id] }

    override suspend fun entry(id: Long): Entry? = entries.value[id]

    override suspend fun summaries(ids: List<Long>): List<EntrySummary> = ids.mapNotNull { entries.value[it]?.summary }

    /** Headwords compared without a trailing tsheg and ignoring case, as the real query does. */
    override suspend fun exactMatches(headwords: List<String>, directionId: String): Map<String, EntrySummary> {
        fun key(text: String) = TibetanScript.stripTrailingTsheg(text).lowercase()
        val byKey = summariesIn(directionId).associateBy { key(it.headword) }
        return headwords.distinct().mapNotNull { headword -> byKey[key(headword)]?.let { headword to it } }.toMap()
    }

    override suspend fun closest(text: String, directionId: String?, limit: Int): List<Suggestion> =
        closestResults.filter { directionId == null || it.directionId == directionId }.take(limit)

    private fun summariesIn(directionId: String): List<EntrySummary> =
        entries.value.values.map { it.summary }.filter { it.directionId == directionId }

    private fun naiveHit(entry: EntrySummary, q: String): SearchHit? {
        val prefix = 0 until q.length
        return when {
            entry.headword.startsWith(q, ignoreCase = true) -> SearchHit(entry, MatchField.HEADWORD, prefix, null, null)
            entry.wylie?.startsWith(q, ignoreCase = true) == true -> SearchHit(entry, MatchField.WYLIE, null, prefix, null)
            else -> entry.glossPreview.indexOf(q, ignoreCase = true).takeIf { it >= 0 }?.let { start ->
                SearchHit(entry, MatchField.GLOSS, null, null, start until start + q.length)
            }
        }
    }

    private fun scriptOf(q: String): Script = when {
        TibetanScript.hasTibetan(q) -> Script.TIBETAN
        q.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } -> Script.HAN
        else -> Script.LATIN
    }
}
