package com.kharagedition.tibetandictionary.ui.about

import androidx.compose.runtime.Immutable

/**
 * About: the app version, the entry count (null when unknown) and the installed data's own [attribution] text
 * (null when it has none).
 */
@Immutable
data class AboutUiState(
    val versionName: String,
    val entryCount: Int?,
    val attribution: String?,
)
