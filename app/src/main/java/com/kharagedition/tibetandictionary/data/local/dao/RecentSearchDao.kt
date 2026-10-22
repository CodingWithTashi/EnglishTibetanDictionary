package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

/** Recent searches (user.db): one row per (pack, query, direction), newest first and trimmed per pack. */
@Dao
abstract class RecentSearchDao {

    @Query("SELECT * FROM recent_searches WHERE pack_id = :pack ORDER BY searched_at DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(pack: String, limit: Int): Flow<List<RecentSearchEntity>>

    /**
     * Upserts [entity] on its (pack, query, direction) and keeps only the [keep] newest rows of its pack. An existing
     * row keeps its id, and keeps its opened entry when [entity] has none.
     */
    @Transaction
    open suspend fun record(entity: RecentSearchEntity, keep: Int = MAX_ROWS) {
        val existing = find(entity.packId, entity.query, entity.directionId)
        val row = when {
            existing == null -> entity
            entity.entryId != null -> entity.copy(id = existing.id)
            else -> entity.copy(id = existing.id, entryId = existing.entryId, headword = existing.headword)
        }
        insertOrReplace(row)
        trim(entity.packId, keep)
    }

    @Query("DELETE FROM recent_searches WHERE id = :id")
    abstract suspend fun delete(id: Long): Int

    @Query("DELETE FROM recent_searches WHERE pack_id = :pack")
    abstract suspend fun clear(pack: String)

    @Query("SELECT * FROM recent_searches WHERE pack_id = :pack AND `query` = :query AND direction_id = :directionId")
    protected abstract suspend fun find(pack: String, query: String, directionId: String): RecentSearchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOrReplace(row: RecentSearchEntity): Long

    @Query(
        """
        DELETE FROM recent_searches WHERE pack_id = :pack AND id NOT IN (
            SELECT id FROM recent_searches WHERE pack_id = :pack ORDER BY searched_at DESC, id DESC LIMIT :keep
        )
        """,
    )
    protected abstract suspend fun trim(pack: String, keep: Int)

    companion object {
        /** Recent searches kept in user.db per dictionary pack. */
        const val MAX_ROWS = 50
    }
}
