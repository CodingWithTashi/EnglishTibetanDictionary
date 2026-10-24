package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** The track is 44 high with 4 around and between the tabs (1y). */
private val TabsHeight = 44.dp
private val TabsPadding = 4.dp

/** The selected tab's lift (1y: a 1px shadow at 8% ink). */
private val TabShadow = 1.dp
private val TabSide = 8.dp

/**
 * Equal tabs across a full-width surface1 pill track (Saved's "Words · 24 | Translations · 6", 1y): the selected tab
 * is a raised pill in the page colour (surface2 in dark) with 13/600 ink text, the others 13/500 muted. Unlike
 * [SegmentedControl3] (a compact settings value), this switches what the screen below shows. Each tab is a
 * selectable tab for screen readers, tagged with [tags] when given (one per option).
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val thumb = if (colors.isDark) colors.surface2 else colors.background
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = TabsHeight)
            .clip(DictShapes.pill)
            .background(colors.surface1)
            .padding(TabsPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TabsPadding),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (selected) Modifier.shadow(TabShadow, DictShapes.pill) else Modifier)
                    .clip(DictShapes.pill)
                    .background(if (selected) thumb else colors.surface1)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = TabSide)
                    .then(tags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier),
                contentAlignment = Alignment.Center,
            ) {
                MixedText(
                    option,
                    if (selected) type.labelSemibold else type.label,
                    type.tibetanSegment,
                    color = if (selected) colors.ink else colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun SegmentedTabsPreview() {
    ComponentPreview {
        SegmentedTabs(listOf("Words · 24", "Translations · 6"), selectedIndex = 1, onSelect = {})
    }
}
