package com.kharagedition.tibetandictionary.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Process-wide DataStore instance. The delegate guarantees a single active DataStore per file for the
 * whole process, even when the Hilt singleton component is rebuilt (e.g. one component per
 * instrumented test class), which `PreferenceDataStoreFactory.create` inside a module cannot.
 * A corrupted file is replaced with empty preferences (all defaults) instead of failing every read.
 */
private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The pack this build starts on until the user chooses one (`R.string.default_dictionary_pack`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDictionaryPack

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideUserPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPrefsDataStore

    @Provides
    @DefaultDictionaryPack
    fun provideDefaultDictionaryPack(@ApplicationContext context: Context): DictionaryPack =
        DictionaryPack.fromId(context.getString(R.string.default_dictionary_pack)) ?: DictionaryPack.TIBETAN
}
