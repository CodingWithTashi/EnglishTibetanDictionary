package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Width of the sense-number column, and the gap after it. */
private val NumberColumn = 16.dp
private val NumberGap = 14.dp

/**
 * One sense of an entry on a surface1 card (radius 16, padding 14×16): an accent 13/600 [number] in a 16dp
 * column (omitted when null), then 15/1.6 text with an inline "(n.)" [partOfSpeech] prefix and, when given, a
 * muted " — e.g. " and the [example] (Tibetan examples in Jomolhari 19/1.6), followed by its translation.
 * Tibetan definitions (bo→bo) draw in Jomolhari 18/1.6.
 */
@Composable
fun SenseCard(
    number: Int?,
    partOfSpeech: String?,
    text: String,
    example: ExampleUi?,
    modifier: Modifier = Modifier,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    val exampleAbbr = stringResource(R.string.example_abbr)
    val styled = remember(partOfSpeech, text, example, exampleAbbr, type, colors) {
        val annotated = buildAnnotatedString {
            if (partOfSpeech != null) appendMixed("($partOfSpeech) ", null, type.tibetanGloss)
            appendMixed(text, null, type.tibetanGloss)
            if (example != null) {
                withStyle(SpanStyle(color = colors.muted)) { append(" — $exampleAbbr ") }
                if (example.textIsTibetan) {
                    withStyle(type.tibetanExample.toRunSpan()) { append(example.text) }
                } else {
                    appendMixed(example.text, null, type.tibetanExample)
                }
                if (example.translation != null) {
                    withStyle(SpanStyle(color = colors.muted)) { append(" (") }
                    appendMixed(example.translation, null, type.tibetanGloss, latinColor = colors.muted, tibetanColor = colors.muted)
                    withStyle(SpanStyle(color = colors.muted)) { append(")") }
                }
            }
        }
        val involved = listOfNotNull(
            type.body,
            type.tibetanGloss.takeIf { TibetanScript.hasTibetan(text) || partOfSpeech?.let(TibetanScript::hasTibetan) == true },
            type.tibetanExample.takeIf { example != null && TibetanScript.hasTibetan(example.text) },
            type.tibetanGloss.takeIf { example?.translation?.let(TibetanScript::hasTibetan) == true },
        )
        // A pure-Tibetan definition keeps Jomolhari's paragraph line breaking.
        val base = if (hasNonTibetan(annotated.text)) type.body else type.tibetanGloss
        StyledText(annotated, base.copy(lineHeight = tallestLineHeight(*involved.toTypedArray())))
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .background(colors.surface1)
            .padding(horizontal = dimens.cardPaddingHorizontal, vertical = dimens.cardPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(NumberGap),
    ) {
        if (number != null) {
            // Same line height as the text, so the number sits on the first line.
            Text(
                number.toString(),
                Modifier.width(NumberColumn),
                colors.accent,
                style = type.labelSemibold.copy(lineHeight = styled.style.lineHeight),
            )
        }
        Text(styled.text, Modifier.weight(1f), colors.ink, style = styled.style)
    }
}

/** [SenseCard] for a [SenseUi]. */
@Composable
fun SenseCard(sense: SenseUi, modifier: Modifier = Modifier) {
    SenseCard(sense.number, sense.partOfSpeech, sense.text, sense.example, modifier)
}

@PreviewLightDark
@BoPreview
@Composable
private fun SenseCardPreview() {
    ComponentPreview {
        PreviewSamples.senses.forEach { SenseCard(it) }
        SenseCard(PreviewSamples.tibetanSense)
        SenseCard(1, null, "kindness, compassion", ExampleUi("He showed great compassion.", "སྙིང་རྗེ་ཆེན་པོ།", false))
    }
}

@ExtremeScalePreview
@Composable
private fun SenseCardExtremeScalePreview() {
    ExtremeScale {
        PreviewSamples.senses.forEach { SenseCard(it) }
        SenseCard(PreviewSamples.tibetanSense)
    }
}
