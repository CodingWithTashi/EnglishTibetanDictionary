package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.data.search.QueryNormalizer
import com.kharagedition.tibetandictionary.data.search.ScriptDetector
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [DictionaryRepository] over the active pack's bundled, read-only dictionary database. Stateless: every call resolves
 * the active database and queries Room on [io]; the flows follow the active pack and drop repeated values. Nothing is
 * resolved at construction, because the first install may not have finished. The search pipeline lives in
 * [SearchEngine].
 */
@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val databases: DictionaryDatabases,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : DictionaryRepository {

    /** Visible directions (`entry_count > 0`, position order) with count-based features, plus `meta`. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val capabilities: Flow<DictionaryCapabilities> =
        databases.active.filterNotNull().flatMapLatest { pack ->
            val db = databases.get(pack)
            combine(db.directionDao().observeVisible(), db.metaDao().observeAll()) { directions, meta ->
                capabilitiesOf(pack, directions, meta.toMetaMap())
            }
        }
            .distinctUntilChanged()
            .flowOn(io)

    /** The direction, or null when it is unknown or has no entries (hidden directions are never exposed). */
    override suspend fun direction(id: String): Direction? = withContext(io) {
        databases.activeDb.directionDao().get(id)?.takeIf { it.entryCount > 0 }?.toDomain()
    }

    /**
     * See [SearchEngine] for the steps. [SearchResult.query] is [query] as given (the steps use its normalised form);
     * an unknown or hidden [directionId] gives an empty result.
     */
    override suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult =
        withContext(io) { engine().search(query, directionId, limit, offset) }

    /**
     * The [count] entries ranked after the day's word of the day in the direction (`wotd_rank`, wrapping after its
     * `wotd_count`), so they never repeat the word of the day and change daily. A direction without ranks gives its
     * first [count] entries in Monlam order.
     */
    override suspend fun suggestionsForEmptyQuery(
        directionId: String,
        date: LocalDate,
        count: Int,
    ): List<EntrySummary> =
        withContext(io) {
            val db = databases.activeDb
            val directionDao = db.directionDao()
            val entryDao = db.entryDao()
            val metaDao = db.metaDao()
            val direction = directionDao.get(directionId)?.takeIf { it.entryCount > 0 }
            if (direction == null || count <= 0) return@withContext emptyList()
            val rows: List<EntryEntity> = if (direction.wotdCount > 0) {
                val dayIndex = wotdDayIndex(date, parseSeed(metaDao.get(MetaKeys.WOTD_SEED)), direction.wotdCount)
                followingRankRanges(dayIndex, count, direction.wotdCount)
                    .flatMap { ranks -> entryDao.wotdRange(direction.id, ranks.first, ranks.last + 1) }
            } else {
                entryDao.firstBySortOrder(direction.id, count)
            }
            rows.map { it.toSummary() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEntry(id: Long): Flow<Entry?> =
        databases.active.filterNotNull().flatMapLatest { pack ->
            databases.get(pack).entryDao().observeWithDetails(id).map { it?.toDomain() }
        }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun entry(id: Long): Entry? = withContext(io) {
        databases.activeDb.entryDao().getWithDetails(id)?.toDomain()
    }

    /** Looked up in chunks below SQLite's bind-variable limit (999 before SQLite 3.32). */
    override suspend fun summaries(ids: List<Long>): List<EntrySummary> = withContext(io) {
        val entryDao = databases.activeDb.entryDao()
        val byId = ids.distinct()
            .chunked(MAX_IDS_PER_QUERY)
            .flatMap { entryDao.getByIds(it) }
            .associateBy { it.id }
        ids.mapNotNull { byId[it]?.toSummary() }
    }

    /**
     * Each headword is asked as given and with a trailing tsheg (Tibetan headwords end with one), in chunks below
     * SQLite's bind-variable limit; rows are matched back the way the NOCASE column compares them.
     */
    override suspend fun exactMatches(headwords: List<String>, directionId: String): Map<String, EntrySummary> =
        withContext(io) {
            val asked = headwords.distinct()
            val forms = asked.flatMap { listOf(it, TibetanScript.ensureTsheg(it)) }.distinct()
            if (forms.isEmpty()) return@withContext emptyMap()
            val entryDao = databases.activeDb.entryDao()
            val byKey = forms.chunked(MAX_IDS_PER_QUERY)
                .flatMap { entryDao.byHeadwords(directionId, it) }
                .associateBy { headwordKey(it.headword) }
            asked.mapNotNull { headword -> byKey[headwordKey(headword)]?.let { headword to it.toSummary() } }.toMap()
        }

    /**
     * Search [directionId], or when null the first visible direction whose source script is [text]'s script (else
     * the first visible one): its hits as suggestions in that direction, or when there are none its did-you-mean
     * list; at most [limit].
     */
    override suspend fun closest(text: String, directionId: String?, limit: Int): List<Suggestion> = withContext(io) {
        val q = QueryNormalizer.normalize(text)
        if (q.isEmpty() || limit <= 0) return@withContext emptyList()
        val target = directionId ?: defaultDirectionFor(q) ?: return@withContext emptyList()
        val result = engine().search(text, target, limit, offset = 0)
        if (result.hits.isNotEmpty()) {
            result.hits.take(limit).map { Suggestion(it, result.directionId) }
        } else {
            result.suggestions.take(limit)
        }
    }

    private suspend fun defaultDirectionFor(q: String): String? {
        val script = ScriptDetector.detect(q)
        val visible = databases.activeDb.directionDao().visible()
        return (visible.firstOrNull { scriptOf(it.sourceScript) == script } ?: visible.firstOrNull())?.id
    }

    /** Built per call from the active database's DAOs; [SearchEngine] holds no state. */
    private fun engine(): SearchEngine = databases.activeDb.let { SearchEngine(it.entryDao(), it.directionDao()) }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500

        /** A headword as NOCASE compares it (ASCII letters folded), without its trailing tsheg. */
        fun headwordKey(headword: String): String = buildString(headword.length) {
            TibetanScript.stripTrailingTsheg(headword).forEach { c ->
                append(if (c in 'A'..'Z') c + ('a' - 'A') else c)
            }
        }
    }
}
