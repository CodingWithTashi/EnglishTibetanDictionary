package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kharagedition.tibetandictionary.data.local.entity.RecentTranslationEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedTranslationEntity
import kotlinx.coroutines.flow.Flow

/** Recent and saved translations (user.db): one row per (pack, direction, source text) in each table. */
@Dao
abstract class TranslationDao {

    @Query("SELECT * FROM recent_translations WHERE pack_id = :pack ORDER BY used_at DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(pack: String, limit: Int): Flow<List<RecentTranslationEntity>>

    /** Upserts [entity] on its (pack, direction, source), keeping its id; keeps only the [keep] newest of its pack. */
    @Transaction
    open suspend fun recordRecent(entity: RecentTranslationEntity, keep: Int = MAX_RECENTS) {
        val existing = findRecent(entity.packId, entity.sourceLang, entity.targetLang, entity.sourceText)
        insertRecent(if (existing == null) entity else entity.copy(id = existing.id))
        trimRecent(entity.packId, keep)
    }

    @Query("DELETE FROM recent_translations WHERE pack_id = :pack")
    abstract suspend fun clearRecent(pack: String)

    @Query("SELECT * FROM saved_translations WHERE pack_id = :pack ORDER BY saved_at DESC, id DESC")
    abstract fun observeSaved(pack: String): Flow<List<SavedTranslationEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM saved_translations WHERE pack_id = :pack AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang AND source_text = :sourceText)",
    )
    abstract fun observeIsSaved(pack: String, sourceLang: String, targetLang: String, sourceText: String): Flow<Boolean>

    /** Upserts [entity] on its (pack, direction, source), keeping its id; returns the id. */
    @Transaction
    open suspend fun save(entity: SavedTranslationEntity): Long {
        val existing = findSaved(entity.packId, entity.sourceLang, entity.targetLang, entity.sourceText)
        return insertSaved(if (existing == null) entity else entity.copy(id = existing.id))
    }

    /** Deletes the saved translation [id] and returns it, or null if there is none. */
    @Transaction
    open suspend fun removeSaved(id: Long): SavedTranslationEntity? {
        val row = savedById(id) ?: return null
        deleteSaved(id)
        return row
    }

    /** Inserts [row] as it is, id included (undo of [removeSaved]). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSaved(row: SavedTranslationEntity): Long

    @Query(
        "SELECT * FROM recent_translations WHERE pack_id = :pack AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang AND source_text = :sourceText",
    )
    protected abstract suspend fun findRecent(
        pack: String,
        sourceLang: String,
        targetLang: String,
        sourceText: String,
    ): RecentTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecent(row: RecentTranslationEntity): Long

    @Query(
        """
        DELETE FROM recent_translations WHERE pack_id = :pack AND id NOT IN (
            SELECT id FROM recent_translations WHERE pack_id = :pack ORDER BY used_at DESC, id DESC LIMIT :keep
        )
        """,
    )
    protected abstract suspend fun trimRecent(pack: String, keep: Int)

    @Query(
        "SELECT * FROM saved_translations WHERE pack_id = :pack AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang AND source_text = :sourceText",
    )
    protected abstract suspend fun findSaved(
        pack: String,
        sourceLang: String,
        targetLang: String,
        sourceText: String,
    ): SavedTranslationEntity?

    @Query("SELECT * FROM saved_translations WHERE id = :id")
    protected abstract suspend fun savedById(id: Long): SavedTranslationEntity?

    @Query("DELETE FROM saved_translations WHERE id = :id")
    protected abstract suspend fun deleteSaved(id: Long): Int

    companion object {
        /** Recent translations kept in user.db per dictionary pack. */
        const val MAX_RECENTS = 20
    }
}
