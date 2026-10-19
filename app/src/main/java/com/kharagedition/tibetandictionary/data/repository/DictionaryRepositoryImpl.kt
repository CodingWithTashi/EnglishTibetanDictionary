package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.data.local.dao.DirectionDao
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.local.dao.MetaDao
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.data.search.PrefixBounds
import com.kharagedition.tibetandictionary.data.search.QueryNormalizer
import com.kharagedition.tibetandictionary.data.search.ScriptDetector
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [DictionaryRepository] over the bundled, read-only `dictionary.db`. Stateless: every call queries Room on [io]; the
 * flows only drop repeated values. The search pipeline lives in [SearchEngine].
 */
@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val directionDao: DirectionDao,
    private val entryDao: EntryDao,
    private val metaDao: MetaDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : DictionaryRepository {

    private val engine = SearchEngine(entryDao, directionDao)

    /** Visible directions (`entry_count > 0`, position order) with count-based features, plus `meta`. */
    override val capabilities: Flow<DictionaryCapabilities> =
        combine(directionDao.observeVisible(), metaDao.observeAll()) { directions, meta ->
            capabilitiesOf(directions, meta.toMetaMap())
        }
            .distinctUntilChanged()
            .flowOn(io)

    /** The direction, or null when it is unknown or has no entries (hidden directions are never exposed). */
    override suspend fun direction(id: String): Direction? = withContext(io) {
        directionDao.get(id)?.takeIf { it.entryCount > 0 }?.toDomain()
    }

    /**
     * See [SearchEngine] for the steps. [SearchResult.query] is [query] as given (the steps use its normalised form);
     * an unknown or hidden [directionId] gives an empty result.
     */
    override suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult =
        withContext(io) { engine.search(query, directionId, limit, offset) }

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
            val direction = directionDao.get(directionId)?.takeIf { it.entryCount > 0 }
            if (direction == null || count <= 0) return@withContext emptyList()
            val rows: List<EntryEntity> = if (direction.wotdCount > 0) {
                val dayIndex = wotdDayIndex(date, parseSeed(metaDao.get(MetaKeys.WOTD_SEED)), direction.wotdCount)
                followingRankRanges(dayIndex, count, direction.wotdCount)
                    .flatMap { ranks -> entryDao.wotdRange(direction.id, ranks.first, ranks.last + 1) }
            } else {
                // Every headword is in ["", U+10FFFF); none equals "", so this is plain sort_order.
                entryDao.prefixByHeadword(direction.id, "", PrefixBounds.upper(""), "", "", count, 0)
            }
            rows.map { it.toSummary() }
        }

    override fun observeEntry(id: Long): Flow<Entry?> =
        entryDao.observeWithDetails(id)
            .map { it?.toDomain() }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun entry(id: Long): Entry? = withContext(io) { entryDao.getWithDetails(id)?.toDomain() }

    /** Looked up in chunks below SQLite's bind-variable limit (999 before SQLite 3.32). */
    override suspend fun summaries(ids: List<Long>): List<EntrySummary> = withContext(io) {
        val byId = ids.distinct()
            .chunked(MAX_IDS_PER_QUERY)
            .flatMap { entryDao.getByIds(it) }
            .associateBy { it.id }
        ids.mapNotNull { byId[it]?.toSummary() }
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
        val result = engine.search(text, target, limit, offset = 0)
        if (result.hits.isNotEmpty()) {
            result.hits.take(limit).map { Suggestion(it, result.directionId) }
        } else {
            result.suggestions.take(limit)
        }
    }

    private suspend fun defaultDirectionFor(q: String): String? {
        val script = ScriptDetector.detect(q)
        val visible = directionDao.observeVisible().first()
        return (visible.firstOrNull { scriptOf(it.sourceScript) == script } ?: visible.firstOrNull())?.id
    }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}
