package com.kharagedition.tibetandictionary.core.di

import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.DictionaryInstallerImpl
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcherImpl
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.ActivityRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepository
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.SavedTranslationsRepository
import com.kharagedition.tibetandictionary.data.repository.SavedTranslationsRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.TranslationHistoryRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationHistoryRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepositoryImpl
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import com.kharagedition.tibetandictionary.data.translation.TranslationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindDictionaryRepository(impl: DictionaryRepositoryImpl): DictionaryRepository
    @Binds @Singleton abstract fun bindSavedRepository(impl: SavedRepositoryImpl): SavedRepository
    @Binds @Singleton abstract fun bindRecentSearchesRepository(impl: RecentSearchesRepositoryImpl): RecentSearchesRepository
    @Binds @Singleton abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository
    @Binds @Singleton abstract fun bindWordOfTheDayRepository(impl: WordOfTheDayRepositoryImpl): WordOfTheDayRepository
    @Binds @Singleton abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
    @Binds @Singleton abstract fun bindDictionaryInstaller(impl: DictionaryInstallerImpl): DictionaryInstaller
    @Binds @Singleton abstract fun bindDictionarySwitcher(impl: DictionarySwitcherImpl): DictionarySwitcher
    @Binds @Singleton abstract fun bindTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository
    @Binds @Singleton
    abstract fun bindTranslationHistoryRepository(impl: TranslationHistoryRepositoryImpl): TranslationHistoryRepository
    @Binds @Singleton
    abstract fun bindSavedTranslationsRepository(impl: SavedTranslationsRepositoryImpl): SavedTranslationsRepository
}
