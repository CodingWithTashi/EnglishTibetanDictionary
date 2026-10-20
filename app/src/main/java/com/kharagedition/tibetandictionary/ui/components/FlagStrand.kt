package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * Sizes of the prayer-flag strand: total [width] (null = fill the parent), each flag [flagWidth] × [flagHeight],
 * [gap] between flags, [startPadding] before the first flag, and whether the flags are [centered] on the line.
 */
enum class FlagStrandSize(
    val width: Dp?,
    val flagWidth: Dp,
    val flagHeight: Dp,
    val gap: Dp,
    val startPadding: Dp,
    val centered: Boolean = false,
) {
    /** Flush along the top of the word-of-the-day card. */
    Card(width = null, flagWidth = 12.dp, flagHeight = 8.dp, gap = 3.dp, startPadding = 6.dp),

    /** Onboarding step 1. */
    Onboarding(width = 140.dp, flagWidth = 12.dp, flagHeight = 8.dp, gap = 3.dp, startPadding = 6.dp),

    /** Top of the splash / installing screen, flags centred. */
    Splash(width = 160.dp, flagWidth = 14.dp, flagHeight = 10.dp, gap = 3.dp, startPadding = 0.dp, centered = true),

    /** Home-screen widget. */
    Widget(width = 50.dp, flagWidth = 7.dp, flagHeight = 5.dp, gap = 1.5.dp, startPadding = 2.dp),

    /** Share card (drawn at 1080 px wide). */
    ShareCard(width = 300.dp, flagWidth = 30.dp, flagHeight = 20.dp, gap = 7.dp, startPadding = 13.dp),
}

/** Flags hang from the line: square top, 2dp-rounded bottom corners. */
private val FlagShape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)

/**
 * A strand of five lungta flags (blue, white, red, green, yellow) hanging from a 1dp line; the only place a
 * second colour appears. Decorative, so hidden from accessibility services.
 *
 * On the light word-of-the-day card the white flag takes the page colour, as in mockup 2a: `flagWhite` is too close
 * to `surface1` to read there, so the strand would look like four flags and a gap.
 */
@Composable
fun FlagStrand(size: FlagStrandSize, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val white = if (size == FlagStrandSize.Card && !colors.isDark) colors.background else colors.flagWhite
    val flags = listOf(colors.flagBlue, white, colors.flagRed, colors.flagGreen, colors.flagYellow)
    Column(
        modifier
            .then(if (size.width != null) Modifier.width(size.width) else Modifier.fillMaxWidth())
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(DictTheme.dimens.border)
                .background(colors.flagLine),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = size.startPadding),
            horizontalArrangement = if (size.centered) {
                Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(size.gap)
            },
        ) {
            flags.forEach { Box(Modifier.size(size.flagWidth, size.flagHeight).background(it, FlagShape)) }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlagStrandPreview() {
    ComponentPreview {
        FlagStrandSize.entries.forEach { FlagStrand(it) }
    }
}
