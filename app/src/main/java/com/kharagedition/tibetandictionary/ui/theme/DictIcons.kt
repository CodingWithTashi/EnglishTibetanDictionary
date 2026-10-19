package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The design's line icons at one stroke width, drawn on a 24-unit viewBox with round caps and joins.
 *
 * Paths are black and carry no theme colour: `Icon(icon, cd, tint = …)` tints the whole vector (the spec's
 * `currentColor`), and `Icon` without a tint uses `LocalContentColor` (ink under [DictTheme]). Use [DictIcons]
 * for the default 1.75 stroke, [DictIcons.Active] for active nav items (2) and [DictIcons.EmptyState] for
 * empty-state illustrations (1.5).
 */
open class DictIconSet(val strokeWidth: Float) {

    val Search: ImageVector by lazy {
        icon("Search") {
            stroke { circle(11f, 11f, 7f) }
            stroke("M20 20l-3.5-3.5")
        }
    }

    /** Search with a minus: no results. */
    val SearchOff: ImageVector by lazy {
        icon("SearchOff") {
            stroke { circle(11f, 11f, 7f) }
            stroke("M20 20l-3.5-3.5M8.5 11h5")
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            stroke { roundRect(9f, 3f, 6f, 11f, 3f) }
            stroke("M5 11a7 7 0 0 0 14 0M12 18v3")
        }
    }

    val Speaker: ImageVector by lazy {
        icon("Speaker") {
            stroke("M4 9v6h4l5 4V5L8 9H4z")
            stroke("M16 9a4 4 0 0 1 0 6")
        }
    }

    /** Outline heart: not saved. */
    val Heart: ImageVector by lazy { icon("Heart") { stroke(HEART) } }

    /** Filled heart: saved. */
    val HeartFilled: ImageVector by lazy { icon("HeartFilled") { stroke(HEART, filled = true) } }

    val Share: ImageVector by lazy {
        icon("Share") {
            stroke { circle(18f, 5f, 2.5f) }
            stroke { circle(6f, 12f, 2.5f) }
            stroke { circle(18f, 19f, 2.5f) }
            stroke("M8.2 10.8l7.6-4.6M8.2 13.2l7.6 4.6")
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroke { roundRect(8f, 8f, 12f, 12f, 2f) }
            stroke("M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3")
        }
    }

    /** Learn tab: stacked cards. */
    val Learn: ImageVector by lazy {
        icon("Learn") {
            stroke { roundRect(3f, 5f, 13f, 15f, 2f) }
            stroke("M8 5V3h13v15h-2")
        }
    }

    val Person: ImageVector by lazy {
        icon("Person") {
            stroke { circle(12f, 8f, 4f) }
            stroke("M4 21a8 8 0 0 1 16 0")
        }
    }

    val Back: ImageVector by lazy { icon("Back") { stroke("M15 5l-7 7 7 7") } }

    val Close: ImageVector by lazy { icon("Close") { stroke("M6 6l12 12M18 6L6 18") } }

    val ChevronRight: ImageVector by lazy { icon("ChevronRight") { stroke("M9 5l7 7-7 7") } }

    val ChevronDown: ImageVector by lazy { icon("ChevronDown") { stroke("M6 9l6 6 6-6") } }

    val ChevronLeft: ImageVector by lazy { icon("ChevronLeft") { stroke("M15 5l-7 7 7 7") } }

    val Check: ImageVector by lazy { icon("Check") { stroke("M5 12l5 5L20 7") } }

    val Plus: ImageVector by lazy { icon("Plus") { stroke("M12 5v14M5 12h14") } }

    /** Clock: recent searches. */
    val History: ImageVector by lazy {
        icon("History") {
            stroke { circle(12f, 12f, 8f) }
            stroke("M12 8v4l3 2")
        }
    }

    val OpenExternal: ImageVector by lazy { icon("OpenExternal") { stroke("M7 17L17 7M9 7h8v8") } }

    /** Every icon by name, for the icon preview and tests. */
    val all: Map<String, ImageVector> by lazy {
        linkedMapOf(
            "Search" to Search, "SearchOff" to SearchOff, "Mic" to Mic, "Speaker" to Speaker, "Heart" to Heart,
            "HeartFilled" to HeartFilled, "Share" to Share, "Copy" to Copy, "Learn" to Learn, "Person" to Person,
            "Back" to Back, "Close" to Close, "ChevronRight" to ChevronRight, "ChevronDown" to ChevronDown,
            "ChevronLeft" to ChevronLeft, "Check" to Check, "Plus" to Plus, "History" to History,
            "OpenExternal" to OpenExternal,
        )
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "DictIcons.$name@$strokeWidth",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** A stroked path built with [PathBuilder]. */
    private fun ImageVector.Builder.stroke(filled: Boolean = false, pathBuilder: PathBuilder.() -> Unit) {
        path(
            fill = if (filled) Ink else null,
            stroke = Ink,
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }

    /** A stroked path from SVG path data (arc flags written out with separators). */
    private fun ImageVector.Builder.stroke(pathData: String, filled: Boolean = false) {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = if (filled) Ink else null,
            stroke = Ink,
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
        close()
    }

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        horizontalLineTo(x + w - r)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = r, dy1 = r)
        verticalLineTo(y + h - r)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -r, dy1 = r)
        horizontalLineTo(x + r)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -r, dy1 = -r)
        verticalLineTo(y + r)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = r, dy1 = -r)
        close()
    }

    private companion object {
        /** Tinted at draw time by `Icon(tint = …)`; never shown as black. */
        val Ink = SolidColor(Color.Black)

        const val HEART = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    }
}

/** Icons at the default 1.75 stroke, plus the [Active] (2) and [EmptyState] (1.5) sets. */
object DictIcons : DictIconSet(strokeWidth = 1.75f) {
    /** Active bottom-nav items. */
    val Active = DictIconSet(strokeWidth = 2f)

    /** Empty-state illustrations. */
    val EmptyState = DictIconSet(strokeWidth = 1.5f)
}
