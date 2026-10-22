package com.kharagedition.tibetandictionary.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.install.ApkAssetSource
import com.kharagedition.tibetandictionary.data.install.AssetSizeProvider
import com.kharagedition.tibetandictionary.data.install.AssetSource
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.UserDatabaseMigrations
import com.kharagedition.tibetandictionary.data.local.dao.ActivityDayDao
import com.kharagedition.tibetandictionary.data.local.dao.FolderDao
import com.kharagedition.tibetandictionary.data.local.dao.RecentSearchDao
import com.kharagedition.tibetandictionary.data.local.dao.SavedEntryDao
import com.kharagedition.tibetandictionary.data.model.resources
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * One prebuilt dictionary file per pack, copied from its asset on first open. A version mismatch drops the file and
     * copies the new asset again (it holds no user data). TRUNCATE journal: the files are only read at runtime.
     */
    @Provides
    @Singleton
    fun provideDictionaryDatabases(@ApplicationContext context: Context): DictionaryDatabases =
        DictionaryDatabases { pack ->
            Room.databaseBuilder(context, DictionaryDatabase::class.java, pack.dbName)
                .createFromAsset(pack.assetPath)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        }

    /** What the dictionary installer copies onto the device before Room opens it. */
    @Provides
    fun provideAssetSource(source: ApkAssetSource): AssetSource = source

    /** The pack asset's size as the data build recorded it (`dictionary_stats_<id>.xml`); 0 there means unknown. */
    @Provides
    fun provideAssetSizeProvider(@ApplicationContext context: Context): AssetSizeProvider = AssetSizeProvider { pack ->
        context.resources.getInteger(pack.resources.assetBytes).toLong().takeIf { it > 0 }
    }

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.DB_NAME)
            .addMigrations(UserDatabaseMigrations.MIGRATION_1_2)
            .build()

    @Provides fun provideFolderDao(db: UserDatabase): FolderDao = db.folderDao()

    @Provides fun provideSavedEntryDao(db: UserDatabase): SavedEntryDao = db.savedEntryDao()

    @Provides fun provideRecentSearchDao(db: UserDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides fun provideActivityDayDao(db: UserDatabase): ActivityDayDao = db.activityDayDao()
}
