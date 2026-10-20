package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlin.math.ceil

/**
 * Width of the sense-number column: the design's 16dp, or [NUMBER_DIGIT_EM] of the number's font size per digit
 * when that is wider (a Noto Sans digit is 0.572em), so the entry's longest number stays on one line at every text
 * size. Then the gap after it.
 */
private val NumberColumn = 16.dp
private const val NUMBER_DIGIT_EM = 0.6f
private val NumberGap = 14.dp

/** Space between the definition and the note under it. */
private val NoteGap = 4.dp

/** Inline-content id prefix of the label badges in the definition text, one per label. */
private const val LABEL_BADGE_ID = "sense_label_"
internal const val LABEL_BADGE_TAG = "sense_label_badge"

/**
 * One sense of an entry on a surface1 card (radius 16, padding 14×16): an accent 13/600 [number] in a column of at
 * least 16dp that fits [numberDigits] digits (omitted when null; with [reserveNumberColumn] the empty column is
 * kept, so a later card of a numbered sense lines up with its first), then 15/1.6 text with an inline "(n.)"
 * [partOfSpeech] prefix, each of the usage [labels] as its own outline badge (10/600, muted, 1dp outline, radius 6)
 * right after it, the definition, and every one of [examples], each after a muted " — e.g. " (Jomolhari in bo;
 * Tibetan examples in Jomolhari 19/1.6) and followed by its translation. A blank [text] starts with the first
 * example, without the dash. The [note] ("broke, broken") goes on a muted 13sp line under the text. Tibetan
 * definitions (bo→bo) draw in Jomolhari 18/1.6. The badges are measured, so the card grows with the text size and
 * font scale, and they wrap between labels like words; screen readers read each label once, from the text.
 */
@Composable
fun SenseCard(
    number: Int?,
    partOfSpeech: String?,
    text: String,
    examples: List<ExampleUi>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    note: String? = null,
    reserveNumberColumn: Boolean = false,
    numberDigits: Int = 1,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    val exampleAbbr = stringResource(R.string.example_abbr)
    val badges = rememberLabelBadges(labels)
    val numberStyle = type.labelSemibold
    val numberWidth = with(LocalDensity.current) { (numberStyle.fontSize * (NUMBER_DIGIT_EM * numberDigits)).toDp() }
    val numberColumn = max(NumberColumn, numberWidth)
    val styled = remember(partOfSpeech, labels, text, examples, exampleAbbr, type, colors) {
        val hasText = text.isNotBlank()
        val badgeRanges = mutableListOf<IntRange>()
        val annotated = buildAnnotatedString {
            if (partOfSpeech != null) appendMixed("($partOfSpeech) ", null, type.tibetanGloss)
            labels.forEachIndexed { index, label ->
                // A space between badges is where a line may break, so a long run of labels wraps.
                if (index > 0) append(" ")
                val start = length
                appendInlineContent(LABEL_BADGE_ID + index, label)
                badgeRanges += start until length
            }
            if (labels.isNotEmpty() && (hasText || examples.isNotEmpty())) append(" ")
            if (hasText) appendMixed(text, null, type.tibetanGloss)
            examples.forEachIndexed { index, example ->
                val dash = if (hasText || index > 0) " — $exampleAbbr " else "$exampleAbbr "
                appendMixed(dash, null, type.tibetanGloss, latinColor = colors.muted, tibetanColor = colors.muted)
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
            type.tibetanGloss.takeIf { examples.isNotEmpty() && TibetanScript.hasTibetan(exampleAbbr) },
            type.tibetanExample.takeIf { examples.any { TibetanScript.hasTibetan(it.text) } },
            type.tibetanGloss.takeIf { examples.any { it.translation?.let(TibetanScript::hasTibetan) == true } },
        )
        // A pure-Tibetan definition keeps Jomolhari's paragraph line breaking; the badges are not text it draws.
        val drawn = badgeRanges.foldRight(annotated.text) { range, rest -> rest.removeRange(range) }
        val base = if (hasNonTibetan(drawn)) type.body else type.tibetanGloss
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
                Modifier.width(numberColumn),
                colors.accent,
                style = numberStyle.copy(lineHeight = styled.style.lineHeight),
                maxLines = 1,
                softWrap = false,
            )
        } else if (reserveNumberColumn) {
            Spacer(Modifier.width(numberColumn))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoteGap)) {
            Text(
                styled.text,
                color = colors.ink,
                style = styled.style,
                inlineContent = badges,
            )
            if (note != null) MixedText(note, type.secondary, type.tibetanInline14, color = colors.muted)
        }
    }
}

/**
 * One outline badge per label of [labels] as inline text content, keyed by its inline-content id, each sized to its
 * measured text plus the badge padding, so it scales with the text size and font scale. The text carries the label
 * as the placeholder's alternate text, so the badge itself is hidden from screen readers.
 */
@Composable
private fun rememberLabelBadges(labels: List<String>): Map<String, InlineTextContent> {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(labels, colors, dimens, type, density, measurer) {
        labels.withIndex().associate { (index, label) ->
            val badgeText = mixedText(label, type.badge, type.tibetanBadge)
            val measured =
                measurer.measure(badgeText.text, badgeText.style, softWrap = false, maxLines = 1, density = density)
            val placeholder = with(density) {
                val width = measured.size.width + 2 * dimens.badgePaddingHorizontalOutline.toPx()
                val height = measured.size.height + 2 * dimens.badgePaddingVertical.toPx()
                // Rounded up so the badge never clips its text after the sp round trip.
                Placeholder(ceil(width + 1).toSp(), ceil(height + 1).toSp(), PlaceholderVerticalAlign.TextCenter)
            }
            LABEL_BADGE_ID + index to InlineTextContent(placeholder) {
                val shape = DictShapes.extraSmall
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .border(dimens.border, colors.outline, shape)
                        .clearAndSetSemantics { testTag = LABEL_BADGE_TAG },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badgeText.text, color = colors.muted, style = badgeText.style, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * [SenseCard] for a [SenseUi]; its label keys are shown through [usageLabels]. [numberDigits] is the digit count of
 * the entry's highest sense number, so every card of the entry shares one number column.
 */
@Composable
fun SenseCard(sense: SenseUi, modifier: Modifier = Modifier, numberDigits: Int = 1) {
    SenseCard(
        number = sense.number,
        partOfSpeech = sense.partOfSpeech,
        text = sense.text,
        examples = sense.examples,
        modifier = modifier,
        labels = sense.label?.let { usageLabels(it) }.orEmpty(),
        note = sense.note,
        reserveNumberColumn = sense.reserveNumberColumn,
        numberDigits = numberDigits,
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun SenseCardPreview() {
    ComponentPreview {
        PreviewSamples.senses.forEach { SenseCard(it) }
        SenseCard(PreviewSamples.tibetanSense)
        SenseCard(1, null, "kindness, compassion", listOf(ExampleUi("He showed great compassion.", "སྙིང་རྗེ་ཆེན་པོ།", false)))
        // A later card of sense 1, then a card with no definition: its example alone.
        SenseCard(null, null, "mercy", emptyList(), reserveNumberColumn = true)
        SenseCard(2, "n.", "", listOf(ExampleUi("ཁང་པ་ཆེན་པོ།", "a big house", textIsTibetan = true)))
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
