package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Gap between the text column and the badge. */
private val RowGap = 14.dp

/** The Wylie line sits 6dp into the Tibetan headword's tall line box. */
internal val WylieOverlap = 6.dp

/** The gloss sits 4dp below the headword block. */
private val GlossTop = 4.dp

/** [text] with [range] (inclusive, clamped to the text) in [highlight]; no highlight when [range] is null. */
private fun highlighted(text: String, range: IntRange?, highlight: SpanStyle): AnnotatedString {
    val bounds = range?.clampTo(text.length) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(highlight, bounds.first, bounds.second)
    }
}

/**
 * A Wylie transliteration in [style] (13 italic) and [color] (muted) with the matched [matchRange] (inclusive;
 * clamped, so odd queries never crash) drawn at 600 in the accent.
 */
@Composable
fun WylieLine(
    wylie: String,
    matchRange: IntRange?,
    modifier: Modifier = Modifier,
    style: TextStyle = DictTheme.type.wylie,
    color: Color = DictTheme.colors.muted,
) {
    val accent = DictTheme.colors.accent
    val text = remember(wylie, matchRange, accent) {
        highlighted(wylie, matchRange, SpanStyle(color = accent, fontWeight = FontWeight.SemiBold))
    }
    Text(text, modifier, color = color, style = style)
}

/**
 * A search result: Tibetan headword (Jomolhari 24/1.8) or Latin headword (16/500) with its matched part in the
 * accent, the [WylieLine] tucked 6dp under it, a one-line 14 gloss, and an outline [DirectionBadge] when the hit
 * comes from another direction. [highlighted] (the exact match on top) fills the row with surface1 and drops
 * the divider; otherwise a divider is drawn unless [showDivider] is false (last row).
 *
 * Test tags: `entry_row_wylie`, `entry_row_badge`.
 */
@Composable
fun EntryRow(
    result: SearchResultUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    showDivider: Boolean = true,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.surface1 else Color.Transparent)
            .bottomDivider(colors.divider, show = showDivider && !highlighted)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.entryRowPaddingHorizontal, vertical = dimens.entryRowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Column(Modifier.weight(1f)) {
            Headword(result)
            if (result.wylie != null) {
                WylieLine(result.wylie, result.matchRange, Modifier.pullUp(WylieOverlap).testTag(WYLIE_TAG))
            }
            MixedText(
                result.gloss,
                type.bodySmallResultGloss,
                type.tibetanInline16,
                Modifier.padding(top = GlossTop),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (result.showBadge) DirectionBadge(result.direction, Modifier.testTag(BADGE_TAG))
    }
}

/** Tibetan headwords mark the match with colour only (Jomolhari has one weight); Latin ones also with 600. */
@Composable
private fun Headword(result: SearchResultUi) {
    val accent = DictTheme.colors.accent
    val style = if (result.headwordIsTibetan) DictTheme.type.tibetanRow else DictTheme.type.headwordLatin
    val text = remember(result.headword, result.headwordMatchRange, result.headwordIsTibetan, accent) {
        val highlight = if (result.headwordIsTibetan) {
            SpanStyle(color = accent)
        } else {
            SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
        }
        highlighted(result.headword, result.headwordMatchRange, highlight)
    }
    Text(text, color = DictTheme.colors.ink, style = style)
}

private const val WYLIE_TAG = "entry_row_wylie"
private const val BADGE_TAG = "entry_row_badge"

@PreviewLightDark
@BoPreview
@Composable
private fun EntryRowPreview() {
    val s = PreviewSamples
    ComponentPreview(edgeToEdge = true) {
        EntryRow(s.tashi.copy(showBadge = false), onClick = {}, highlighted = true)
        EntryRow(s.tashiDelek, onClick = {})
        EntryRow(s.tashi.copy(entryId = 9, wylie = null, matchRange = null), onClick = {})
        EntryRow(s.compassion, onClick = {}, showDivider = false)
    }
}

@ExtremeScalePreview
@Composable
private fun EntryRowExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        EntryRow(PreviewSamples.tashi, onClick = {}, highlighted = true)
        EntryRow(PreviewSamples.compassion, onClick = {})
    }
}
