package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.relation.FolderCount
import kotlinx.coroutines.flow.Flow

/** Folders of saved words (user.db). */
@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY position, name")
    fun observeAll(): Flow<List<FolderEntity>>

    /** Returns the new id; a duplicate name throws `SQLiteConstraintException` (check [existsByNameIgnoreCase] first). */
    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Int

    /** Deletes the folder; its saved entries stay, with `folder_id` set to NULL by the foreign key. */
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT folder_id, COUNT(*) AS count FROM saved_entries WHERE folder_id IS NOT NULL GROUP BY folder_id")
    fun observeCounts(): Flow<List<FolderCount>>

    /** True when a folder other than [exceptId] has [name], ignoring surrounding spaces and ASCII case. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM folders
            WHERE trim(name) = trim(:name) COLLATE NOCASE AND (:exceptId IS NULL OR id != :exceptId)
        )
        """,
    )
    suspend fun existsByNameIgnoreCase(name: String, exceptId: Long?): Boolean

    /** True when a folder with [id] exists. */
    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    /** The position after the highest one (0 when there are no folders). */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM folders")
    suspend fun nextPosition(): Int
}
