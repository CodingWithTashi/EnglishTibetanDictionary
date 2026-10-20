package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.FolderPickerSheet
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.entry.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** New-folder sheet: title padding and the gap between its rows. */
private val SheetTitleTop = 8.dp
private val SheetGap = 12.dp
private val SheetBottom = 24.dp

internal const val FOLDER_NAME_TAG = "saved_folder_name"

/** The hoisted [FolderPickerSheet] for today's word, just saved. */
@Composable
internal fun SavedFolderPicker(picker: FolderPickerUi, onEvent: (SavedEvent) -> Unit) {
    FolderPickerSheet(
        folders = picker.folders,
        selectedId = picker.selectedId,
        onPick = { onEvent(SavedEvent.PickFolder(it)) },
        creating = picker.creating,
        newFolderName = picker.newFolderName,
        onNewFolderNameChange = { onEvent(SavedEvent.NewFolderNameChanged(it)) },
        onStartCreate = { onEvent(SavedEvent.StartCreateFolder) },
        onCancelCreate = { onEvent(SavedEvent.CancelCreateFolder) },
        onCreate = { onEvent(SavedEvent.CreateFolder(it)) },
        onDismiss = { onEvent(SavedEvent.DismissFolderPicker) },
        createError = picker.createError?.resolve(LocalResources.current),
    )
}

/** The "+" chip's sheet: "New folder", a name field with Save, and the duplicate-name error under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewFolderSheet(state: NewFolderUi, onEvent: (SavedEvent) -> Unit) {
    DictModalSheet(onDismissRequest = { onEvent(SavedEvent.DismissAddFolder) }) {
        NewFolderContent(
            state,
            onNameChange = { onEvent(SavedEvent.NewFolderNameChanged(it)) },
            onCreate = { onEvent(SavedEvent.AddFolder(it)) },
        )
    }
}

@Composable
internal fun NewFolderContent(state: NewFolderUi, onNameChange: (String) -> Unit, onCreate: (String) -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val name = state.name.trim()
    val save = { if (name.isNotEmpty()) onCreate(name) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = DictTheme.dimens.screenPadding, end = DictTheme.dimens.screenPadding, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(SheetGap),
    ) {
        MixedText(
            stringResource(R.string.new_folder),
            type.noResultsTitle,
            type.tibetanGloss,
            Modifier
                .padding(top = SheetTitleTop)
                .semantics { heading() },
            color = colors.ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            FolderNameField(state.name, onNameChange, onDone = save, Modifier.weight(1f))
            FilledButton(stringResource(R.string.save), save, size = ButtonSize.Small, enabled = name.isNotEmpty())
        }
        val error = state.error
        if (error != null) {
            MixedText(error.resolve(LocalResources.current), type.caption, type.tibetanInline14, color = colors.danger)
        }
    }
}

/**
 * Pill name field (surface1, 2dp accent border, at least 48 high) that takes focus when shown; Done saves.
 * `FolderPickerSheet` has the same field but keeps it private.
 */
@Composable
private fun FolderNameField(name: String, onNameChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = modifier
            .heightIn(min = DictTheme.dimens.searchFieldCompact)
            .clip(DictShapes.pill)
            .background(colors.surface1)
            .border(DictTheme.dimens.focusBorder, colors.accent, DictShapes.pill)
            .padding(horizontal = DictTheme.dimens.cardPaddingHorizontal)
            .focusRequester(focus)
            .testTag(FOLDER_NAME_TAG),
        textStyle = type.body.copy(color = colors.ink),
        singleLine = true,
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
