package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import kotlinx.coroutines.flow.Flow

/** Saved words (user.db). Every list is newest first by `saved_at`. */
@Dao
abstract class SavedEntryDao {

    @Query("SELECT * FROM saved_entries ORDER BY saved_at DESC")
    abstract fun observeAll(): Flow<List<SavedEntryEntity>>

    @Query("SELECT * FROM saved_entries WHERE folder_id = :folderId ORDER BY saved_at DESC")
    abstract fun observeInFolder(folderId: Long): Flow<List<SavedEntryEntity>>

    @Query("SELECT * FROM saved_entries WHERE folder_id IS NULL ORDER BY saved_at DESC")
    abstract fun observeUnfiled(): Flow<List<SavedEntryEntity>>

    /**
     * Entries whose headword, Wylie or gloss preview contains [query] (ASCII case-insensitive, wildcards literal).
     * The stored text's typographic apostrophes (’ ‘ ʼ, as in the English headword "bird’s nest") compare as the
     * ASCII `'` the search query is folded to.
     */
    fun search(query: String): Flow<List<SavedEntryEntity>> = searchLike("%" + escapeLike(query) + "%")

    @Query("SELECT EXISTS(SELECT 1 FROM saved_entries WHERE entry_id = :entryId)")
    abstract fun observeIsSaved(entryId: Long): Flow<Boolean>

    @Query("SELECT entry_id FROM saved_entries")
    abstract fun observeIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM saved_entries")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT * FROM saved_entries WHERE entry_id = :entryId")
    abstract suspend fun get(entryId: Long): SavedEntryEntity?

    /** Inserts [entry]; an already saved entry is left as is and -1 is returned. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(entry: SavedEntryEntity): Long

    /** Returns the number of rows deleted (0 or 1). */
    @Query("DELETE FROM saved_entries WHERE entry_id = :entryId")
    abstract suspend fun delete(entryId: Long): Int

    /** Moves the entry to [folderId] (null = no folder). */
    @Query("UPDATE saved_entries SET folder_id = :folderId, updated_at = :now WHERE entry_id = :entryId")
    abstract suspend fun setFolder(entryId: Long, folderId: Long?, now: Long): Int

    @Query("UPDATE saved_entries SET note = :note, updated_at = :now WHERE entry_id = :entryId")
    abstract suspend fun setNote(entryId: Long, note: String?, now: Long): Int

    @Query(
        """
        SELECT * FROM saved_entries
        WHERE REPLACE(REPLACE(REPLACE(headword, '’', ''''), '‘', ''''), 'ʼ', '''') LIKE :pattern ESCAPE '\'
            OR wylie LIKE :pattern ESCAPE '\'
            OR REPLACE(REPLACE(REPLACE(gloss_preview, '’', ''''), '‘', ''''), 'ʼ', '''') LIKE :pattern ESCAPE '\'
        ORDER BY saved_at DESC
        """,
    )
    protected abstract fun searchLike(pattern: String): Flow<List<SavedEntryEntity>>

    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
