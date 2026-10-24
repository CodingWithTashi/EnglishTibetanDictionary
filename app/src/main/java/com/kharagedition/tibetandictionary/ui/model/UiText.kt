package com.kharagedition.tibetandictionary.ui.model

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.core.os.ConfigurationCompat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals

/**
 * A string resource and its format arguments, resolved by the UI in the app language: [args] as they are, then
 * [counts] formatted with [TibetanNumerals.formatCount] (Tibetan digits in the Tibetan UI).
 */
@Immutable
data class UiText(
    @param:StringRes val id: Int,
    val args: List<String> = emptyList(),
    val counts: List<Int> = emptyList(),
) {
    fun resolve(resources: Resources): String {
        if (args.isEmpty() && counts.isEmpty()) return resources.getString(id)
        val language = AppLanguage.fromTag(ConfigurationCompat.getLocales(resources.configuration)[0]?.language)
        val all = args + counts.map { TibetanNumerals.formatCount(it, language) }
        return resources.getString(id, *all.toTypedArray())
    }
}
