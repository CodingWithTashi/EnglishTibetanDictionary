package com.kharagedition.tibetandictionary.ui.model

import androidx.compose.runtime.Immutable

/**
 * State of the hoisted [com.kharagedition.tibetandictionary.ui.components.FolderPickerSheet] (Entry and Saved):
 * the [folders] to pick from, the word's current folder [selectedId] (null = none) and the inline new-folder field
 * ([creating], [newFolderName]); [createError] is resolved by the screen in the app language.
 */
@Immutable
data class FolderPickerUi(
    val folders: List<FolderUi>,
    val selectedId: Long?,
    val creating: Boolean = false,
    val newFolderName: String = "",
    val createError: UiText? = null,
)
