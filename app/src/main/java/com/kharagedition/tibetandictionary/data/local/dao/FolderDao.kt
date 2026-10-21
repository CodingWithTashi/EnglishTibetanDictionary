package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.relation.FolderCount
import kotlinx.coroutines.flow.Flow

/** Folders of saved words (user.db). Lists and lookups take a dictionary pack id; folder ids are global. */
@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE pack_id = :pack ORDER BY position, name")
    fun observeAll(pack: String): Flow<List<FolderEntity>>

    /**
     * Returns the new id; a duplicate name in the same pack throws `SQLiteConstraintException` (check
     * [existsByNameIgnoreCase] first).
     */
    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Int

    /** Deletes the folder; its saved entries stay, with `folder_id` set to NULL by the foreign key. */
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query(
        """
        SELECT folder_id, COUNT(*) AS count FROM saved_entries
        JOIN folders f ON f.id = folder_id
        WHERE f.pack_id = :pack
        GROUP BY folder_id
        """,
    )
    fun observeCounts(pack: String): Flow<List<FolderCount>>

    /** True when a folder of [pack] other than [exceptId] has [name], ignoring surrounding spaces and ASCII case. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM folders
            WHERE pack_id = :pack
                AND trim(name) = trim(:name) COLLATE NOCASE
                AND (:exceptId IS NULL OR id != :exceptId)
        )
        """,
    )
    suspend fun existsByNameIgnoreCase(pack: String, name: String, exceptId: Long?): Boolean

    /** True when a folder with [id] exists in [pack]. */
    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE id = :id AND pack_id = :pack)")
    suspend fun exists(id: Long, pack: String): Boolean

    /** The position after the highest one in [pack] (0 when it has no folders). */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM folders WHERE pack_id = :pack")
    suspend fun nextPosition(pack: String): Int
}
