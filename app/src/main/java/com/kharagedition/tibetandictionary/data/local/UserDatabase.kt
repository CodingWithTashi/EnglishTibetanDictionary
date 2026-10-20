package com.kharagedition.tibetandictionary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kharagedition.tibetandictionary.data.local.dao.ActivityDayDao
import com.kharagedition.tibetandictionary.data.local.dao.FolderDao
import com.kharagedition.tibetandictionary.data.local.dao.RecentSearchDao
import com.kharagedition.tibetandictionary.data.local.dao.SavedEntryDao
import com.kharagedition.tibetandictionary.data.local.entity.ActivityDayEntity
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity

/** User state (`user.db`): saved words, folders, recent searches and daily activity. Schema changes need migrations. */
@Database(
    entities = [
        FolderEntity::class,
        SavedEntryEntity::class,
        RecentSearchEntity::class,
        ActivityDayEntity::class,
    ],
    version = UserDatabase.VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao

    abstract fun savedEntryDao(): SavedEntryDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun activityDayDao(): ActivityDayDao

    companion object {
        const val VERSION = 1
        const val DB_NAME = "user.db"
    }
}
