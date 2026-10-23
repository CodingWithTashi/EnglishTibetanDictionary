package com.kharagedition.tibetandictionary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kharagedition.tibetandictionary.data.local.dao.ActivityDayDao
import com.kharagedition.tibetandictionary.data.local.dao.FolderDao
import com.kharagedition.tibetandictionary.data.local.dao.RecentSearchDao
import com.kharagedition.tibetandictionary.data.local.dao.SavedEntryDao
import com.kharagedition.tibetandictionary.data.local.dao.TranslationDao
import com.kharagedition.tibetandictionary.data.local.entity.ActivityDayEntity
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import com.kharagedition.tibetandictionary.data.local.entity.RecentTranslationEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedTranslationEntity

/**
 * User state (`user.db`): saved words, folders, recent searches and recent and saved translations (each scoped to a
 * dictionary pack) and daily activity (shared). Schema changes need migrations ([UserDatabaseMigrations]).
 */
@Database(
    entities = [
        FolderEntity::class,
        SavedEntryEntity::class,
        RecentSearchEntity::class,
        ActivityDayEntity::class,
        RecentTranslationEntity::class,
        SavedTranslationEntity::class,
    ],
    version = UserDatabase.VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao

    abstract fun savedEntryDao(): SavedEntryDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun activityDayDao(): ActivityDayDao

    abstract fun translationDao(): TranslationDao

    companion object {
        const val VERSION = 3
        const val DB_NAME = "user.db"
    }
}
