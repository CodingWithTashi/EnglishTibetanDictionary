package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Chip heights: Large 36 (Home recents, component sheet), Small 32 (folder and detected-direction chips). */
enum class ChipSize { Large, Small }

/** Gap between a chip's leading icon and its label. */
private val ChipIconGap = 6.dp

/** Side padding: Large 14, Small 12; 10 before a leading icon. */
private fun ChipSize.endPadding(): Dp = if (this == ChipSize.Large) 14.dp else 12.dp
private val ChipLeadingPadding = 10.dp

/** Leading icon: 16 on Large, 14 on Small. */
private fun ChipSize.iconSize(): Dp = if (this == ChipSize.Large) 16.dp else 14.dp

@Composable
private fun ChipSize.height(): Dp = if (this == ChipSize.Large) DictTheme.dimens.chipLarge else DictTheme.dimens.chipSmall

/** Label roles: Large 13/500 + Jomolhari 17; Small 12/500 + Jomolhari 14. */
@Composable
private fun ChipSize.textStyles(): Pair<TextStyle, TextStyle> = if (this == ChipSize.Large) {
    DictTheme.type.label to DictTheme.type.tibetanChip
} else {
    DictTheme.type.captionMedium to DictTheme.type.tibetanInline14
}

/**
 * The pill chip all chips share. [selected] = accent fill with onAccent content; [tonal] = accentContainer fill
 * with onAccentContainer content; otherwise transparent with a 1dp outline border and ink content. [content]
 * reads its colour from `LocalContentColor`. A null [onClick] makes the chip display-only.
 */
@Composable
fun DictChip(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.Large,
    leading: ImageVector? = null,
    tonal: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = DictTheme.colors
    val shape = DictShapes.pill
    val (container, contentColor) = when {
        selected -> colors.accent to colors.onAccent
        tonal -> colors.accentContainer to colors.onAccentContainer
        else -> Color.Transparent to colors.ink
    }
    val outlined = !selected && !tonal
    Row(
        modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .heightIn(min = size.height())
            .clip(shape)
            .background(container)
            .then(if (outlined) Modifier.border(DictTheme.dimens.border, colors.outline, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { if (selected) this.selected = true }
            .padding(start = if (leading != null) ChipLeadingPadding else size.endPadding(), end = size.endPadding()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipIconGap),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (leading != null) Icon(leading, contentDescription = null, Modifier.size(size.iconSize()))
            content()
        }
    }
}

/** A chip with a text label that may mix scripts ("compassion", "སྙིང་རྗེ།", "All 42"). */
@Composable
fun TextChip(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: ChipSize = ChipSize.Large,
    tonal: Boolean = false,
    leading: ImageVector? = null,
) {
    val (latin, tibetan) = size.textStyles()
    DictChip(selected, onClick, modifier, size, leading, tonal) {
        MixedText(text, latin, tibetan, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A chip with a Tibetan label in Jomolhari 17 (14 on Small), e.g. a recent Tibetan search. */
@Composable
fun TibetanChip(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: ChipSize = ChipSize.Large,
) {
    DictChip(selected, onClick, modifier, size) {
        TibetanText(text, style = size.textStyles().second, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Tonal chip with a check, naming what auto-detection picked: "[text] · [direction]" ("Wylie · བོད→ EN"), or
 * just [text] when [direction] is null.
 */
@Composable
fun DetectedChip(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    direction: Direction? = null,
    size: ChipSize = ChipSize.Small,
) {
    val label = if (direction != null) "$text · ${directionLabelText(direction)}" else text
    TextChip(label, onClick, modifier, size = size, tonal = true, leading = DictIcons.Bold.Check)
}

/**
 * Dashed round "+" chip (new folder), [size] square, muted plus. Its 48dp touch target overhangs the chip
 * sideways without taking row space, so it sits one chip gap after its neighbour (1i).
 */
@Composable
fun AddChip(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.Large,
) {
    Box(
        modifier
            .reportWidth(size.height())
            .minimumInteractiveComponentSize()
            .size(size.height())
            .clip(DictShapes.pill)
            .dashedBorder(DictTheme.colors.outline, DictShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(DictIcons.Active.Plus, contentDescription = null, Modifier.size(ChipSize.Large.iconSize()), tint = DictTheme.colors.muted)
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun ChipsPreview() {
    ComponentPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            TextChip("Selected", {}, selected = true)
            TextChip("Default", {})
            TibetanChip("སྙིང་རྗེ།", {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            DetectedChip("Detected", {}, size = ChipSize.Large)
            AddChip({}, "New folder")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            TextChip("All 42", {}, selected = true, size = ChipSize.Small)
            TextChip("Dharma 14", {}, size = ChipSize.Small)
            AddChip({}, "New folder", size = ChipSize.Small)
        }
        DetectedChip("Wylie", {}, direction = PreviewSamples.boEn)
    }
}
