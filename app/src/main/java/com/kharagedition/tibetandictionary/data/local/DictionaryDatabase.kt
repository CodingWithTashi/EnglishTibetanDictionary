package com.kharagedition.tibetandictionary.data.local

import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.RoomDatabase
import com.kharagedition.tibetandictionary.data.local.dao.DirectionDao
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.local.dao.MetaDao
import com.kharagedition.tibetandictionary.data.local.dao.RelatedEntryDao
import com.kharagedition.tibetandictionary.data.local.dao.SenseDao
import com.kharagedition.tibetandictionary.data.local.dao.TestWriteDao
import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.EntryFtsEntity
import com.kharagedition.tibetandictionary.data.local.entity.ExampleEntity
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.entity.RelatedEntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.SenseEntity
import com.kharagedition.tibetandictionary.data.local.entity.SenseFtsEntity

/**
 * The bundled, read-only dictionary (`dictionary.db`, copied from assets). Its exported schema
 * (`app/schemas/…DictionaryDatabase/<VERSION>.json`) is the DDL the Python pipeline executes; [VERSION] equals the
 * asset's `PRAGMA user_version` and is bumped on every data release.
 */
@Database(
    entities = [
        DirectionEntity::class,
        EntryEntity::class,
        SenseEntity::class,
        ExampleEntity::class,
        RelatedEntryEntity::class,
        MetaEntity::class,
        EntryFtsEntity::class,
        SenseFtsEntity::class,
    ],
    version = DictionaryDatabase.VERSION,
    exportSchema = true,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun directionDao(): DirectionDao

    abstract fun entryDao(): EntryDao

    abstract fun senseDao(): SenseDao

    abstract fun relatedEntryDao(): RelatedEntryDao

    abstract fun metaDao(): MetaDao

    /** Fixture inserts for tests; the app never writes dictionary.db. */
    @VisibleForTesting
    abstract fun testWriteDao(): TestWriteDao

    companion object {
        const val VERSION = 1
        const val DB_NAME = "dictionary.db"
        const val ASSET_PATH = "dictionary/bo.db"
    }
}
