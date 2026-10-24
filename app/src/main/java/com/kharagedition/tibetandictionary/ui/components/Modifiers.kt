package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Dash and gap of dashed outlines (the spec's CSS `1px dashed`). */
private val DashLength = 4.dp
private val DashGap = 3.dp

/** Dot size and spacing of dotted underlines (CSS `1.5px dotted`). */
private val DotSize = 1.5.dp
private val DotPitch = 3.dp

/**
 * A dashed [width] outline of [shape] in [color], drawn just inside the bounds. Used by dashed boxes: inline empty
 * state, tip box and the add chip.
 */
internal fun Modifier.dashedBorder(color: Color, shape: Shape, width: Dp = 1.dp): Modifier = drawWithCache {
    val strokePx = width.toPx()
    val inset = strokePx / 2
    val outline = shape.createOutline(Size(size.width - strokePx, size.height - strokePx), layoutDirection, this)
    val stroke = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashGap.toPx())),
    )
    onDrawBehind { translate(inset, inset) { drawOutline(outline, color, style = stroke) } }
}

/** A [width] line in [color] along the bottom edge (list separators); nothing when [show] is false. */
internal fun Modifier.bottomDivider(color: Color, show: Boolean = true, width: Dp = 1.dp): Modifier =
    if (!show) this else drawBehind {
        val strokePx = width.toPx()
        val y = size.height - strokePx / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokePx)
    }

/** Round dots in [color] along the bottom edge: the mark of a tappable word in a translation. */
fun Modifier.dottedUnderline(color: Color): Modifier = drawBehind {
    val dot = DotSize.toPx()
    val y = size.height - dot / 2
    drawLine(
        color,
        Offset(dot / 2, y),
        Offset(size.width - dot / 2, y),
        strokeWidth = dot,
        cap = StrokeCap.Round,
        // Zero-length dashes with round caps are dots.
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, DotPitch.toPx())),
    )
}

/**
 * Pulls this element up by [amount] (a negative top margin): it is drawn [amount] higher and reports a height
 * [amount] shorter, so the next element follows it directly. Used for the Wylie line tucked under a Tibetan
 * headword, whose line box has generous built-in space.
 */
internal fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

/**
 * Extends this element [amount] past its start edge (a negative start margin), so the icon inside a leading
 * touch target lines up with the text above it.
 */
internal fun Modifier.extendStart(amount: Dp): Modifier = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + px else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))
    layout((placeable.width - px).coerceAtLeast(0), placeable.height) { placeable.place(-px, 0) }
}

/**
 * Lays this element out at its own width (e.g. a 48dp touch target around a 32dp chip) but reports only
 * [reported] to the parent, centred on it, so the overhang reaches into the neighbouring gaps instead of widening
 * them. The overhang still draws and takes touches.
 */
internal fun Modifier.reportWidth(reported: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = reported.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
}

/** This inclusive range clipped to a text of [length] as an end-exclusive pair, or null when nothing is left. */
internal fun IntRange.clampTo(length: Int): Pair<Int, Int>? {
    val start = first.coerceAtLeast(0)
    val end = (last + 1).coerceAtMost(length)
    return if (start < end) start to end else null
}
