package com.kharagedition.tibetandictionary.ui.about

import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.data.model.DictionaryPack

/**
 * About: the app version, the entry count (null when unknown), the active [pack] and its data's [sourceName],
 * [author] and [licence], and the data's own [attribution] text (null when it has none).
 */
@Immutable
data class AboutUiState(
    val versionName: String,
    val entryCount: Int?,
    val pack: DictionaryPack,
    val sourceName: String,
    val author: String,
    val licence: String,
    val attribution: String?,
)
