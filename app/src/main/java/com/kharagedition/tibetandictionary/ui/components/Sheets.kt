package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Sheet drag handle 32×4 with 10 above and 2 below. */
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleTop = 10.dp
private val HandleBottom = 2.dp

/** Sheet title padding, gap between option cards / controls, and space under the last item. */
private val TitleTop = 8.dp
private val TitleBottom = 12.dp
private val OptionGap = 10.dp
private val ControlGap = 14.dp
private val SheetBottom = 24.dp

/** Option card: at least 64 high, 18 side padding, 14 gap, 84 label column, 22 check, 2dp selected border. */
private val OptionHeight = 64.dp
private val OptionSide = 18.dp
private val OptionGapInside = 14.dp
private val OptionLabel = 84.dp
private val OptionCheck = 22.dp
private val SelectedBorder = 2.dp

/**
 * The app's modal bottom sheet: background container with 28dp top corners, the scrim colour, ink content and
 * a 32×4 outline drag handle; no tonal elevation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DictTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = DictShapes.sheet,
        containerColor = colors.background,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
        content = content,
    )
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = HandleTop, bottom = HandleBottom)
            .size(HandleWidth, HandleHeight)
            .background(DictTheme.colors.outline, DictShapes.pill),
    )
}

/** A sheet's title (the no-results title role, Jomolhari in bo): screen side padding, 8 above, 12 below; a heading. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    MixedText(
        text,
        DictTheme.type.noResultsTitle,
        DictTheme.type.tibetanGloss,
        modifier
            .fillMaxWidth()
            .padding(
                start = DictTheme.dimens.screenPadding,
                top = TitleTop,
                end = DictTheme.dimens.screenPadding,
                bottom = TitleBottom,
            )
            .semantics { heading() },
        color = DictTheme.colors.ink,
    )
}

/**
 * A large choice card (onboarding step 1, picker sheets): at least 64 high, radius 16. Selected = accentContainer
 * fill, 2dp accent border and a 22dp accent check; otherwise a 1dp outline border. [label] sits in an 84dp
 * column (content colour: onAccentContainer when selected, ink otherwise), then the optional [description].
 */
@Composable
fun OptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    label: @Composable () -> Unit,
) {
    val colors = DictTheme.colors
    val shape = DictShapes.medium
    val content = if (selected) colors.onAccentContainer else colors.ink
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = OptionHeight)
            .clip(shape)
            .background(if (selected) colors.accentContainer else Color.Transparent)
            .border(
                if (selected) SelectedBorder else DictTheme.dimens.border,
                if (selected) colors.accent else colors.outline,
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = OptionSide),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OptionGapInside),
    ) {
        Box(Modifier.widthIn(min = OptionLabel)) {
            CompositionLocalProvider(LocalContentColor provides content) { label() }
        }
        Box(Modifier.weight(1f)) {
            if (description != null) {
                MixedText(
                    description,
                    DictTheme.type.secondary,
                    DictTheme.type.tibetanInline15,
                    color = if (selected) colors.onAccentContainer else colors.muted,
                )
            }
        }
        if (selected) {
            Icon(DictIcons.Bold.Check, contentDescription = null, Modifier.size(OptionCheck), tint = colors.accent)
        }
    }
}

/**
 * What a direction is for: the `direction_desc_*` copy for the three release directions, otherwise the
 * direction's own label in the UI language.
 */
@Composable
fun directionDescription(direction: Direction): String = when (direction.id) {
    "bo-en" -> stringResource(R.string.direction_desc_bo_en)
    "bo-bo" -> stringResource(R.string.direction_desc_bo_bo)
    "en-bo" -> stringResource(R.string.direction_desc_en_bo)
    else -> if (DictTheme.language == AppLanguage.BO) direction.labelBo else direction.labelEn
}

/** Picks the default direction from [directions] (option cards with descriptions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionPickerSheet(
    directions: List<Direction>,
    selected: Direction,
    onPick: (Direction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DictModalSheet(onDismiss, modifier) { DirectionPickerContent(directions, selected, onPick) }
}

@Composable
private fun DirectionPickerContent(directions: List<Direction>, selected: Direction, onPick: (Direction) -> Unit) {
    SheetTitle(stringResource(R.string.default_direction))
    OptionList {
        directions.forEach { direction ->
            OptionCard(
                selected = direction.id == selected.id,
                onClick = { onPick(direction) },
                description = directionDescription(direction),
            ) {
                DirectionLabel(direction, latinStyle = DictTheme.type.buttonLarge, tibetanStyle = DictTheme.type.tibetanGloss)
            }
        }
    }
}

/** Picks the UI language: English or བོད་ཡིག. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    selected: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DictModalSheet(onDismiss, modifier) { LanguagePickerContent(selected, onPick) }
}

@Composable
private fun LanguagePickerContent(selected: AppLanguage, onPick: (AppLanguage) -> Unit) {
    SheetTitle(stringResource(R.string.app_language))
    OptionList {
        AppLanguage.entries.forEach { language ->
            val name = stringResource(if (language == AppLanguage.BO) R.string.language_bo else R.string.language_en)
            OptionCard(selected = language == selected, onClick = { onPick(language) }) {
                MixedText(name, DictTheme.type.buttonLarge, DictTheme.type.tibetanGloss)
            }
        }
    }
}

/** Text size and Tibetan line spacing, with a live [PreviewCard] of both. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSizeSheet(
    textSize: AppTextSize,
    lineSpacing: Float,
    onTextSizeChange: (AppTextSize) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DictModalSheet(onDismiss, modifier) { TextSizeContent(textSize, lineSpacing, onTextSizeChange, onLineSpacingChange) }
}

@Composable
private fun TextSizeContent(
    textSize: AppTextSize,
    lineSpacing: Float,
    onTextSizeChange: (AppTextSize) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
) {
    SheetTitle(stringResource(R.string.text_size_spacing))
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = DictTheme.dimens.screenPadding, end = DictTheme.dimens.screenPadding, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(ControlGap),
    ) {
        PreviewCard(textSize, lineSpacing)
        SliderHeader(stringResource(R.string.text_size), textSizeLabel(textSize))
        TextSizeSlider(textSize, onTextSizeChange)
        SliderHeader(stringResource(R.string.line_spacing), lineSpacingLabel(lineSpacing, DictTheme.language))
        LineSpacingSlider(lineSpacing, onLineSpacingChange)
    }
}

/**
 * Picks the folder a saved word goes into: "All" (no folder, [selectedId] null) and each of [folders] with its
 * word count. Stateless: the caller owns the new-folder state. "New folder" calls [onStartCreate]; while
 * [creating], an inline field edits [newFolderName] through [onNewFolderNameChange] (clear a stale
 * [createError] there), Save passes the trimmed name to [onCreate], and the close button calls [onCancelCreate].
 * [createError] (e.g. a duplicate name) shows under the field.
 *
 * Test tag: `folder_name_field`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<FolderUi>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    creating: Boolean,
    newFolderName: String,
    onNewFolderNameChange: (String) -> Unit,
    onStartCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    createError: String? = null,
) {
    DictModalSheet(onDismiss, modifier) {
        FolderPickerContent(
            folders = folders,
            selectedId = selectedId,
            onPick = onPick,
            creating = creating,
            newFolderName = newFolderName,
            onNewFolderNameChange = onNewFolderNameChange,
            onStartCreate = onStartCreate,
            onCancelCreate = onCancelCreate,
            onCreate = onCreate,
            createError = createError,
        )
    }
}

@Composable
private fun FolderPickerContent(
    folders: List<FolderUi>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    creating: Boolean,
    newFolderName: String,
    onNewFolderNameChange: (String) -> Unit,
    onStartCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onCreate: (String) -> Unit,
    createError: String?,
) {
    val language = DictTheme.language
    SheetTitle(stringResource(R.string.folder_picker_title))
    OptionList {
        OptionCard(selected = selectedId == null, onClick = { onPick(null) }) {
            MixedText(stringResource(R.string.all_folder), DictTheme.type.buttonLarge, DictTheme.type.tibetanGloss)
        }
        folders.forEach { folder ->
            val count = pluralStringResource(R.plurals.saved_count, folder.count, TibetanNumerals.formatCount(folder.count, language))
            OptionCard(selected = folder.id == selectedId, onClick = { onPick(folder.id) }, description = count) {
                MixedText(folder.name, DictTheme.type.buttonLarge, DictTheme.type.tibetanGloss)
            }
        }
        if (creating) {
            val name = newFolderName.trim()
            NewFolderRow(newFolderName, onNewFolderNameChange, onSave = { if (name.isNotEmpty()) onCreate(name) }, onCancelCreate)
            if (createError != null) {
                MixedText(createError, DictTheme.type.caption, DictTheme.type.tibetanInline14, color = DictTheme.colors.danger)
            }
        } else {
            TextButton(stringResource(R.string.new_folder), onStartCreate, leading = DictIcons.Plus)
        }
    }
}

/** The picker's inline new-folder row: [FolderNameField], Save (enabled once the name is not blank) and cancel. */
@Composable
private fun NewFolderRow(name: String, onNameChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
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
 * hint); Done calls [onDone]. With [autoFocus] it takes focus when first shown (the Saved tab's new-folder sheet).
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

@Composable
private fun OptionList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = DictTheme.dimens.screenPadding, end = DictTheme.dimens.screenPadding, bottom = SheetBottom)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(OptionGap),
        content = content,
    )
}

/** Sheet contents on the sheet background, as a preview stand-in for the modal window. */
@Composable
private fun SheetPreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    ComponentPreview(edgeToEdge = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(DictShapes.sheet)
                .background(DictTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHandle()
            content()
        }
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun DirectionPickerPreview() {
    SheetPreviewFrame { DirectionPickerContent(PreviewSamples.directions, PreviewSamples.boEn, {}) }
}

@PreviewLightDark
@Composable
private fun LanguagePickerPreview() {
    SheetPreviewFrame { LanguagePickerContent(AppLanguage.EN, {}) }
}

@PreviewLightDark
@BoPreview
@Composable
private fun TextSizeSheetPreview() {
    SheetPreviewFrame { TextSizeContent(AppTextSize.DEFAULT, LineSpacing.DEFAULT, {}, {}) }
}

@PreviewLightDark
@Composable
private fun FolderPickerPreview() {
    SheetPreviewFrame {
        FolderPickerContent(
            folders = PreviewSamples.folders,
            selectedId = 2,
            onPick = {},
            creating = false,
            newFolderName = "",
            onNewFolderNameChange = {},
            onStartCreate = {},
            onCancelCreate = {},
            onCreate = {},
            createError = null,
        )
    }
}

@PreviewLightDark
@Composable
private fun FolderPickerCreatingPreview() {
    SheetPreviewFrame {
        FolderPickerContent(
            folders = PreviewSamples.folders,
            selectedId = null,
            onPick = {},
            creating = true,
            newFolderName = "Daily",
            onNewFolderNameChange = {},
            onStartCreate = {},
            onCancelCreate = {},
            onCreate = {},
            createError = "A folder with this name exists.",
        )
    }
}
