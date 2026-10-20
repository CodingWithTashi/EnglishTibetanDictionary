package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** What a [SettingsRow] shows at its end. */
sealed interface SettingsTrailing {
    /** A muted chevron (the row opens a sheet or screen). */
    data object Chevron : SettingsTrailing

    /** A 36×20 switch; the whole row toggles it. */
    data class Switch(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : SettingsTrailing

    /** Nothing. */
    data object None : SettingsTrailing
}

/** Group title padding (4 above, 8 below). */
private val GroupTitleTop = 4.dp
private val GroupTitleBottom = 8.dp

/**
 * Row gaps: label to value (12), value to chevron and wrapped value to label (4), row to its extra content (10),
 * row padding (8 / 12).
 */
private val RowGap = 12.dp
private val ValueGap = 4.dp
private val ContentGap = 10.dp
private val RowPadding = 8.dp
private val RowPaddingWithContent = 12.dp

/** Chevron after a value. */
private val Chevron = 18.dp

/** Switch: 36×20 track, 16dp thumb inset 2dp. */
private val SwitchWidth = 36.dp
private val SwitchHeight = 20.dp
private val SwitchThumb = 16.dp
private val SwitchInset = 2.dp

/**
 * Segmented control: a track at least 32 high (each segment's 48dp touch target reaches past it), 3dp track padding,
 * 2dp between segments, 10dp segment side padding.
 */
private val SegmentedHeight = 32.dp
private val SegmentedPadding = 3.dp
private val SegmentedGap = 2.dp
private val SegmentSide = 10.dp

private const val TOGGLE_MS = 180

/**
 * A settings section: an upper-cased 11/600 muted [title] ("READING"; a Tibetan title draws in Jomolhari 15)
 * over its rows.
 */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Overline(
            title,
            Modifier
                .padding(top = GroupTitleTop, bottom = GroupTitleBottom)
                .semantics { heading() },
        )
        content()
    }
}

/**
 * A settings row, at least 52 high (56 in the bo UI): [label] (14, or Jomolhari 18 in bo) with an optional
 * [subLabel] (12 muted), then [valueContent] or the muted 13 [value], then [trailing]. With a
 * [SettingsTrailing.Switch] the whole row toggles; otherwise [onClick] makes it a button. [content] adds a block
 * under the row (the text-size preview box). A divider is drawn below unless [divider] is false.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: SettingsTrailing = SettingsTrailing.None,
    subLabel: String? = null,
    divider: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val base = modifier
        .fillMaxWidth()
        .bottomDivider(colors.divider, show = divider)
    val interactive = when {
        trailing is SettingsTrailing.Switch ->
            base.toggleable(value = trailing.checked, role = Role.Switch, onValueChange = trailing.onCheckedChange)
        onClick != null -> base.clickable(role = Role.Button, onClick = onClick)
        else -> base
    }
    Column(
        interactive
            .heightIn(min = DictTheme.dimens.settingsRow)
            .padding(vertical = if (content != null) RowPaddingWithContent else RowPadding),
        verticalArrangement = Arrangement.spacedBy(ContentGap, Alignment.CenterVertically),
    ) {
        // Label and value share a line when they fit; at large text sizes the value wraps under the label.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(ValueGap),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = RowGap)) {
                MixedText(label, type.bodySmall, type.tibetanGloss, color = colors.ink)
                if (subLabel != null) MixedText(subLabel, type.caption, type.tibetanInline14, color = colors.muted)
            }
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ValueGap, Alignment.End),
            ) {
                when {
                    valueContent != null -> valueContent()
                    value != null -> MixedText(value, type.secondary, type.tibetanInline15, color = colors.muted)
                }
                when (trailing) {
                    SettingsTrailing.Chevron ->
                        Icon(DictIcons.ChevronRight, contentDescription = null, Modifier.size(Chevron), tint = colors.muted)
                    is SettingsTrailing.Switch -> DictSwitch(trailing.checked)
                    SettingsTrailing.None -> Unit
                }
            }
        }
        if (content != null) content()
    }
}

/**
 * The 36×20 settings switch (display only; the row handles toggling): checked = accent track with an onAccent
 * thumb on the right, unchecked = surface2 track with a background thumb on the left.
 */
@Composable
private fun DictSwitch(checked: Boolean) {
    val colors = DictTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.surface2, tween(TOGGLE_MS), label = "switchTrack")
    val thumb by animateColorAsState(if (checked) colors.onAccent else colors.background, tween(TOGGLE_MS), label = "switchThumb")
    val x by animateDpAsState(
        if (checked) SwitchWidth - SwitchThumb - SwitchInset else SwitchInset,
        tween(TOGGLE_MS),
        label = "switchX",
    )
    Box(
        Modifier
            .size(SwitchWidth, SwitchHeight)
            .clip(DictShapes.pill)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .size(SwitchThumb)
                .background(thumb, CircleShape),
        )
    }
}

/**
 * A three-way choice (Light / Dark / System) on a surface1 pill track: the selected segment is accent with
 * onAccent text; labels are 12/500 (Jomolhari 14 in bo). The track stays 32dp high while each segment reserves a
 * 48dp touch target around it; every segment draws its own slice of the track, so the track spans the segments
 * with no gaps.
 */
@Composable
fun SegmentedControl3(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DictTheme.colors
    Row(modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val first = index == 0
            val last = index == options.lastIndex
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    // At least as wide as the touch target, so the slices meet.
                    .widthIn(min = DictTheme.dimens.minTouch)
                    .background(colors.surface1, trackSlice(first, last))
                    .padding(
                        start = if (first) SegmentedPadding else SegmentedGap / 2,
                        top = SegmentedPadding,
                        end = if (last) SegmentedPadding else SegmentedGap / 2,
                        bottom = SegmentedPadding,
                    )
                    .heightIn(min = SegmentedHeight - SegmentedPadding * 2)
                    .clip(DictShapes.pill)
                    .background(if (selected) colors.accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = SegmentSide),
                contentAlignment = Alignment.Center,
            ) {
                MixedText(
                    option,
                    DictTheme.type.captionMedium,
                    DictTheme.type.tibetanInline14,
                    color = if (selected) colors.onAccent else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One segment's slice of the pill track: the first rounds its start, the last its end, the others are square. */
private fun trackSlice(first: Boolean, last: Boolean): Shape = RoundedCornerShape(
    topStartPercent = if (first) HALF else 0,
    topEndPercent = if (last) HALF else 0,
    bottomEndPercent = if (last) HALF else 0,
    bottomStartPercent = if (first) HALF else 0,
)

/** Corner radius as a percentage of the height: a pill end. */
private const val HALF = 50

/** Preview copy: the string resources in en, the 3d mockup's Tibetan labels in bo (values-bo is filled later). */
private class SettingsCopy(
    val group: String,
    val textSize: String,
    val theme: String,
    val direction: String,
    val language: String,
    val wotd: String,
    val themes: List<String>,
)

@Composable
private fun settingsCopy(): SettingsCopy = if (DictTheme.language == AppLanguage.BO) {
    SettingsCopy(
        group = "ཀློག་སྟངས།",
        textSize = "ཡིག་གཟུགས་ཆེ་ཆུང་།",
        theme = "མདོག་རྣམ།",
        direction = "སྔོན་སྒྲིག་ཁ་ཕྱོགས།",
        language = "མཉེན་ཆས་སྐད་ཡིག",
        wotd = "དེ་རིང་གི་ཚིག་བརྡ་ཐོ།",
        themes = listOf("དཀར།", "ནག", "རང་འགུལ།"),
    )
} else {
    SettingsCopy(
        stringResource(R.string.reading),
        stringResource(R.string.text_size_spacing),
        stringResource(R.string.theme),
        stringResource(R.string.default_direction),
        stringResource(R.string.app_language),
        stringResource(R.string.wotd_notification),
        listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_system).map { stringResource(it) },
    )
}

@Composable
private fun SettingsSample() {
    val copy = settingsCopy()
    SettingsGroup(copy.group) {
        SettingsRow(copy.textSize, value = "Default · 1.9", onClick = {}) {
            PreviewCard(AppTextSize.DEFAULT, LineSpacing.DEFAULT)
        }
        SettingsRow(copy.theme, valueContent = { SegmentedControl3(copy.themes, 2, {}) })
        SettingsRow(
            copy.direction,
            valueContent = { DirectionLabel(PreviewSamples.boEn, color = DictTheme.colors.muted) },
            onClick = {},
            trailing = SettingsTrailing.Chevron,
        )
        SettingsRow(copy.language, value = stringResource(R.string.language_bo), onClick = {}, trailing = SettingsTrailing.Chevron)
        SettingsRow(copy.wotd, subLabel = "9:00 AM", trailing = SettingsTrailing.Switch(checked = true) {})
        SettingsRow(copy.wotd, trailing = SettingsTrailing.Switch(checked = false) {}, divider = false)
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun SettingsPreview() {
    ComponentPreview { SettingsSample() }
}

@ExtremeScalePreview
@Composable
private fun SettingsRowExtremeScalePreview() {
    ExtremeScale { SettingsSample() }
}
