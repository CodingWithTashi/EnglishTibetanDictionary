package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * "Tibetan Dictionary 3.0.0 · 180,412 entries · offline" ([packName] leads), without the count when [entryCount] is
 * null; digits are Tibetan in the bo UI. Shown at the foot of Profile and on About.
 */
@Composable
internal fun versionLine(packName: String, versionName: String, entryCount: Int?): String {
    val language = DictTheme.language
    val version = if (language.usesTibetanDigits) TibetanNumerals.toTibetan(versionName) else versionName
    return if (entryCount == null) {
        stringResource(R.string.version_line_no_count, packName, version)
    } else {
        stringResource(R.string.version_line, packName, version, TibetanNumerals.formatCount(entryCount, language))
    }
}
