package com.kharagedition.tibetandictionary.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.AppLocale

/**
 * Reads and switches the per-app UI language. A seam over [AppLocale], whose per-app locale needs a running
 * activity on API 33+, so view models can be tested without one.
 */
interface LocaleApplier {
    /** Switches the UI to [language]; the activity is recreated in it. */
    fun setLanguage(language: AppLanguage)

    /** The per-app language in effect, or null when none is set (the app follows the system). */
    fun currentLanguage(): AppLanguage?
}

/** The per-app language through AppCompat, as [AppLocale] sets it. */
internal object AppCompatLocaleApplier : LocaleApplier {
    override fun setLanguage(language: AppLanguage) = AppLocale.apply(language)

    override fun currentLanguage(): AppLanguage? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }
}
