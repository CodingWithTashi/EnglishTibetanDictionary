package com.kharagedition.tibetandictionary.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.theme.DictIconSet
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Nav icon size inside the 56×28 indicator. */
private val NavIcon = 22.dp

/** Bar padding (8 top and sides, 4 bottom) and the gap between indicator, label and gloss (2). */
private val BarPaddingTop = 8.dp
private val BarPaddingSide = 8.dp
private val BarPaddingBottom = 4.dp
private val ItemGap = 2.dp

/** Duration of the indicator and label colour change. */
private const val SELECTION_MS = 180

private fun MainTab.icon(set: DictIconSet, selected: Boolean): ImageVector = when (this) {
    MainTab.SEARCH -> set.Search
    MainTab.SAVED -> if (selected) set.HeartFilled else set.Heart
    MainTab.LEARN -> set.Learn
    MainTab.PROFILE -> set.Person
}

@StringRes
private fun MainTab.labelRes(): Int = when (this) {
    MainTab.SEARCH -> R.string.tab_search
    MainTab.SAVED -> R.string.tab_saved
    MainTab.LEARN -> R.string.tab_learn
    MainTab.PROFILE -> R.string.tab_profile
}

@StringRes
private fun MainTab.glossRes(): Int = when (this) {
    MainTab.SEARCH -> R.string.tab_search_gloss
    MainTab.SAVED -> R.string.tab_saved_gloss
    MainTab.LEARN -> R.string.tab_learn_gloss
    MainTab.PROFILE -> R.string.tab_profile_gloss
}

/**
 * The main bottom bar: at least 88dp of surface1 (plus the system navigation-bar inset) with one item per tab in
 * [tabs] (the caller drops tabs whose feature is off). The [selected] item shows a 56×28 accentContainer
 * indicator behind a stroke-2 icon (a filled heart for Saved). Labels come from `tab_*` and `tab_*_gloss`:
 * for [AppLanguage.EN] the Latin label (13, 600 active) sits over a Jomolhari 12/1.2 gloss; for
 * [AppLanguage.BO] the Tibetan label (Jomolhari 15/1.3) sits over a Latin 11 gloss. Whichever resource holds the
 * Tibetan text is used for the Tibetan line, so a bo translation that swaps the pair still renders correctly.
 * The bar is a toast anchor: toasts sit 12dp above it.
 *
 * Test tags: `nav_{tab}` (lower-case tab name) on each item, `nav_label_{tab}` on its primary label.
 */
@Composable
fun BottomNavBar(
    tabs: List<MainTab>,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(DictTheme.colors.surface1)
            .toastAnchor()
            .navigationBarsPadding()
            .heightIn(min = DictTheme.dimens.navBar)
            .padding(start = BarPaddingSide, top = BarPaddingTop, end = BarPaddingSide, bottom = BarPaddingBottom)
            .selectableGroup(),
    ) {
        tabs.forEach { tab ->
            NavItem(tab, tab == selected, { onSelect(tab) }, language, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(tab: MainTab, selected: Boolean, onClick: () -> Unit, language: AppLanguage, modifier: Modifier) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    val indicator by animateColorAsState(
        if (selected) colors.accentContainer else Color.Transparent,
        tween(SELECTION_MS),
        label = "navIndicator",
    )
    val iconTint by animateColorAsState(
        if (selected) colors.onAccentContainer else colors.muted,
        tween(SELECTION_MS),
        label = "navIcon",
    )
    val labelColor = if (selected) colors.ink else colors.muted
    val name = tab.name.lowercase()
    val first = stringResource(tab.labelRes())
    val second = stringResource(tab.glossRes())
    val (latin, tibetan) = if (TibetanScript.hasTibetan(first)) second to first else first to second
    Column(
        modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag("nav_$name"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Box(
            Modifier
                .size(dimens.navIndicatorWidth, dimens.navIndicatorHeight)
                .clip(DictShapes.pill)
                .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon(if (selected) DictIcons.Active else DictIcons, selected), null, Modifier.size(NavIcon), tint = iconTint)
        }
        if (language == AppLanguage.BO) {
            Text(tibetan, Modifier.testTag("nav_label_$name"), labelColor, style = type.tibetanNavPrimary, textAlign = TextAlign.Center)
            Text(latin, color = colors.muted, style = type.navGlossLatin, textAlign = TextAlign.Center)
        } else {
            Text(
                latin,
                Modifier.testTag("nav_label_$name"),
                labelColor,
                style = if (selected) type.navLabelActive else type.navLabel,
                textAlign = TextAlign.Center,
            )
            Text(tibetan, color = colors.muted, style = type.tibetanNavGloss, textAlign = TextAlign.Center)
        }
    }
}

private val ReleaseTabs = listOf(MainTab.SEARCH, MainTab.SAVED, MainTab.PROFILE)

@PreviewLightDark
@BoPreview
@Composable
private fun BottomNavBarPreview() {
    ComponentPreview(edgeToEdge = true) {
        BottomNavBar(MainTab.entries, MainTab.SEARCH, {}, DictTheme.language)
        Box(Modifier.size(DictTheme.dimens.blockGap))
        BottomNavBar(ReleaseTabs, MainTab.SAVED, {}, DictTheme.language)
        Box(Modifier.size(DictTheme.dimens.blockGap))
        BottomNavBar(ReleaseTabs, MainTab.PROFILE, {}, DictTheme.language)
    }
}

@ExtremeScalePreview
@Composable
private fun BottomNavBarExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        BottomNavBar(MainTab.entries, MainTab.SAVED, {}, AppLanguage.EN)
        Box(Modifier.size(DictTheme.dimens.blockGap))
        BottomNavBar(MainTab.entries, MainTab.PROFILE, {}, AppLanguage.BO)
    }
}
