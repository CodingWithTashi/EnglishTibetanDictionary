package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.local.relation.EntryWithDetails
import kotlinx.coroutines.flow.Flow

/** Read-only queries over `entries` (dictionary.db). */
@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<EntryWithDetails?>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getWithDetails(id: Long): EntryWithDetails?

    /** Entries for [ids] in no particular order; unknown ids are skipped. */
    @Query("SELECT * FROM entries WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<EntryEntity>

    /**
     * Headwords in `[prefix, upper)` (NOCASE B-tree range scan); [upper] is `PrefixBounds.upper(prefix)`. The headword
     * equal to [exact] or [exactTsheg] (the query with a trailing tsheg) comes first, then Monlam order.
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE direction_id = :dir AND headword >= :prefix AND headword < :upper
        ORDER BY CASE WHEN headword = :exact OR headword = :exactTsheg THEN 0 ELSE 1 END, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun prefixByHeadword(
        dir: String,
        prefix: String,
        upper: String,
        exact: String,
        exactTsheg: String,
        limit: Int,
        offset: Int,
    ): List<EntryEntity>

    /** Wylie in `[prefix, upper)` (NOCASE); entries without Wylie never match. [exact] first, then Monlam order. */
    @Query(
        """
        SELECT * FROM entries
        WHERE direction_id = :dir AND wylie >= :prefix AND wylie < :upper
        ORDER BY CASE WHEN wylie = :exact THEN 0 ELSE 1 END, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun prefixByWylie(
        dir: String,
        prefix: String,
        upper: String,
        exact: String,
        limit: Int,
        offset: Int,
    ): List<EntryEntity>

    /**
     * Full-text match on headword and Wylie syllables, in Monlam order. [match] must come from `FtsQueryBuilder`; raw
     * user text can throw `malformed MATCH expression`.
     *
     * `CROSS JOIN` makes the full-text index the outer loop. Left to choose, SQLite walks the direction in
     * `sort_order` (to skip the sort) and runs the MATCH once per entry: seconds on the shipped asset (`ཀ་བ་`).
     */
    @Query(
        """
        SELECT entries.* FROM entries_fts
        CROSS JOIN entries ON entries.id = entries_fts.rowid
        WHERE entries_fts MATCH :match AND entries.direction_id = :dir
        ORDER BY entries.sort_order
        LIMIT :limit
        """,
    )
    suspend fun ftsHeadword(dir: String, match: String, limit: Int): List<EntryEntity>

    /**
     * [ftsHeadword] without the rows the prefix step lists for the same query: an entry whose headword or Wylie is in
     * `[prefix, upper)` (NOCASE, as in [prefixByHeadword] and [prefixByWylie]) is skipped, so search page 0's
     * "contains" hits never repeat a prefix row of that page or a later one. A NULL Wylie is never a prefix match.
     * Driven by the full-text index, as [ftsHeadword].
     */
    @Query(
        """
        SELECT entries.* FROM entries_fts
        CROSS JOIN entries ON entries.id = entries_fts.rowid
        WHERE entries_fts MATCH :match AND entries.direction_id = :dir
            AND NOT (entries.headword >= :prefix AND entries.headword < :upper)
            AND NOT (entries.wylie IS NOT NULL AND entries.wylie >= :prefix AND entries.wylie < :upper)
        ORDER BY entries.sort_order
        LIMIT :limit
        """,
    )
    suspend fun ftsHeadwordOutsidePrefix(
        dir: String,
        match: String,
        prefix: String,
        upper: String,
        limit: Int,
    ): List<EntryEntity>

    /**
     * Reverse lookup: entries with a sense gloss matching [match], shortest gloss preview first. [match] must come from
     * `FtsQueryBuilder`; raw user text can throw `malformed MATCH expression`.
     *
     * `CROSS JOIN` fixes the join order to full-text index → `senses` → `entries`, so the MATCH runs once. Left to
     * choose, the planner (older SQLite versions in particular) may drive the query from the `entries` direction index
     * and run the MATCH once per row, the plan that made the headword query take 18 s on the shipped asset.
     */
    @Query(
        """
        SELECT DISTINCT entries.* FROM senses_fts
        CROSS JOIN senses ON senses.id = senses_fts.rowid
        CROSS JOIN entries ON entries.id = senses.entry_id
        WHERE senses_fts MATCH :match AND entries.direction_id = :dir
        ORDER BY length(entries.gloss_preview), entries.sort_order
        LIMIT :limit
        """,
    )
    suspend fun ftsGloss(dir: String, match: String, limit: Int): List<EntryEntity>

    /**
     * The first [limit] entries of [dir] in Monlam order. The `(direction_id, sort_order)` index yields them in order,
     * so SQLite reads [limit] rows instead of sorting the direction.
     */
    @Query("SELECT * FROM entries WHERE direction_id = :dir ORDER BY sort_order LIMIT :limit")
    suspend fun firstBySortOrder(dir: String, limit: Int): List<EntryEntity>

    /** The word-of-the-day candidate at [rank]; null when no entry has it. */
    @Query("SELECT * FROM entries WHERE wotd_rank = :rank LIMIT 1")
    suspend fun byWotdRank(rank: Int): EntryEntity?

    /**
     * Entries of [dir] with `from <= wotd_rank < to`, in rank order. The unary `+` keeps SQLite off the direction
     * indices, which would walk every entry of the direction (29 ms on the shipped asset), and on
     * `index_entries_wotd_rank`, which reads only the requested ranks (1 ms).
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE +direction_id = :dir AND wotd_rank >= :from AND wotd_rank < :to
        ORDER BY wotd_rank
        """,
    )
    suspend fun wotdRange(dir: String, from: Int, to: Int): List<EntryEntity>
}
