package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Gap between page-indicator marks, progress dots, and the streak pill's dot and text. */
private val PageGap = 6.dp
private val DotGap = 4.dp
private val StreakGap = 6.dp

/** The streak pill's dot. */
private val StreakDot = 6.dp

/** Progress bar thickness (the installing screen's 120×3 bar). */
private val ProgressBarHeight = 3.dp

/** Indeterminate progress: segment length (fraction of the track) and sweep duration. */
private const val INDETERMINATE_SEGMENT = 0.4f
private const val INDETERMINATE_MS = 1_200
private const val PAGE_MS = 180

/** Onboarding steps: the [current] page is a 20×4 accent dash, the others 8×4 surface2 dots. */
@Composable
fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(PageGap)) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) dimens.pageDashWidth else dimens.pageDotWidth, tween(PAGE_MS), label = "page")
            val color by animateColorAsState(if (active) colors.accent else colors.surface2, tween(PAGE_MS), label = "pageColor")
            Box(
                Modifier
                    .size(width, dimens.pageDashHeight)
                    .background(color, DictShapes.pill),
            )
        }
    }
}

/** "12-day streak" on an accentContainer pill with an accent dot; the count uses Tibetan digits in the bo UI. */
@Composable
fun StreakPill(days: Int, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val text = pluralStringResource(R.plurals.streak_days, days, TibetanNumerals.formatCount(days, DictTheme.language))
    Row(
        modifier
            .clip(DictShapes.pill)
            .background(colors.accentContainer)
            .padding(horizontal = dimens.streakPillPaddingHorizontal, vertical = dimens.streakPillPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StreakGap),
    ) {
        Box(Modifier.size(StreakDot).background(colors.accent, CircleShape))
        MixedText(text, DictTheme.type.captionMedium, DictTheme.type.tibetanInline14, color = colors.accent)
    }
}

/** One 8dp dot per flag: accent when set (a studied day), surface2 otherwise. */
@Composable
fun ProgressDots(flags: List<Boolean>, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(DotGap)) {
        flags.forEach { on ->
            Box(Modifier.size(DictTheme.dimens.progressDot).background(if (on) colors.accent else colors.surface2, CircleShape))
        }
    }
}

/**
 * A 3dp accent bar on a surface2 track, filling the width the caller gives it (120 on the installing screen).
 * [progress] is 0..1; null shows an indeterminate sweep.
 */
@Composable
fun ProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val track = modifier
        .fillMaxWidth()
        .height(ProgressBarHeight)
        .clip(DictShapes.pill)
        .background(colors.surface2)
    if (progress != null) {
        val value = progress.coerceIn(0f, 1f)
        Box(track.semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .background(colors.accent, DictShapes.pill),
            )
        }
    } else {
        val sweep by rememberInfiniteTransition(label = "progress").animateFloat(
            initialValue = -INDETERMINATE_SEGMENT,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(INDETERMINATE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "progressSweep",
        )
        Box(
            track
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(
                        colors.accent,
                        topLeft = Offset(size.width * sweep, 0f),
                        size = Size(size.width * INDETERMINATE_SEGMENT, size.height),
                        cornerRadius = radius,
                    )
                },
        )
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun IndicatorsPreview() {
    ComponentPreview {
        PageIndicator(count = 3, current = 0)
        PageIndicator(count = 3, current = 1)
        StreakPill(12)
        StreakPill(1)
        ProgressDots(listOf(true, true, true, true, true, false, false))
        ProgressBar(0.6f, Modifier.width(120.dp))
        ProgressBar(null, Modifier.width(120.dp))
    }
}
