package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Side inset of a see-also row, gap between badge and headword, and gap between label and rows. */
private val RowInset = 4.dp
private val RowGap = 10.dp
private val SectionGap = 8.dp

/**
 * "See also" (12/600 muted) over a list of [SeeAlsoRow]s; the last row has no divider. [rowTag] gives each row a
 * test tag from its index and item (none when it returns null).
 */
@Composable
fun SeeAlsoSection(
    items: List<RelatedUi>,
    onClick: (RelatedUi) -> Unit,
    modifier: Modifier = Modifier,
    rowTag: (index: Int, item: RelatedUi) -> String? = { _, _ -> null },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        MixedText(
            stringResource(R.string.see_also),
            DictTheme.type.sectionLabel,
            DictTheme.type.tibetanInline15,
            Modifier.semantics { heading() },
            color = DictTheme.colors.muted,
        )
        Column {
            items.forEachIndexed { index, item ->
                val tag = rowTag(index, item)
                SeeAlsoRow(
                    item,
                    { onClick(item) },
                    if (tag != null) Modifier.testTag(tag) else Modifier,
                    showDivider = index < items.lastIndex,
                )
            }
        }
    }
}

/**
 * A related entry, at least 52 high: outline [DirectionBadge], headword (Jomolhari 17 or Latin 14) and a
 * trailing chevron.
 */
@Composable
fun SeeAlsoRow(item: RelatedUi, onClick: () -> Unit, modifier: Modifier = Modifier, showDivider: Boolean = true) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimens.seeAlsoRow)
            .bottomDivider(colors.divider, show = showDivider)
            .clickable(onClick = onClick)
            .padding(horizontal = RowInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        DirectionBadge(item.direction)
        if (item.isTibetan) {
            TibetanText(item.headword, Modifier.weight(1f), style = DictTheme.type.tibetanChip, color = colors.ink)
        } else {
            MixedText(item.headword, DictTheme.type.bodySmall, DictTheme.type.tibetanChip, Modifier.weight(1f), color = colors.ink)
        }
        Icon(DictIcons.ChevronRight, contentDescription = null, Modifier.size(dimens.iconSmall), tint = colors.muted)
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun SeeAlsoPreview() {
    ComponentPreview {
        SeeAlsoSection(PreviewSamples.related, onClick = {})
    }
}
