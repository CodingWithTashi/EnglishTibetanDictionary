package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.ExampleEntity
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.entity.RelatedEntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.SenseEntity

/**
 * Inserts fixture rows into dictionary.db. Used by tests and the build pipeline verification only: the app never
 * writes dictionary.db (it ships prebuilt). Room needs the DAO on the real database class, so it lives in main.
 * Inserting entries and senses also fills the FTS tables through Room's content-sync triggers.
 */
@Dao
interface TestWriteDao {
    @Insert
    suspend fun insertDirections(rows: List<DirectionEntity>)

    @Insert
    suspend fun insertEntries(rows: List<EntryEntity>)

    @Insert
    suspend fun insertSenses(rows: List<SenseEntity>)

    @Insert
    suspend fun insertExamples(rows: List<ExampleEntity>)

    @Insert
    suspend fun insertRelated(rows: List<RelatedEntryEntity>)

    @Insert
    suspend fun insertMeta(rows: List<MetaEntity>)
}
