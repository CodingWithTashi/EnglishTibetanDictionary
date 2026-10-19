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

    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!locales.isEmpty) locales[0]?.toLanguageTag() else Locale.getDefault().toLanguageTag()
        return AppLanguage.fromTag(tag)
    }

    /** For receivers and workers, where the per-app locale is not applied to the context (API < 33). */
    fun localizedContext(context: Context, language: AppLanguage): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(config)
    }
}
