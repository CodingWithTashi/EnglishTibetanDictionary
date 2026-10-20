package com.kharagedition.tibetandictionary.ui.profile

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.AppLocale
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Switches the per-app UI language. A seam over [AppLocale.apply], whose per-app locale needs a running activity
 * on API 33+, so view models can be tested without one.
 */
fun interface LocaleApplier {
    fun apply(language: AppLanguage)
}

@Module
@InstallIn(ViewModelComponent::class)
object LocaleApplierModule {
    @Provides
    fun localeApplier(): LocaleApplier = LocaleApplier(AppLocale::apply)
}
