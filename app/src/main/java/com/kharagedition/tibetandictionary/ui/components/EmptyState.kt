package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Inline empty state: padding 12×16, gap 14 between circle and text. */
private val InlinePaddingVertical = 12.dp
private val InlinePaddingHorizontal = 16.dp
private val InlineGap = 14.dp

/** Full empty state: gap between lines, extra space under the circle and above the action. */
private val FullGap = 8.dp
private val CircleBottom = 8.dp
private val ActionTop = 10.dp

/** Icon inside the large (72) and medium (64) circles. */
private val LargeCircleIcon = 30.dp
private val MediumCircleIcon = 28.dp

/** Full empty-state text column side padding (the spec's 40dp). */
private val FullSidePadding = 40.dp

/**
 * A dashed one-line notice (radius 16): a 40dp surface1 circle with a 20dp accent [icon], a 14/600 [title] and a
 * 13 muted [body]. Use for "no exact match" inside a list or sheet.
 */
@Composable
fun InlineEmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .dashedBorder(colors.outline, DictShapes.medium)
            .padding(horizontal = InlinePaddingHorizontal, vertical = InlinePaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InlineGap),
    ) {
        Box(
            Modifier
                .size(dimens.emptyStateCircleSmall)
                .background(colors.surface1, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(dimens.iconSmall), tint = colors.accent)
        }
        Column(Modifier.weight(1f)) {
            MixedText(title, type.button, type.tibetanInline16, color = colors.ink)
            MixedText(body, type.secondary, type.tibetanInline15, color = colors.muted)
        }
    }
}

/**
 * A centred empty screen: a [circleSize] surface1 circle (72; 64 for no-results) holding [icon] in [iconTint],
 * a 17/600 [title], a 14/1.5 muted [body] and an optional [action] (usually a [FilledButton] or
 * [OutlinedButton]). Pass an icon from `DictIcons.EmptyState` (stroke 1.5).
 */
@Composable
fun FullEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    circleSize: Dp = DictTheme.dimens.emptyStateCircleLarge,
    iconTint: Color = DictTheme.colors.accent,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = FullSidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FullGap),
    ) {
        Box(
            Modifier
                .padding(bottom = CircleBottom)
                .size(circleSize)
                .background(colors.surface1, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val iconSize = if (circleSize >= DictTheme.dimens.emptyStateCircleLarge) LargeCircleIcon else MediumCircleIcon
            Icon(icon, contentDescription = null, Modifier.size(iconSize), tint = iconTint)
        }
        MixedText(
            title,
            type.noResultsTitle,
            type.tibetanGloss,
            Modifier.semantics { heading() },
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        MixedText(body, type.bodySmall, type.tibetanInline16, color = colors.muted, textAlign = TextAlign.Center)
        if (action != null) Box(Modifier.padding(top = ActionTop)) { action() }
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun EmptyStatePreview() {
    ComponentPreview {
        InlineEmptyState(DictIcons.SearchOff, "No exact match", stringResource(R.string.no_match_hint_tibetan))
        FullEmptyState(
            DictIcons.EmptyState.Heart,
            stringResource(R.string.saved_empty_title),
            stringResource(R.string.saved_empty_body),
        ) {
            FilledButton(stringResource(R.string.save_todays_word, "བཀྲ་ཤིས།"), {}, size = ButtonSize.Medium)
        }
        FullEmptyState(
            DictIcons.EmptyState.Search,
            stringResource(R.string.no_match_title, "bkrashi"),
            stringResource(R.string.no_match_hint_wylie),
            circleSize = DictTheme.dimens.emptyStateCircleMedium,
            iconTint = DictTheme.colors.muted,
        )
    }
}
