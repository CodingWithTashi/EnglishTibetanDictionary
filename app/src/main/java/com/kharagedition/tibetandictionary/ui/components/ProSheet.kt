package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Free and Pro limits as the comparison table states them (spec 1za); the server's config is the real source. */
private const val FREE_PER_DAY = 10
private const val FREE_CHARACTERS = 100

/** Pro's characters per translation, also named by Translate's "Need longer text?" line. */
internal const val PRO_MAX_CHARS = 500

private val TitleTop = 18.dp
private val SubtitleTop = 8.dp
private val TableTop = 24.dp
private val CellPadding = 12.dp
private val ButtonTop = 24.dp
private val NoteTop = 10.dp
private val SheetBottom = 24.dp
private const val LABEL_WEIGHT = 1.4f

/**
 * The Pro fake door (1za): "Translate without limits", the Free/Pro table and a disabled "Coming soon". It opens
 * only from a limit, the too-long line or Profile; the caller logs `pro_interest`. Tags: `pro_sheet`,
 * `pro_sheet_coming_soon`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    DictModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        ProSheetContent()
    }
}

@Composable
private fun ProSheetContent() {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val language = DictTheme.language
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("pro_sheet")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DictTheme.dimens.screenPadding)
            .padding(bottom = SheetBottom),
    ) {
        FlagStrand(FlagStrandSize.Onboarding)
        MixedText(
            stringResource(R.string.pro_title),
            type.onboardingTitle,
            type.tibetanGreeting,
            Modifier
                .padding(top = TitleTop)
                .semantics { heading() },
            color = colors.ink,
        )
        MixedText(
            stringResource(R.string.pro_subtitle),
            type.bodySmall,
            type.tibetanInline15,
            Modifier.padding(top = SubtitleTop),
            color = colors.muted,
        )
        Column(Modifier.padding(top = TableTop)) {
            TableRow(divider = false) {
                Cell("", type.label, colors.muted, label = true)
                Cell(stringResource(R.string.pro_free), type.labelSemibold, colors.muted)
                Cell(stringResource(R.string.pro_plan), type.labelSemibold, colors.accent)
            }
            TableRow {
                Cell(stringResource(R.string.pro_per_day), type.secondary, colors.ink, label = true)
                Cell(TibetanNumerals.formatCount(FREE_PER_DAY, language), type.secondary, colors.ink)
                Cell(stringResource(R.string.pro_unlimited), type.labelSemibold, colors.ink)
            }
            TableRow {
                Cell(stringResource(R.string.pro_characters), type.secondary, colors.ink, label = true)
                Cell(TibetanNumerals.formatCount(FREE_CHARACTERS, language), type.secondary, colors.ink)
                Cell(TibetanNumerals.formatCount(PRO_MAX_CHARS, language), type.labelSemibold, colors.ink)
            }
            TableRow {
                Cell(stringResource(R.string.pro_dictionary), type.secondary, colors.ink, label = true)
                Cell(stringResource(R.string.pro_full), type.secondary, colors.ink)
                Cell(stringResource(R.string.pro_full), type.labelSemibold, colors.ink)
            }
            HorizontalDivider(color = colors.divider)
        }
        FilledButton(
            stringResource(R.string.pro_coming_soon),
            onClick = {},
            modifier = Modifier
                .padding(top = ButtonTop)
                .fillMaxWidth()
                .testTag("pro_sheet_coming_soon"),
            size = ButtonSize.Cta,
            enabled = false,
        )
        MixedText(
            stringResource(R.string.pro_note),
            type.caption,
            type.tibetanInline14,
            Modifier
                .fillMaxWidth()
                .padding(top = NoteTop),
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** One table row under a divider line (the header has none). */
@Composable
private fun TableRow(divider: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (divider) HorizontalDivider(color = DictTheme.colors.divider)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = CellPadding),
        horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        content = content,
    )
}

/** A [label] cell is the wider first column, start-aligned; the plan columns are centred. */
@Composable
private fun RowScope.Cell(text: String, style: TextStyle, color: Color, label: Boolean = false) {
    MixedText(
        text,
        style,
        DictTheme.type.tibetanInline15,
        Modifier.weight(if (label) LABEL_WEIGHT else 1f),
        color = color,
        textAlign = if (label) TextAlign.Start else TextAlign.Center,
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun ProSheetPreview() {
    ComponentPreview(edgeToEdge = true) { ProSheetContent() }
}
