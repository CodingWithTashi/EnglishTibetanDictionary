package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.kharagedition.tibetandictionary.data.local.entity.RelatedEntryEntity

@Dao
interface RelatedEntryDao {

    /** See-also and synonym links from an entry, in display order. */
    @Query("SELECT * FROM related_entries WHERE source_entry_id = :entryId ORDER BY position")
    suspend fun forEntry(entryId: Long): List<RelatedEntryEntity>
}
