package com.kharagedition.tibetandictionary.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.DictIconButton
import com.kharagedition.tibetandictionary.ui.components.DirectionBadge
import com.kharagedition.tibetandictionary.ui.components.FolderPickerSheet
import com.kharagedition.tibetandictionary.ui.components.FullEmptyState
import com.kharagedition.tibetandictionary.ui.components.LatinText
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.OutlinedButton
import com.kharagedition.tibetandictionary.ui.components.SeeAlsoRow
import com.kharagedition.tibetandictionary.ui.components.SenseCard
import com.kharagedition.tibetandictionary.ui.components.SkeletonEntryRow
import com.kharagedition.tibetandictionary.ui.components.SkeletonPreset
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.components.appendMixed
import com.kharagedition.tibetandictionary.ui.components.pullUp
import com.kharagedition.tibetandictionary.ui.components.tallestLineHeight
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.delay

/** Toolbar padding (8 top, 12 sides), and the body's top padding under it. */
private val ToolbarTop = 8.dp
private val ToolbarSide = 12.dp
internal val BodyTop = 8.dp

/** Header: 2dp between badge row, headword and pronunciation; the pronunciation row tucks 8dp into the headword. */
private val HeaderGap = 2.dp
private val PronunciationOverlap = 8.dp
private val PronunciationGap = 10.dp

/** Footer: 4 above the text, 16 under the touch target. */
private val FooterTop = 4.dp
private val FooterBottom = 16.dp

/** A local entry reads in a few ms; the skeleton only appears if it takes longer than this. */
private const val SKELETON_DELAY_MS = 250L

internal const val SCREEN_TAG = "entry_screen"
internal const val SAVE_TAG = "entry_save"
internal const val AUDIO_TAG = "entry_audio"
internal const val REPORT_TAG = "entry_report"
internal const val LOADING_TAG = "entry_loading"
internal const val MISSING_BACK_TAG = "entry_missing_back"

/** `entry_related_{id}`, or `entry_related_search_{index}` for a link that opens a search. */
internal fun relatedTag(item: RelatedUi, index: Int): String =
    item.entryId?.let { "entry_related_$it" } ?: "entry_related_search_$index"

internal fun senseTag(index: Int): String = "entry_sense_$index"

/** Back, then heart (accent and filled when saved), copy and share; the entry actions only once it is shown. */
@Composable
internal fun EntryToolbar(entryShown: Boolean, isSaved: Boolean, onEvent: (EntryEvent) -> Unit) {
    val colors = DictTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ToolbarSide, top = ToolbarTop, end = ToolbarSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DictIconButton(DictIcons.Back, stringResource(R.string.back), { onEvent(EntryEvent.Back) })
        Spacer(Modifier.weight(1f))
        if (entryShown) {
            DictIconButton(
                if (isSaved) DictIcons.HeartFilled else DictIcons.Heart,
                stringResource(if (isSaved) R.string.saved_cd else R.string.save),
                { onEvent(EntryEvent.ToggleSave) },
                Modifier.testTag(SAVE_TAG),
                tint = if (isSaved) colors.accent else colors.ink,
            )
            DictIconButton(DictIcons.Copy, stringResource(R.string.copy), { onEvent(EntryEvent.Copy) })
            DictIconButton(DictIcons.Share, stringResource(R.string.share), { onEvent(EntryEvent.Share) })
        }
    }
}

/**
 * Tonal direction badge with the parts of speech ("adj. · n.") and shared label; the headword (Jomolhari 46/1.8,
 * or 24/600 Latin); then Wylie (15 italic), "· phonetic" (13) and a 36dp audio button, each only when present.
 */
@Composable
internal fun EntryHeader(entry: EntryUi, onPlayAudio: () -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(HeaderGap)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        ) {
            DirectionBadge(entry.direction, tonal = true)
            val caption = (entry.partsOfSpeech + listOfNotNull(entry.label)).joinToString(DOT_SEPARATOR)
            if (caption.isNotEmpty()) MixedText(caption, type.caption, type.tibetanInline14, color = colors.muted)
        }
        val heading = Modifier.semantics { heading() }
        if (entry.headwordIsTibetan) {
            TibetanText(entry.headword, heading, style = type.tibetanEntryHeadword, color = colors.ink)
        } else {
            MixedText(entry.headword, type.screenTitle, type.tibetanEntryHeadword, heading, color = colors.ink)
        }
        if (entry.wylie != null || entry.phonetic != null || entry.hasAudio) {
            // Wraps instead of squeezing the audio button out at large text sizes.
            FlowRow(
                if (entry.headwordIsTibetan) Modifier.pullUp(PronunciationOverlap) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(PronunciationGap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry.wylie != null) LatinText(entry.wylie, style = type.wylieEntry, color = colors.muted)
                if (entry.phonetic != null) {
                    val phonetic = if (entry.wylie != null) "· ${entry.phonetic}" else entry.phonetic
                    LatinText(phonetic, style = type.secondary, color = colors.muted)
                }
                if (entry.hasAudio) {
                    DictIconButton(
                        DictIcons.Speaker,
                        stringResource(R.string.play_audio),
                        onPlayAudio,
                        Modifier.testTag(AUDIO_TAG),
                        size = DictTheme.dimens.iconButtonSmall,
                        tint = colors.accent,
                        filled = true,
                    )
                }
            }
        }
    }
}

/** Sense cards 8dp apart, then the truncation note (12 muted) when the entry was shortened. */
@Composable
internal fun EntrySenses(entry: EntryUi) {
    Column(verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
        entry.senses.forEachIndexed { index, sense -> SenseCard(sense, Modifier.testTag(senseTag(index))) }
        if (entry.isTruncated) {
            MixedText(
                stringResource(R.string.entry_truncated),
                DictTheme.type.caption,
                DictTheme.type.tibetanInline14,
                color = DictTheme.colors.muted,
            )
        }
    }
}

/**
 * "See also" over [SeeAlsoRow]s, as `SeeAlsoSection` draws it, with a test tag on each row ([relatedTag]).
 */
@Composable
internal fun EntryRelated(items: List<RelatedUi>, onOpen: (RelatedUi) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
        MixedText(
            stringResource(R.string.see_also),
            DictTheme.type.sectionLabel,
            DictTheme.type.tibetanInline15,
            Modifier.semantics { heading() },
            color = DictTheme.colors.muted,
        )
        Column {
            items.forEachIndexed { index, item ->
                SeeAlsoRow(
                    item,
                    { onOpen(item) },
                    Modifier.testTag(relatedTag(item, index)),
                    showDivider = index < items.lastIndex,
                )
            }
        }
    }
}

/** "Something wrong? Report a correction" (12 muted, the link 500 accent), centred, one 48dp touch target. */
@Composable
internal fun ReportCorrectionFooter(onClick: () -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val prefix = stringResource(R.string.report_correction_prefix)
    val link = stringResource(R.string.report_correction)
    val text = remember(prefix, link, type, colors) {
        buildAnnotatedString {
            appendMixed(prefix, null, type.tibetanInline14)
            append(" ")
            appendMixed(
                link,
                type.captionMedium,
                type.tibetanInline14,
                latinColor = colors.accent,
                tibetanColor = colors.accent,
            )
        }
    }
    val style = if (TibetanScript.hasTibetan(text)) {
        type.caption.copy(lineHeight = tallestLineHeight(type.caption, type.tibetanInline14))
    } else {
        type.caption
    }
    Box(
        Modifier
            .padding(bottom = FooterBottom)
            .fillMaxWidth()
            .heightIn(min = DictTheme.dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = FooterTop)
            .testTag(REPORT_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.muted, style = style, textAlign = TextAlign.Center)
    }
}

/** Two skeleton rows, shown only when reading the entry takes longer than [SKELETON_DELAY_MS]. */
@Composable
internal fun EntryLoading(modifier: Modifier = Modifier) {
    val immediate = LocalInspectionMode.current
    var visible by remember { mutableStateOf(immediate) }
    if (!visible) {
        LaunchedEffect(Unit) {
            delay(SKELETON_DELAY_MS)
            visible = true
        }
    }
    Column(modifier.fillMaxWidth().testTag(LOADING_TAG)) {
        if (visible) {
            SkeletonEntryRow(preset = SkeletonPreset.First)
            SkeletonEntryRow(preset = SkeletonPreset.Second)
        }
    }
}

/** The id is not in the installed dictionary: a centred notice with a way back. */
@Composable
internal fun EntryMissing(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FullEmptyState(
            DictIcons.EmptyState.SearchOff,
            title = stringResource(R.string.entry_missing),
            body = "",
            iconTint = DictTheme.colors.muted,
        ) {
            OutlinedButton(
                stringResource(R.string.back),
                onBack,
                Modifier.testTag(MISSING_BACK_TAG),
                size = ButtonSize.Medium,
            )
        }
    }
}

/** The hoisted [FolderPickerSheet] driven by [picker]. */
@Composable
internal fun EntryFolderPicker(picker: FolderPickerUi, onEvent: (EntryEvent) -> Unit) {
    FolderPickerSheet(
        folders = picker.folders,
        selectedId = picker.selectedId,
        onPick = { onEvent(EntryEvent.PickFolder(it)) },
        creating = picker.creating,
        newFolderName = picker.newFolderName,
        onNewFolderNameChange = { onEvent(EntryEvent.NewFolderNameChanged(it)) },
        onStartCreate = { onEvent(EntryEvent.StartCreateFolder) },
        onCancelCreate = { onEvent(EntryEvent.CancelCreateFolder) },
        onCreate = { onEvent(EntryEvent.CreateFolder(it)) },
        onDismiss = { onEvent(EntryEvent.DismissFolderPicker) },
        createError = picker.createError,
    )
}
