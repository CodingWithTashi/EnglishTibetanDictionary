package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Duration of the selection thumb's slide (and the segment label colour change). */
private const val THUMB_MS = 180

/** Chevron after "More". */
private val MoreChevron = 16.dp

/** Gap between "More" and its chevron. */
private val MoreGap = 4.dp

/** Languages whose token is not Latin-lettered (it touches the arrow). */
private val NonLatinTokens = setOf("bo", "zh")

/** Display token of a language code: bo → "བོད", en → "EN", zh → "中", anything else upper-cased. */
private fun languageToken(code: String): String = when (code.lowercase()) {
    "bo" -> "བོད"
    "en" -> "EN"
    "zh" -> "中"
    else -> code.uppercase()
}

/** True when the token for [code] is written in Latin letters (spaced away from the arrow). */
private fun isLatinToken(code: String): Boolean = code.lowercase() !in NonLatinTokens

/**
 * The short label of [direction]: "བོད→ EN", "བོད→བོད", "EN →བོད", "中→བོད". A Latin token is separated from
 * the arrow by a space; Tibetan and Han tokens touch it.
 */
fun directionLabelText(direction: Direction): String = buildString {
    append(languageToken(direction.sourceLang))
    if (isLatinToken(direction.sourceLang)) append(' ')
    append('→')
    if (isLatinToken(direction.targetLang)) append(' ')
    append(languageToken(direction.targetLang))
}

/** Zero-width space: a line-break opportunity that draws and reads as nothing. */
private const val BREAK_OPPORTUNITY = '\u200B'

/**
 * [label] with a break opportunity after an arrow that touches the next token. The arrow and Tibetan letters
 * share a line-break class, so "བོད→བོད" otherwise has nowhere to wrap and splits inside a syllable at large
 * text sizes.
 */
private fun wrappable(label: String): String = buildString(label.length + 1) {
    label.forEachIndexed { i, c ->
        append(c)
        if (c == '→' && label.getOrNull(i + 1)?.isWhitespace() == false) append(BREAK_OPPORTUNITY)
    }
}

/**
 * [directionLabelText] with "བོད" in Jomolhari ([tibetanStyle]) and the rest in [latinStyle]. At large text
 * sizes it wraps after the arrow, never inside a token; accessibility services read the plain label.
 */
@Composable
fun DirectionLabel(
    direction: Direction,
    modifier: Modifier = Modifier,
    latinStyle: TextStyle = DictTheme.type.label,
    tibetanStyle: TextStyle = DictTheme.type.tibetanInline15,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
) {
    val label = directionLabelText(direction)
    Box(modifier.clearAndSetSemantics { text = AnnotatedString(label) }, contentAlignment = Alignment.Center) {
        MixedText(
            wrappable(label),
            latinStyle,
            tibetanStyle,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Small direction tag (10/600 tracked + Jomolhari 11, radius 6): [tonal] = accentContainer fill (entry header,
 * flashcard); otherwise a 1dp outline border with muted text (result rows, see-also rows).
 */
@Composable
fun DirectionBadge(direction: Direction, modifier: Modifier = Modifier, tonal: Boolean = false) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val shape = DictShapes.extraSmall
    Box(
        modifier
            .clip(shape)
            .then(
                if (tonal) {
                    Modifier.background(colors.accentContainer)
                } else {
                    Modifier.border(dimens.border, colors.outline, shape)
                },
            )
            .padding(
                horizontal = if (tonal) dimens.badgePaddingHorizontalTonal else dimens.badgePaddingHorizontalOutline,
                vertical = dimens.badgePaddingVertical,
            ),
    ) {
        DirectionLabel(
            direction,
            latinStyle = DictTheme.type.badge,
            tibetanStyle = DictTheme.type.tibetanBadge,
            color = if (tonal) colors.onAccentContainer else colors.muted,
            maxLines = 1,
        )
    }
}

/**
 * Three equal segments on a surface1 track (40, or 36 [compact]) with an accent thumb that slides to [selected]
 * in 180 ms; the slide is the only sign that the direction was auto-detected. When [DirectionSlots.overflow] is
 * not empty the last segment reads "More ▾" (or [DirectionSlots.moreLabel]) and opens a menu of the overflow
 * directions. The track's state description is the selected direction's label; [autoSelected] makes it a polite
 * live region, so an auto-detected change is announced.
 *
 * Test tags: `direction_switcher`, `direction_segment_{id}`, `direction_segment_more`, `direction_thumb`,
 * `direction_menu_{id}`.
 */
@Composable
fun DirectionSwitcher(
    slots: DirectionSlots,
    selected: Direction,
    onSelect: (Direction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    autoSelected: Boolean = false,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val hasMore = slots.overflow.isNotEmpty()
    val count = slots.fixed.size + if (hasMore) 1 else 0
    if (count == 0) return
    val selectedIndex = slots.fixed.indexOfFirst { it.id == selected.id }.takeIf { it >= 0 }
        ?: slots.fixed.size.takeIf { hasMore && slots.overflow.any { it.id == selected.id } }
    val gap = dimens.switcherPadding
    val segmentShape = RoundedCornerShape(dimens.switcherSegmentRadius)
    val trackHeight = if (compact) dimens.switcherCompact else dimens.switcher
    // Radius of the nominal track (20 / 18), not of a track grown by large text, so the thumb keeps its corners.
    val trackShape = RoundedCornerShape(trackHeight / 2)
    val selectedLabel = directionLabelText(selected)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = trackHeight)
            .clip(trackShape)
            .background(colors.surface1)
            .padding(dimens.switcherPadding)
            .semantics {
                // The live region announces this description when an auto-selection changes it.
                stateDescription = selectedLabel
                if (autoSelected) liveRegion = LiveRegionMode.Polite
            }
            .testTag("direction_switcher"),
    ) {
        val segmentWidth = (maxWidth - gap * (count - 1)) / count
        val thumbX by animateDpAsState(
            targetValue = (segmentWidth + gap) * (selectedIndex ?: 0),
            animationSpec = tween(THUMB_MS),
            label = "directionThumb",
        )
        if (selectedIndex != null) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset { IntOffset(thumbX.roundToPx(), 0) }
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .background(colors.accent, segmentShape)
                        .testTag("direction_thumb"),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            slots.fixed.forEachIndexed { index, direction ->
                Segment(
                    selected = index == selectedIndex,
                    onClick = { onSelect(direction) },
                    role = Role.Tab,
                    tag = "direction_segment_${direction.id}",
                    compact = compact,
                    modifier = Modifier.weight(1f),
                ) { color, style -> DirectionLabel(direction, latinStyle = style, color = color) }
            }
            if (hasMore) MoreSegment(slots, selected, selectedIndex == slots.fixed.size, onSelect, compact)
        }
    }
}

@Composable
private fun RowScope.MoreSegment(
    slots: DirectionSlots,
    selected: Direction,
    isSelected: Boolean,
    onSelect: (Direction) -> Unit,
    compact: Boolean,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = DictTheme.colors
    Box(Modifier.weight(1f)) {
        Segment(
            selected = isSelected,
            onClick = { open = true },
            role = Role.DropdownList,
            tag = "direction_segment_more",
            compact = compact,
            modifier = Modifier.fillMaxWidth(),
        ) { color, style ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoreGap)) {
                val label = slots.moreLabel
                if (label != null) {
                    DirectionLabel(label, Modifier.weight(1f, fill = false), latinStyle = style, color = color)
                } else {
                    MixedText(
                        stringResource(R.string.more),
                        style,
                        DictTheme.type.tibetanInline15,
                        Modifier.weight(1f, fill = false),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(DictIcons.ChevronDown, contentDescription = null, Modifier.size(MoreChevron), tint = color)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = DictShapes.small,
            containerColor = colors.surface1,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(DictTheme.dimens.border, colors.outline),
        ) {
            slots.overflow.forEach { direction ->
                DropdownMenuItem(
                    text = { DirectionLabel(direction, maxLines = 1) },
                    onClick = {
                        open = false
                        onSelect(direction)
                    },
                    modifier = Modifier.testTag("direction_menu_${direction.id}"),
                    trailingIcon = if (direction.id == selected.id) {
                        { Icon(DictIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    colors = MenuDefaults.itemColors(textColor = colors.ink, trailingIconColor = colors.accent),
                )
            }
        }
    }
}

/**
 * One switcher segment; the accent thumb behind it shows selection, so the segment itself only recolours its
 * label: onAccent when selected (600 in dark, 500 in light), ink otherwise.
 */
@Composable
private fun Segment(
    selected: Boolean,
    onClick: () -> Unit,
    role: Role,
    tag: String,
    compact: Boolean,
    modifier: Modifier,
    label: @Composable (color: Color, style: TextStyle) -> Unit,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val color by animateColorAsState(
        if (selected) colors.onAccent else colors.ink,
        tween(THUMB_MS),
        label = "segmentLabel",
    )
    val style = if (selected && colors.isDark) DictTheme.type.labelSemibold else DictTheme.type.label
    val track = if (compact) dimens.switcherCompact else dimens.switcher
    Box(
        modifier
            .fillMaxHeight()
            .heightIn(min = track - dimens.switcherPadding * 2)
            .clip(RoundedCornerShape(dimens.switcherSegmentRadius))
            .selectable(selected = selected, role = role, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = dimens.switcherPadding),
        contentAlignment = Alignment.Center,
    ) {
        label(color, style)
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun DirectionSwitcherPreview() {
    val s = PreviewSamples
    ComponentPreview {
        DirectionSwitcher(s.threeSlots, s.boEn, onSelect = {})
        DirectionSwitcher(s.threeSlots, s.enBo, onSelect = {}, compact = true, autoSelected = true)
        DirectionSwitcher(s.fourSlots, s.boEn, onSelect = {})
        DirectionSwitcher(s.fourSlots.copy(moreLabel = s.zhBo), s.zhBo, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            DirectionBadge(s.boEn, tonal = true)
            DirectionBadge(s.boBo)
            DirectionBadge(s.enBo)
            DirectionBadge(s.zhBo)
        }
    }
}
