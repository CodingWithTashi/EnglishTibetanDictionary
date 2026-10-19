package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectionDao {

    /** Directions that have entries, in switcher position order. */
    @Query("SELECT * FROM directions WHERE entry_count > 0 ORDER BY position")
    fun observeVisible(): Flow<List<DirectionEntity>>

    @Query("SELECT * FROM directions WHERE id = :id")
    suspend fun get(id: String): DirectionEntity?
}
