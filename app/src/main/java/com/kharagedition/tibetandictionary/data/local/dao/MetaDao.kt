package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {

    @Query("SELECT * FROM meta")
    fun observeAll(): Flow<List<MetaEntity>>

    /** The value for [key]; null when the key is missing or its value is null. */
    @Query("SELECT `value` FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?
}
