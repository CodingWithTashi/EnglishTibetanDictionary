package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * "Tibetan Dictionary 3.0.0 · 180,412 entries · offline", without the count when [entryCount] is null; digits are
 * Tibetan in the bo UI. Shown at the foot of Profile and on About.
 */
@Composable
internal fun versionLine(versionName: String, entryCount: Int?): String {
    val language = DictTheme.language
    val version = if (language.usesTibetanDigits) TibetanNumerals.toTibetan(versionName) else versionName
    return if (entryCount == null) {
        stringResource(R.string.version_line_no_count, version)
    } else {
        stringResource(R.string.version_line, version, TibetanNumerals.formatCount(entryCount, language))
    }
}
