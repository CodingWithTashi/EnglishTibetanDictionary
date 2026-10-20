package com.kharagedition.tibetandictionary.core.locale

import javax.inject.Inject

/**
 * Reads and switches the per-app UI language. A seam over [AppLocale], whose per-app locale needs a running
 * activity on API 33+, so view models can be tested without one. Bound in `core/di/AppModule`.
 */
interface LocaleApplier {
    /** Switches the UI to [language]; the activity is recreated in it. */
    fun setLanguage(language: AppLanguage)

    /** The per-app language in effect, or null when none is set (the app follows the system). */
    fun currentLanguage(): AppLanguage?
}

/** The per-app language through AppCompat, as [AppLocale] sets and reads it. */
class AppCompatLocaleApplier @Inject constructor() : LocaleApplier {
    override fun setLanguage(language: AppLanguage) = AppLocale.apply(language)

    override fun currentLanguage(): AppLanguage? = AppLocale.current()
}
