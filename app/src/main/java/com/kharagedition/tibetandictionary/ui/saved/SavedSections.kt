package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.domain.SavedPeriod
import com.kharagedition.tibetandictionary.ui.components.AddChip
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.ChipSize
import com.kharagedition.tibetandictionary.ui.components.DictIconButton
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.FullEmptyState
import com.kharagedition.tibetandictionary.ui.components.InlineEmptyState
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.Overline
import com.kharagedition.tibetandictionary.ui.components.ScreenTitle
import com.kharagedition.tibetandictionary.ui.components.SearchField
import com.kharagedition.tibetandictionary.ui.components.SearchFieldVariant
import com.kharagedition.tibetandictionary.ui.components.SegmentedTabs
import com.kharagedition.tibetandictionary.ui.components.TextChip
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.components.bottomDivider
import com.kharagedition.tibetandictionary.ui.components.pullUp
import com.kharagedition.tibetandictionary.ui.entry.CenteredScrollColumn
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.delay

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

/** The Translations list starts 8 under the switch (the first section header adds its 6); a row is 12 by 20. */
private val TranslationListTop = 8.dp
private val TranslationRowVertical = 12.dp
private val TranslationRowGap = 2.dp
private val TranslationMetaTop = 2.dp
private val SwipeIcon = 22.dp

/** How long a swiped-away translation waits for the list to drop it before it slides back (the removal failed). */
private const val SWIPE_RESET_MS = 800L

/** A Latin headword's line: 6 above, 2 below (1i). */
private val LatinHeadwordTop = 6.dp
private val LatinHeadwordBottom = 2.dp

internal const val SCREEN_TAG = "saved_screen"
internal const val SEARCH_TAG = "saved_search"
internal const val ALL_CHIP_TAG = "folder_chip_all"
internal const val ADD_FOLDER_TAG = "saved_add_folder"
internal const val EMPTY_CTA_TAG = "saved_empty_cta"
internal const val NO_MATCHES_TAG = "saved_no_matches"
internal const val WORDS_TAB_TAG = "saved_tab_words"
internal const val TRANSLATIONS_TAB_TAG = "saved_tab_translations"
internal const val TRANSLATIONS_EMPTY_TAG = "saved_translations_empty"

internal fun translationTag(id: Long): String = "saved_translation_$id"

internal fun folderChipTag(folderId: Long): String = "folder_chip_$folderId"

internal fun rowTag(entryId: Long): String = "saved_row_$entryId"

internal fun groupTag(period: SavedPeriod): String = "saved_group_" + when (period) {
    SavedPeriod.TODAY -> "today"
    SavedPeriod.THIS_WEEK -> "week"
    SavedPeriod.THIS_MONTH -> "month"
    SavedPeriod.EARLIER -> "earlier"
}

/**
 * "Saved ཉར་ཚགས།" (with the word count on the Words tab once something is saved), the Words | Translations switch
 * once the words are read, and on the Words tab the search-within field from [SEARCH_MIN_COUNT] words on.
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
        val words = state.tab == SavedTab.WORDS
        val count: (@Composable () -> Unit)? = if (words && !state.loading && !state.isEmpty) {
            { WordCount(state.totalCount) }
        } else {
            null
        }
        ScreenTitle(
            title = stringResource(R.string.saved_title),
            gloss = stringResource(R.string.saved_gloss),
            trailing = count,
        )
        if (!state.loading) SavedTabs(state, onEvent)
        if (words && state.showSearch) {
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

/** "Words · 24 | Translations · 6" (Tibetan digits in bo). */
@Composable
private fun SavedTabs(state: SavedUiState, onEvent: (SavedEvent) -> Unit) {
    val language = DictTheme.language
    val tabs = SavedTab.entries
    SegmentedTabs(
        options = listOf(
            stringResource(R.string.saved_tab_words, TibetanNumerals.formatCount(state.totalCount, language)),
            stringResource(
                R.string.saved_tab_translations,
                TibetanNumerals.formatCount(state.translationCount, language),
            ),
        ),
        selectedIndex = tabs.indexOf(state.tab),
        onSelect = { onEvent(SavedEvent.SelectTab(tabs[it])) },
        tags = listOf(WORDS_TAB_TAG, TRANSLATIONS_TAB_TAG),
    )
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
            stringResource(
                R.string.saved_folder_chip,
                stringResource(R.string.all_folder),
                TibetanNumerals.formatCount(state.totalCount, language),
            ),
            { onEvent(SavedEvent.SelectFolder(null)) },
            Modifier.testTag(ALL_CHIP_TAG),
            selected = state.selectedFolderId == null,
            size = ChipSize.Small,
        )
        state.folders.forEach { folder ->
            TextChip(
                stringResource(R.string.saved_folder_chip, folder.name, TibetanNumerals.formatCount(folder.count, language)),
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

/** A filter that leaves nothing: the dashed inline notice, top-aligned, scrolling when large text outgrows the space. */
@Composable
internal fun SavedNoMatches(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        InlineEmptyState(
            DictIcons.SearchOff,
            title = stringResource(R.string.no_matches),
            body = stringResource(R.string.saved_no_matches_body),
            modifier = Modifier.testTag(NO_MATCHES_TAG),
        )
    }
}

/**
 * Nothing saved yet (3c): the heart in a 72 circle, the title, the hint (plus the flashcards sentence when
 * [learnEnabled]) and, when [wotd] is given, "Save today's word {headword}"; centred, and scrolls when large text
 * outgrows the screen.
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
    CenteredScrollColumn(modifier) {
        FullEmptyState(
            DictIcons.EmptyState.Heart,
            title = stringResource(R.string.saved_empty_title),
            body = body,
            action = action,
        )
    }
}

/** The saved translations in sections; every row but the very last has a divider. */
@Composable
internal fun SavedTranslationList(
    groups: List<SavedTranslationGroupUi>,
    onEvent: (SavedEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(top = TranslationListTop)) {
        groups.forEachIndexed { groupIndex, group ->
            item(key = group.period) { GroupHeader(group.period, first = groupIndex == 0) }
            val lastGroup = groupIndex == groups.lastIndex
            itemsIndexed(group.items, key = { _, item -> "translation_${item.id}" }) { index, item ->
                SavedTranslationRow(
                    item,
                    onOpen = { onEvent(SavedEvent.OpenTranslation(item.id)) },
                    onRemove = { onEvent(SavedEvent.RemoveTranslation(item.id)) },
                    showDivider = !(lastGroup && index == group.items.lastIndex),
                )
            }
        }
    }
}

/**
 * A saved translation (1y): the source in up to two lines (Jomolhari 17 when Tibetan), the translation in up to two
 * muted lines, and the direction ("བོད → EN"). Tapping opens it in Translate; swiping it left, a long press's
 * "Remove from Saved", or the screen reader's, removes it. A row the list still has a moment after the swipe (the
 * removal failed) slides back.
 */
@Composable
internal fun SavedTranslationRow(
    item: SavedTranslationUi,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue != SwipeToDismissBoxValue.Settled) {
            delay(SWIPE_RESET_MS)
            swipe.reset()
        }
    }
    val removeLabel = stringResource(R.string.unsave)
    var menuOpen by remember { mutableStateOf(false) }
    SwipeToDismissBox(
        swipe,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.accentContainer)
                    .padding(horizontal = DictTheme.dimens.savedRowPaddingHorizontal),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    DictIcons.Heart,
                    contentDescription = null,
                    Modifier.size(SwipeIcon),
                    tint = colors.onAccentContainer,
                )
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .bottomDivider(colors.divider, show = showDivider)
                .combinedClickable(role = Role.Button, onLongClick = { menuOpen = true }, onClick = onOpen)
                .semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true }) }
                .padding(horizontal = DictTheme.dimens.savedRowPaddingHorizontal, vertical = TranslationRowVertical)
                .testTag(translationTag(item.id)),
            verticalArrangement = Arrangement.spacedBy(TranslationRowGap),
        ) {
            if (item.sourceIsTibetan) {
                TibetanText(
                    item.source,
                    style = type.tibetanChip,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                MixedText(
                    item.source,
                    type.body,
                    type.tibetanChip,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MixedText(
                item.translation,
                type.bodySmall,
                type.tibetanInline16,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MixedText(
                "${langToken(item.pair.source)} → ${langToken(item.pair.target)}",
                type.tiny,
                type.tibetanSegment,
                Modifier.padding(top = TranslationMetaTop),
                color = colors.muted,
            )
            // Styled like the direction switcher's menu.
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = DictShapes.small,
                containerColor = colors.surface1,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(DictTheme.dimens.border, colors.outline),
            ) {
                DropdownMenuItem(
                    text = { MixedText(removeLabel, type.body, type.tibetanInline16, color = colors.ink) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                    modifier = Modifier.testTag("saved_translation_menu_remove"),
                    leadingIcon = { Icon(DictIcons.Heart, contentDescription = null, tint = colors.accent) },
                )
            }
        }
    }
}

/** A language as the direction labels write it ("བོད", "རྫོང", "EN"), in every UI language. */
private fun langToken(lang: Lang): String = when (lang) {
    Lang.BO -> "བོད"
    Lang.DZ -> "རྫོང"
    Lang.EN -> "EN"
}

/** No saved translations: the translate icon in a 72 circle, the title and the hint; centred, scrolling if large. */
@Composable
internal fun SavedTranslationsEmpty(modifier: Modifier = Modifier) {
    CenteredScrollColumn(modifier.testTag(TRANSLATIONS_EMPTY_TAG)) {
        FullEmptyState(
            DictIcons.EmptyState.Translate,
            title = stringResource(R.string.saved_translations_empty_title),
            body = stringResource(R.string.saved_translations_empty_body),
        )
    }
}
