package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

/** Recent searches (user.db): one row per (query, direction), newest first. */
@Dao
abstract class RecentSearchDao {

    @Query("SELECT * FROM recent_searches ORDER BY searched_at DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    /**
     * Upserts [entity] on its (query, direction) and keeps only the [keep] newest rows. An existing row keeps its id,
     * and keeps its opened entry when [entity] has none.
     */
    @Transaction
    open suspend fun record(entity: RecentSearchEntity, keep: Int = MAX_ROWS) {
        val existing = find(entity.query, entity.directionId)
        val row = when {
            existing == null -> entity
            entity.entryId != null -> entity.copy(id = existing.id)
            else -> entity.copy(id = existing.id, entryId = existing.entryId, headword = existing.headword)
        }
        insertOrReplace(row)
        trim(keep)
    }

    @Query("DELETE FROM recent_searches WHERE id = :id")
    abstract suspend fun delete(id: Long): Int

    @Query("DELETE FROM recent_searches")
    abstract suspend fun clear()

    @Query("SELECT * FROM recent_searches WHERE `query` = :query AND direction_id = :directionId")
    protected abstract suspend fun find(query: String, directionId: String): RecentSearchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOrReplace(row: RecentSearchEntity): Long

    @Query(
        """
        DELETE FROM recent_searches WHERE id NOT IN (
            SELECT id FROM recent_searches ORDER BY searched_at DESC, id DESC LIMIT :keep
        )
        """,
    )
    protected abstract suspend fun trim(keep: Int)

    companion object {
        /** Recent searches kept in user.db. */
        const val MAX_ROWS = 50
    }
}
