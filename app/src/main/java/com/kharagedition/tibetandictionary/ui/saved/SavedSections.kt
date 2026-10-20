package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.domain.SavedPeriod
import com.kharagedition.tibetandictionary.ui.components.AddChip
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.ChipSize
import com.kharagedition.tibetandictionary.ui.components.DictIconButton
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.FolderPickerSheet
import com.kharagedition.tibetandictionary.ui.components.FullEmptyState
import com.kharagedition.tibetandictionary.ui.components.InlineEmptyState
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.Overline
import com.kharagedition.tibetandictionary.ui.components.ScreenTitle
import com.kharagedition.tibetandictionary.ui.components.SearchField
import com.kharagedition.tibetandictionary.ui.components.SearchFieldVariant
import com.kharagedition.tibetandictionary.ui.components.TextChip
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.components.bottomDivider
import com.kharagedition.tibetandictionary.ui.components.pullUp
import com.kharagedition.tibetandictionary.ui.entry.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Header: 16 above the title, 14 between title, search field and chips (1i). */
private val HeaderTop = 16.dp
private val HeaderGap = 14.dp

/**
 * A 32dp chip stands in a 48dp touch target, 8dp of which falls above and below it; the chip row and the list are
 * pulled in by that much so the visible gaps stay 14 and 12.
 */
private val ChipTouchInset = 8.dp
internal val ChipRowTop = HeaderGap - ChipTouchInset
internal val ListTop = 12.dp - ChipTouchInset

/** Section header padding: 6 above the first, 14 above the others, 6 below. */
private val GroupFirstTop = 6.dp
private val GroupTop = 14.dp
private val GroupBottom = 6.dp

/** Row: 12 between the text and the heart; the secondary line tucks 5dp into a Tibetan headword's line box. */
private val RowGap = 12.dp
private val SecondaryOverlap = 5.dp

/** A Latin headword's line: 6 above, 2 below (1i). */
private val LatinHeadwordTop = 6.dp
private val LatinHeadwordBottom = 2.dp

/** New-folder sheet: title padding and the gap between its rows. */
private val SheetTitleTop = 8.dp
private val SheetGap = 12.dp
private val SheetBottom = 24.dp

internal const val SCREEN_TAG = "saved_screen"
internal const val SEARCH_TAG = "saved_search"
internal const val ALL_CHIP_TAG = "folder_chip_all"
internal const val ADD_FOLDER_TAG = "saved_add_folder"
internal const val EMPTY_CTA_TAG = "saved_empty_cta"
internal const val NO_MATCHES_TAG = "saved_no_matches"
internal const val FOLDER_NAME_TAG = "saved_folder_name"

internal fun folderChipTag(folderId: Long): String = "folder_chip_$folderId"

internal fun rowTag(entryId: Long): String = "saved_row_$entryId"

internal fun groupTag(period: SavedPeriod): String = "saved_group_" + when (period) {
    SavedPeriod.TODAY -> "today"
    SavedPeriod.THIS_WEEK -> "week"
    SavedPeriod.THIS_MONTH -> "month"
    SavedPeriod.EARLIER -> "earlier"
}

/**
 * "Saved ཉར་ཚགས།" with the word count once something is saved, and the search-within field from
 * [SEARCH_MIN_COUNT] words on.
 */
@Composable
internal fun SavedHeader(state: SavedUiState, onEvent: (SavedEvent) -> Unit) {
    val dimens = DictTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = dimens.screenPadding, top = HeaderTop, end = dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        val count: (@Composable () -> Unit)? = if (!state.loading && !state.isEmpty) {
            { WordCount(state.totalCount) }
        } else {
            null
        }
        ScreenTitle(
            title = stringResource(R.string.saved_title),
            gloss = stringResource(R.string.saved_gloss),
            trailing = count,
        )
        if (state.showSearch) {
            SearchField(
                value = state.query,
                onValueChange = { onEvent(SavedEvent.QueryChanged(it)) },
                placeholder = stringResource(R.string.search_placeholder_saved),
                modifier = Modifier.testTag(SEARCH_TAG),
                variant = SearchFieldVariant.Compact,
            )
        }
    }
}

/** "42 words" (12 muted; Tibetan digits in bo). */
@Composable
private fun WordCount(count: Int) {
    val text = pluralStringResource(R.plurals.saved_count, count, TibetanNumerals.formatCount(count, DictTheme.language))
    MixedText(text, DictTheme.type.caption, DictTheme.type.tibetanInline14, color = DictTheme.colors.muted)
}

/** "All N", then each folder with its count, then the dashed "+" that adds a folder; scrolls sideways. */
@Composable
internal fun FolderChips(state: SavedUiState, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    val language = DictTheme.language
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DictTheme.dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextChip(
            "${stringResource(R.string.all_folder)} ${TibetanNumerals.formatCount(state.totalCount, language)}",
            { onEvent(SavedEvent.SelectFolder(null)) },
            Modifier.testTag(ALL_CHIP_TAG),
            selected = state.selectedFolderId == null,
            size = ChipSize.Small,
        )
        state.folders.forEach { folder ->
            TextChip(
                "${folder.name} ${TibetanNumerals.formatCount(folder.count, language)}",
                { onEvent(SavedEvent.SelectFolder(folder.id)) },
                Modifier.testTag(folderChipTag(folder.id)),
                selected = folder.id == state.selectedFolderId,
                size = ChipSize.Small,
            )
        }
        AddChip(
            { onEvent(SavedEvent.StartAddFolder) },
            stringResource(R.string.new_folder),
            Modifier.testTag(ADD_FOLDER_TAG),
            size = ChipSize.Small,
        )
    }
}

/** A section header ("TODAY", 11/600 caps, muted); [first] sits closer to the chips. */
@Composable
internal fun GroupHeader(period: SavedPeriod, first: Boolean) {
    val label = when (period) {
        SavedPeriod.TODAY -> R.string.saved_group_today
        SavedPeriod.THIS_WEEK -> R.string.saved_group_week
        SavedPeriod.THIS_MONTH -> R.string.saved_group_month
        SavedPeriod.EARLIER -> R.string.saved_group_earlier
    }
    Overline(
        stringResource(label),
        Modifier
            .fillMaxWidth()
            .padding(
                start = DictTheme.dimens.screenPadding,
                top = if (first) GroupFirstTop else GroupTop,
                end = DictTheme.dimens.screenPadding,
                bottom = GroupBottom,
            )
            .semantics { heading() }
            .testTag(groupTag(period)),
    )
}

/**
 * A saved word (padding 10×20): the headword (Jomolhari 22/1.8, or 16/500 Latin), the secondary line (13 muted,
 * Tibetan runs in Jomolhari 16, one line) and a filled accent heart that removes it. Tapping the row opens it.
 */
@Composable
internal fun SavedRow(
    item: SavedItemUi,
    onOpen: () -> Unit,
    onUnsave: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .bottomDivider(colors.divider, show = showDivider)
            .clickable(onClick = onOpen)
            .padding(horizontal = dimens.savedRowPaddingHorizontal, vertical = dimens.savedRowPaddingVertical)
            .testTag(rowTag(item.entryId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Column(Modifier.weight(1f)) {
            if (item.headwordIsTibetan) {
                TibetanText(item.headword, style = type.tibetanSaved, color = colors.ink)
            } else {
                MixedText(
                    item.headword,
                    type.headwordLatin,
                    type.tibetanSaved,
                    Modifier.padding(top = LatinHeadwordTop, bottom = LatinHeadwordBottom),
                    color = colors.ink,
                )
            }
            if (item.secondary.isNotEmpty()) {
                MixedText(
                    item.secondary,
                    type.secondary,
                    type.tibetanInline16,
                    if (item.headwordIsTibetan) Modifier.pullUp(SecondaryOverlap) else Modifier,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DictIconButton(
            DictIcons.HeartFilled,
            stringResource(R.string.unsave),
            onUnsave,
            size = dimens.iconButtonMedium,
            tint = colors.accent,
        )
    }
}

/** A filter that leaves nothing: the dashed inline notice. */
@Composable
internal fun SavedNoMatches(modifier: Modifier = Modifier) {
    InlineEmptyState(
        DictIcons.SearchOff,
        title = stringResource(R.string.no_matches),
        body = stringResource(R.string.saved_no_matches_body),
        modifier = modifier.testTag(NO_MATCHES_TAG),
    )
}

/**
 * Nothing saved yet (3c): the heart in a 72 circle, the title, the hint (plus the flashcards sentence when
 * [learnEnabled]) and, when [wotd] is given, "Save today's word {headword}".
 */
@Composable
fun SavedEmpty(
    wotd: WordOfTheDayUi?,
    onSaveWotd: () -> Unit,
    modifier: Modifier = Modifier,
    learnEnabled: Boolean = Features.LEARN,
) {
    val body = buildString {
        append(stringResource(R.string.saved_empty_body))
        if (learnEnabled) append(' ').append(stringResource(R.string.saved_empty_body_learn))
    }
    val action: (@Composable () -> Unit)? = if (wotd != null) {
        {
            FilledButton(
                stringResource(R.string.save_todays_word, wotd.headword),
                onSaveWotd,
                Modifier.testTag(EMPTY_CTA_TAG),
                size = ButtonSize.Medium,
            )
        }
    } else {
        null
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FullEmptyState(
            DictIcons.EmptyState.Heart,
            title = stringResource(R.string.saved_empty_title),
            body = body,
            action = action,
        )
    }
}

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
