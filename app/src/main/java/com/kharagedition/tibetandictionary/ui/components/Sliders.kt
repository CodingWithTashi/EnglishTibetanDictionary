package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Gap between the "A" ends and the track. */
private val EndGap = 12.dp

/** The large "A" is 20sp against the small 13sp one. */
private const val LARGE_A_RATIO = 20f / 13f

/** Sample glyph at the ends of the text-size slider (a size sample, not copy). */
private const val SIZE_SAMPLE = "A"

/** Display name of a text size ("Default"). */
@Composable
fun textSizeLabel(size: AppTextSize): String = stringResource(
    when (size) {
        AppTextSize.SMALL -> R.string.text_size_small
        AppTextSize.DEFAULT -> R.string.text_size_default
        AppTextSize.LARGE -> R.string.text_size_large
        AppTextSize.LARGEST -> R.string.text_size_largest
    },
)

/** A line-spacing value with one decimal ("1.9"), in Tibetan digits for the bo UI. */
fun lineSpacingLabel(value: Float, language: AppLanguage): String {
    val text = String.format(Locale.US, "%.1f", LineSpacing.clamp(value))
    return if (language.usesTibetanDigits) TibetanNumerals.toTibetan(text) else text
}

/** The caption row above a slider: [label] (12 muted) and the current [value] (12/600 ink). */
@Composable
fun SliderHeader(label: String, value: String, modifier: Modifier = Modifier) {
    val type = DictTheme.type
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MixedText(label, type.caption, type.tibetanInline14, color = DictTheme.colors.muted)
        MixedText(value, type.sectionLabel, type.tibetanInline14, color = DictTheme.colors.ink)
    }
}

/** The four app text sizes on a stepped slider between a small and a large "A". */
@Composable
fun TextSizeSlider(value: AppTextSize, onValueChange: (AppTextSize) -> Unit, modifier: Modifier = Modifier) {
    val sizes = AppTextSize.entries
    val small = DictTheme.type.secondary
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EndGap)) {
        Text(SIZE_SAMPLE, Modifier.clearAndSetSemantics {}, DictTheme.colors.muted, style = small)
        DictSlider(
            value = value.ordinal.toFloat(),
            onValueChange = { onValueChange(sizes[it.roundToInt().coerceIn(sizes.indices)]) },
            valueRange = 0f..sizes.lastIndex.toFloat(),
            steps = sizes.size - 2,
            stateDescription = textSizeLabel(value),
            modifier = Modifier.weight(1f),
        )
        Text(
            SIZE_SAMPLE,
            Modifier.clearAndSetSemantics {},
            DictTheme.colors.muted,
            style = small.copy(fontSize = small.fontSize * LARGE_A_RATIO),
        )
    }
}

/** Tibetan line spacing from 1.5 to 2.3 in steps of 0.1; values are snapped with [LineSpacing.clamp]. */
@Composable
fun LineSpacingSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val steps = ((LineSpacing.MAX - LineSpacing.MIN) / LineSpacing.STEP).roundToInt() - 1
    DictSlider(
        value = LineSpacing.clamp(value),
        onValueChange = { onValueChange(LineSpacing.clamp(it)) },
        valueRange = LineSpacing.MIN..LineSpacing.MAX,
        steps = steps,
        stateDescription = lineSpacingLabel(value, DictTheme.language),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Material slider restyled: 4dp surface2 track with an accent active part, a 20dp accent thumb and no tick
 * marks; keeps Material's drag, keyboard and accessibility handling. [stateDescription] is what TalkBack reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    stateDescription: String,
    modifier: Modifier,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = colors.surface2,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { this.stateDescription = stateDescription },
        colors = sliderColors,
        steps = steps,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(dimens.sliderThumb)
                    .background(colors.accent, CircleShape),
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(dimens.sliderTrack),
            ) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(colors.surface2, cornerRadius = radius)
                drawRoundRect(colors.accent, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            }
        },
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun SlidersPreview() {
    ComponentPreview {
        SliderHeader(stringResource(R.string.text_size), textSizeLabel(AppTextSize.DEFAULT))
        TextSizeSlider(AppTextSize.DEFAULT, {})
        SliderHeader(stringResource(R.string.line_spacing), lineSpacingLabel(LineSpacing.DEFAULT, DictTheme.language))
        LineSpacingSlider(LineSpacing.DEFAULT, {})
        TextSizeSlider(AppTextSize.LARGEST, {})
        LineSpacingSlider(LineSpacing.MIN, {})
    }
}
