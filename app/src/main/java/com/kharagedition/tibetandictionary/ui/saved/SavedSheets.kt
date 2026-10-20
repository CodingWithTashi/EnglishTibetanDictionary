package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.FolderNameField
import com.kharagedition.tibetandictionary.ui.components.FolderPickerSheet
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.SheetTitle
import com.kharagedition.tibetandictionary.ui.model.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** New-folder sheet: the gap between its rows, and the space under the last one. */
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

/** "New folder" over the name field and Save, then the duplicate-name error when there is one. */
@Composable
internal fun NewFolderContent(state: NewFolderUi, onNameChange: (String) -> Unit, onCreate: (String) -> Unit) {
    val name = state.name.trim()
    val save = { if (name.isNotEmpty()) onCreate(name) }
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(stringResource(R.string.new_folder))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = DictTheme.dimens.screenPadding, end = DictTheme.dimens.screenPadding, bottom = SheetBottom),
            verticalArrangement = Arrangement.spacedBy(SheetGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
                FolderNameField(
                    state.name,
                    onNameChange,
                    onDone = save,
                    Modifier.weight(1f).testTag(FOLDER_NAME_TAG),
                    autoFocus = true,
                )
                FilledButton(stringResource(R.string.save), save, size = ButtonSize.Small, enabled = name.isNotEmpty())
            }
            val error = state.error
            if (error != null) {
                MixedText(
                    error.resolve(LocalResources.current),
                    DictTheme.type.caption,
                    DictTheme.type.tibetanInline14,
                    color = DictTheme.colors.danger,
                )
            }
        }
    }
}
