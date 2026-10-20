package com.kharagedition.tibetandictionary.ui.profile

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Where the Profile footer links go (the per-app locale is bound app-wide, in `core/di/AppModule`). */
@Module
@InstallIn(ViewModelComponent::class)
object ProfileModule {
    /** The shipped links (`core/Links`). */
    @Provides
    fun linkTargets(): ProfileLinkTargets = ProfileLinkTargets()
}
