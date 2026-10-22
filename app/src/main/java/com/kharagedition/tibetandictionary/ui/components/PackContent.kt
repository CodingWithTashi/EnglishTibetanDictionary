package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.kharagedition.tibetandictionary.data.model.DictionaryPack

/**
 * The dictionary pack the app is showing, provided by the app root. Pack-specific copy and samples (the greeting, the
 * reading preview) default to it; a screen that shows another pack, such as onboarding's choice, passes that one.
 */
val LocalActivePack = staticCompositionLocalOf { DictionaryPack.TIBETAN }
