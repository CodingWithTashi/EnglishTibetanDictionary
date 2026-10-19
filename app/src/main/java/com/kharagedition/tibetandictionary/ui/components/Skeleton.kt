package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Bar widths of a placeholder result row (headword, Wylie, gloss); alternate the two presets down a list. */
enum class SkeletonPreset(val headword: Dp, val wylie: Dp, val gloss: Dp) {
    First(headword = 120.dp, wylie = 80.dp, gloss = 240.dp),
    Second(headword = 160.dp, wylie = 100.dp, gloss = 200.dp),
}

/** Gap between skeleton bars. */
private val BarGap = 8.dp

/**
 * A loading placeholder shaped like an [EntryRow] (same padding): a 22dp surface2 bar for the headword and two
 * 12dp surface1 bars, radius 6. Bars never exceed the row width. Hidden from accessibility services.
 */
@Composable
fun SkeletonEntryRow(modifier: Modifier = Modifier, preset: SkeletonPreset = SkeletonPreset.First) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.entryRowPaddingHorizontal, vertical = dimens.entryRowPaddingVertical)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        Bar(preset.headword, dimens.skeletonBarLarge, colors.surface2)
        Bar(preset.wylie, dimens.skeletonBarSmall, colors.surface1)
        Bar(preset.gloss, dimens.skeletonBarSmall, colors.surface1)
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, color: Color) {
    Box(
        Modifier
            .widthIn(max = width)
            .fillMaxWidth()
            .height(height)
            .background(color, DictShapes.extraSmall),
    )
}

@PreviewLightDark
@Composable
private fun SkeletonPreview() {
    ComponentPreview(edgeToEdge = true) {
        SkeletonEntryRow(preset = SkeletonPreset.First)
        SkeletonEntryRow(preset = SkeletonPreset.Second)
    }
}
