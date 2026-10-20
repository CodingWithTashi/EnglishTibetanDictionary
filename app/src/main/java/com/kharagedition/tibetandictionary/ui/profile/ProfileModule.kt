package com.kharagedition.tibetandictionary.ui.profile

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** What the Profile view model reads from outside: the per-app locale and where the footer links go. */
@Module
@InstallIn(ViewModelComponent::class)
object ProfileModule {
    @Provides
    fun localeApplier(): LocaleApplier = AppCompatLocaleApplier

    /** The shipped links (`core/Links`). */
    @Provides
    fun linkTargets(): ProfileLinkTargets = ProfileLinkTargets()
}
