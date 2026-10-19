package com.kharagedition.tibetandictionary.core.di

import com.kharagedition.tibetandictionary.data.install.ApkAssetSource
import com.kharagedition.tibetandictionary.data.install.AssetSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** What the dictionary installer copies; `DictionaryInstaller` itself is bound in [RepositoryModule]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class InstallModule {
    @Binds @Singleton abstract fun bindAssetSource(impl: ApkAssetSource): AssetSource
}
