package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** The picker's inline new-folder row: [FolderNameField], Save (enabled once the name is not blank) and cancel. */
@Composable
internal fun NewFolderRow(name: String, onNameChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
        FolderNameField(name, onNameChange, onDone = onSave, Modifier.weight(1f).testTag(FOLDER_NAME_TAG))
        FilledButton(stringResource(R.string.save), onSave, size = ButtonSize.Small, enabled = name.isNotBlank())
        DictIconButton(
            DictIcons.Close,
            stringResource(R.string.close),
            onCancel,
            size = DictTheme.dimens.iconButtonMedium,
            iconSize = DictTheme.dimens.iconSmall,
            tint = DictTheme.colors.muted,
        )
    }
}

/**
 * Pill field for a new folder's name (surface1, 2dp accent border, at least 48 high, one line, the "Folder name"
 * hint); Done calls [onDone]. Typed Tibetan runs draw in Jomolhari 16, as in the search field. With [autoFocus] it
 * takes focus when first shown (the Saved tab's new-folder sheet).
 */
@Composable
fun FolderNameField(
    name: String,
    onNameChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(focus) { focus.requestFocus() }
    val tibetanRuns = remember(type.tibetanInline16) { TibetanRunsTransformation(type.tibetanInline16.toRunSpan()) }
    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = modifier
            .heightIn(min = DictTheme.dimens.searchFieldCompact)
            .clip(DictShapes.pill)
            .background(colors.surface1)
            .border(DictTheme.dimens.focusBorder, colors.accent, DictShapes.pill)
            .padding(horizontal = DictTheme.dimens.cardPaddingHorizontal)
            .focusRequester(focus),
        textStyle = type.body.copy(color = colors.ink),
        singleLine = true,
        visualTransformation = tibetanRuns,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) {
                    MixedText(stringResource(R.string.new_folder_hint), type.body, type.tibetanInline16, color = colors.muted)
                }
                inner()
            }
        },
    )
}

private const val FOLDER_NAME_TAG = "folder_name_field"
