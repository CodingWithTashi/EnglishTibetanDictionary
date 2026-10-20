package com.kharagedition.tibetandictionary.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.install.ApkAssetSource
import com.kharagedition.tibetandictionary.data.install.AssetSizeProvider
import com.kharagedition.tibetandictionary.data.install.AssetSource
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.dao.ActivityDayDao
import com.kharagedition.tibetandictionary.data.local.dao.DirectionDao
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.local.dao.FolderDao
import com.kharagedition.tibetandictionary.data.local.dao.MetaDao
import com.kharagedition.tibetandictionary.data.local.dao.RecentSearchDao
import com.kharagedition.tibetandictionary.data.local.dao.RelatedEntryDao
import com.kharagedition.tibetandictionary.data.local.dao.SavedEntryDao
import com.kharagedition.tibetandictionary.data.local.dao.SenseDao
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
     * The prebuilt dictionary: copied from the asset on first open. A version mismatch drops the file and copies the
     * new asset again (it holds no user data). TRUNCATE journal: the file is only read at runtime.
     */
    @Provides
    @Singleton
    fun provideDictionaryDatabase(@ApplicationContext context: Context): DictionaryDatabase =
        Room.databaseBuilder(context, DictionaryDatabase::class.java, DictionaryDatabase.DB_NAME)
            .createFromAsset(DictionaryDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    /** What the dictionary installer copies onto the device before Room opens it. */
    @Provides
    fun provideAssetSource(source: ApkAssetSource): AssetSource = source

    /** The asset's size as the data build recorded it (`dictionary_stats.xml`); 0 there means unknown. */
    @Provides
    fun provideAssetSizeProvider(@ApplicationContext context: Context): AssetSizeProvider = AssetSizeProvider {
        context.resources.getInteger(R.integer.dictionary_asset_bytes).toLong().takeIf { it > 0 }
    }

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.DB_NAME).build()

    @Provides fun provideDirectionDao(db: DictionaryDatabase): DirectionDao = db.directionDao()

    @Provides fun provideEntryDao(db: DictionaryDatabase): EntryDao = db.entryDao()

    @Provides fun provideSenseDao(db: DictionaryDatabase): SenseDao = db.senseDao()

    @Provides fun provideRelatedEntryDao(db: DictionaryDatabase): RelatedEntryDao = db.relatedEntryDao()

    @Provides fun provideMetaDao(db: DictionaryDatabase): MetaDao = db.metaDao()

    @Provides fun provideFolderDao(db: UserDatabase): FolderDao = db.folderDao()

    @Provides fun provideSavedEntryDao(db: UserDatabase): SavedEntryDao = db.savedEntryDao()

    @Provides fun provideRecentSearchDao(db: UserDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides fun provideActivityDayDao(db: UserDatabase): ActivityDayDao = db.activityDayDao()
}
