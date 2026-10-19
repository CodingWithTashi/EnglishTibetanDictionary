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
     * Headwords in `[prefix, upper)` (NOCASE B-tree range scan); [upper] is `prefix + U+10FFFF`. The headword equal to
     * [exact] or [exactTsheg] (the query with a trailing tsheg) comes first, then Monlam order.
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

    /** Full-text match on headword and Wylie syllables; [match] is an FTS4 MATCH expression. Monlam order. */
    @Query(
        """
        SELECT entries.* FROM entries
        JOIN entries_fts ON entries_fts.rowid = entries.id
        WHERE entries_fts MATCH :match AND entries.direction_id = :dir
        ORDER BY entries.sort_order
        LIMIT :limit
        """,
    )
    suspend fun ftsHeadword(dir: String, match: String, limit: Int): List<EntryEntity>

    /** Reverse lookup: entries with a sense gloss matching [match] (FTS4), shortest gloss preview first. */
    @Query(
        """
        SELECT DISTINCT entries.* FROM entries
        JOIN senses ON senses.entry_id = entries.id
        JOIN senses_fts ON senses_fts.rowid = senses.id
        WHERE senses_fts MATCH :match AND entries.direction_id = :dir
        ORDER BY length(entries.gloss_preview), entries.sort_order
        LIMIT :limit
        """,
    )
    suspend fun ftsGloss(dir: String, match: String, limit: Int): List<EntryEntity>

    /** The word-of-the-day candidate at [rank]; null when no entry has it. */
    @Query("SELECT * FROM entries WHERE wotd_rank = :rank LIMIT 1")
    suspend fun byWotdRank(rank: Int): EntryEntity?

    /** Entries of [dir] with `from <= wotd_rank < to`, in rank order. */
    @Query("SELECT * FROM entries WHERE direction_id = :dir AND wotd_rank >= :from AND wotd_rank < :to ORDER BY wotd_rank")
    suspend fun wotdRange(dir: String, from: Int, to: Int): List<EntryEntity>
}
