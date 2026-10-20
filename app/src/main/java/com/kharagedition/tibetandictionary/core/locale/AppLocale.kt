package com.kharagedition.tibetandictionary.core.locale

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Per-app locale (AppCompat): applies and reads the UI language. */
object AppLocale {
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    /** The per-app locale's language tag, or null when none is set (the app follows the system). */
    fun currentTag(): String? =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()

    /** The per-app language, or null when none is set (the app follows the system). */
    fun current(): AppLanguage? = currentTag()?.let(AppLanguage::fromTag)

    /** For receivers and workers, where the per-app locale is not applied to the context (API < 33). */
    fun localizedContext(context: Context, language: AppLanguage): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(config)
    }
}
