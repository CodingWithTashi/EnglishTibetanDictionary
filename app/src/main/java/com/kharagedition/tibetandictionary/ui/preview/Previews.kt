package com.kharagedition.tibetandictionary.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.theme.DictColors
import com.kharagedition.tibetandictionary.ui.theme.DictIconSet
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlin.math.roundToInt

/**
 * Wraps preview [content] in [DictTheme] on a background-coloured [Surface]. Every `@Preview` in the app goes
 * through this so previews match the running app.
 */
@Composable
fun DictPreview(
    dark: Boolean,
    language: AppLanguage = AppLanguage.EN,
    textScale: Float = 1f,
    lineSpacing: Float = LineSpacing.DEFAULT,
    content: @Composable () -> Unit,
) {
    DictTheme(darkTheme = dark, textScale = textScale, lineSpacing = lineSpacing, language = language) {
        Surface(color = DictTheme.colors.background, contentColor = DictTheme.colors.ink, content = content)
    }
}

private const val LATIN_SAMPLE = "Compassion"
private const val TIBETAN_SAMPLE = "སྙིང་རྗེ། བོད་ཡིག"

/** Every colour token of [this] palette by name, for [PaletteSheet]. */
private fun DictColors.swatches(): List<Pair<String, Color>> = listOf(
    "background" to background, "surface1" to surface1, "surface2" to surface2,
    "accentContainer" to accentContainer, "onAccentContainer" to onAccentContainer, "accent" to accent,
    "onAccent" to onAccent, "ink" to ink, "muted" to muted, "faint" to faint, "outline" to outline,
    "cardBorder" to cardBorder, "divider" to divider, "scrim" to scrim, "inverseSurface" to inverseSurface,
    "inverseOnSurface" to inverseOnSurface, "inverseAccent" to inverseAccent, "success" to success,
    "danger" to danger, "flagLine" to flagLine, "flagBlue" to flagBlue, "flagWhite" to flagWhite,
    "flagRed" to flagRed, "flagGreen" to flagGreen, "flagYellow" to flagYellow,
)

/** The current palette as named swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaletteSheet() {
    val colors = DictTheme.colors
    Column(Modifier.padding(DictTheme.dimens.screenPadding)) {
        Text(if (colors.isDark) "Dark palette" else "Light palette", style = DictTheme.type.screenTitle)
        Spacer(Modifier.height(DictTheme.dimens.blockGap))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
            verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        ) {
            colors.swatches().forEach { (name, color) -> Swatch(name, color) }
        }
    }
}

/** Every Latin (Noto Sans) role, set in Latin. */
@Composable
internal fun LatinTypeSheet() {
    TypeSheet("Latin · Noto Sans", DictTheme.type.latinRoles, LATIN_SAMPLE)
}

/** Every Tibetan (Jomolhari) role, set in Tibetan. */
@Composable
internal fun TibetanTypeSheet() {
    TypeSheet("Tibetan · Jomolhari", DictTheme.type.tibetanRoles, TIBETAN_SAMPLE)
}

@Composable
private fun TypeSheet(title: String, roles: Map<String, TextStyle>, sample: String) {
    Column(Modifier.padding(DictTheme.dimens.screenPadding)) {
        Text(title, style = DictTheme.type.sectionLabel, color = DictTheme.colors.muted)
        roles.forEach { (name, style) -> TypeSample(name, style, sample) }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(Modifier.width(88.dp)) {
        Box(
            Modifier
                .size(width = 88.dp, height = 40.dp)
                .background(color, DictShapes.extraSmall)
                .border(DictTheme.dimens.border, DictTheme.colors.outline, DictShapes.extraSmall),
        )
        Text(name, style = DictTheme.type.dayLabel, color = DictTheme.colors.muted, maxLines = 1)
    }
}

private fun Float.oneDecimal(): Float = (this * 10).roundToInt() / 10f

@Composable
private fun TypeSample(name: String, style: TextStyle, sample: String) {
    val line = style.lineHeight.takeIf { it.isSp }?.value?.oneDecimal()?.toString() ?: "auto"
    Column(Modifier.padding(top = DictTheme.dimens.chipGap)) {
        Text(
            "$name · ${style.fontSize.value.oneDecimal()}sp / $line",
            style = DictTheme.type.caption,
            color = DictTheme.colors.faint,
        )
        Text(sample, style = style)
    }
}

/** Every icon of [set] tinted [tint], with its name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconsSheet(set: DictIconSet = DictIcons, tint: Color = DictTheme.colors.accent) {
    Column(Modifier.padding(DictTheme.dimens.screenPadding)) {
        Text("Icons · stroke ${set.strokeWidth}", style = DictTheme.type.sectionLabel, color = DictTheme.colors.muted)
        Spacer(Modifier.height(DictTheme.dimens.blockGap))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
            verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        ) {
            set.all.forEach { (name, icon) -> IconCell(name, icon, tint) }
        }
    }
}

@Composable
private fun IconCell(name: String, icon: ImageVector, tint: Color) {
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(DictTheme.dimens.minTouch), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = name, tint = tint, modifier = Modifier.size(DictTheme.dimens.iconLarge))
        }
        Text(name, style = DictTheme.type.dayLabel, color = DictTheme.colors.muted, maxLines = 1)
    }
}

@Preview(name = "Palette · light", widthDp = 400)
@Preview(name = "Palette · dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PalettePreview() {
    DictPreview(dark = isSystemInDarkTheme()) { PaletteSheet() }
}

@Preview(name = "Latin type · light", widthDp = 400)
@Preview(name = "Latin type · dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LatinTypePreview() {
    DictPreview(dark = isSystemInDarkTheme()) { LatinTypeSheet() }
}

@Preview(name = "Tibetan type · light", widthDp = 400)
@Preview(name = "Tibetan type · dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TibetanTypePreview() {
    DictPreview(dark = isSystemInDarkTheme()) { TibetanTypeSheet() }
}

@Preview(name = "Icons · light", widthDp = 400)
@Preview(name = "Icons · dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IconsPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        Column {
            IconsSheet(DictIcons)
            IconsSheet(DictIcons.Active)
            IconsSheet(DictIcons.Bold)
            IconsSheet(DictIcons.EmptyState)
            Row(Modifier.padding(DictTheme.dimens.screenPadding), verticalAlignment = Alignment.CenterVertically) {
                Icon(DictIcons.Search, contentDescription = null)
                Spacer(Modifier.width(DictTheme.dimens.chipGap))
                Text("Untinted icons use LocalContentColor (ink)", style = DictTheme.type.caption)
            }
        }
    }
}
