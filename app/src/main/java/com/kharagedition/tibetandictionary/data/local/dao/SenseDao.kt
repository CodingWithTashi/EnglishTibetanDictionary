package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kharagedition.tibetandictionary.data.local.relation.SenseWithExamples

@Dao
interface SenseDao {

    /** Senses of an entry ordered by sense, then sub-sense (a sense without sub-number first); examples unordered. */
    @Transaction
    @Query("SELECT * FROM senses WHERE entry_id = :entryId ORDER BY sense_no, sub_no")
    suspend fun forEntry(entryId: Long): List<SenseWithExamples>
}
