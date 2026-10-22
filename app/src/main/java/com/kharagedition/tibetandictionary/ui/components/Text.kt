package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.resources
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Text and the style to draw it with, as produced by [mixedText]. */
internal data class StyledText(val text: AnnotatedString, val style: TextStyle)

/** [this] style's character attributes (family, size, weight, slant, tracking) as a span, in [color]. */
internal fun TextStyle.toRunSpan(color: Color = Color.Unspecified): SpanStyle = SpanStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontStyle = fontStyle,
    fontFamily = fontFamily,
    // A Tibetan run inside a tracked Latin role (overline, badge) must not inherit the tracking.
    letterSpacing = if (letterSpacing.isSpecified) letterSpacing else 0.em,
)

/** The tallest specified sp line height of [styles] (unspecified when none has one). */
internal fun tallestLineHeight(vararg styles: TextStyle): TextUnit =
    styles.map { it.lineHeight }.filter { it.isSpecified && it.isSp }.maxByOrNull { it.value } ?: TextUnit.Unspecified

/** True when [text] has a non-space character outside its Tibetan runs (Latin letters, digits, arrows, dots). */
internal fun hasNonTibetan(text: CharSequence): Boolean {
    val runs = TibetanScript.runs(text)
    return text.indices.any { i -> !text[i].isWhitespace() && runs.none { i in it } }
}

/**
 * Appends [text] with every Tibetan run in [tibetanStyle] (and [tibetanColor]); the rest gets [latinStyle] and
 * [latinColor] when given, otherwise it inherits the paragraph style. Existing spans of an [AnnotatedString] are
 * kept underneath.
 */
internal fun AnnotatedString.Builder.appendMixed(
    text: CharSequence,
    latinStyle: TextStyle?,
    tibetanStyle: TextStyle,
    latinColor: Color = Color.Unspecified,
    tibetanColor: Color = Color.Unspecified,
) {
    val start = length
    if (text is AnnotatedString) append(text) else append(text.toString())
    if (latinStyle != null || latinColor.isSpecified) {
        addStyle(latinStyle?.toRunSpan(latinColor) ?: SpanStyle(color = latinColor), start, length)
    }
    val span = tibetanStyle.toRunSpan(tibetanColor)
    TibetanScript.runs(text).forEach { addStyle(span, start + it.first, start + it.last + 1) }
}

/**
 * [text] split into Latin and Tibetan runs: Latin-only text keeps [latinStyle]; Tibetan-only text uses
 * [tibetanStyle] whole (Jomolhari, its own line height and paragraph line breaking); mixed text keeps
 * [latinStyle] with a [tibetanStyle] span on each Tibetan run, and the paragraph line height is the taller of
 * the two so Tibetan stacks are never clipped.
 */
internal fun mixedText(
    text: CharSequence,
    latinStyle: TextStyle,
    tibetanStyle: TextStyle,
    tibetanColor: Color = Color.Unspecified,
): StyledText {
    val annotated = text as? AnnotatedString ?: AnnotatedString(text.toString())
    if (!TibetanScript.hasTibetan(annotated.text)) return StyledText(annotated, latinStyle)
    if (!hasNonTibetan(annotated.text)) {
        val colored = if (tibetanColor.isSpecified) {
            buildAnnotatedString {
                append(annotated)
                addStyle(SpanStyle(color = tibetanColor), 0, length)
            }
        } else {
            annotated
        }
        return StyledText(colored, tibetanStyle)
    }
    val mixed = buildAnnotatedString { appendMixed(annotated, null, tibetanStyle, tibetanColor = tibetanColor) }
    return StyledText(mixed, latinStyle.copy(lineHeight = tallestLineHeight(latinStyle, tibetanStyle)))
}

/** Tibetan text in a Jomolhari role ([style], default `tibetanInline16`). */
@Composable
fun TibetanText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DictTheme.type.tibetanInline16,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
}

/** Latin text in a Noto Sans role ([style], default `body`). */
@Composable
fun LatinText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DictTheme.type.body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
}

/**
 * Text that may mix scripts: runs found by [TibetanScript.isTibetan] draw in [tibetanStyle], the rest in
 * [latinStyle] (see [mixedText] for the line-height rule). [tibetanColor] recolours only the Tibetan runs.
 * Use it for any copy that can hold both scripts: string resources in the bo locale, greetings, placeholders,
 * toasts, direction labels and glosses.
 */
@Composable
fun MixedText(
    text: CharSequence,
    latinStyle: TextStyle,
    tibetanStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    tibetanColor: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val styled = remember(text, latinStyle, tibetanStyle, tibetanColor) {
        mixedText(text, latinStyle, tibetanStyle, tibetanColor)
    }
    Text(
        styled.text,
        modifier,
        color = color,
        style = styled.style,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

/**
 * A screen title with an optional [gloss] in the other script, as one wrapping heading, and an optional
 * [trailing] element baseline-aligned at the end ("42 words"). Latin titles draw at 24/600 with a Jomolhari 18
 * gloss; Tibetan titles (bo UI) at Jomolhari 26/1.7 with a Latin 13 gloss.
 */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    gloss: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val type = DictTheme.type
    val muted = DictTheme.colors.muted
    val styled = remember(title, gloss, type, muted) {
        val text = buildAnnotatedString {
            appendMixed(title, null, type.tibetanScreenTitle)
            if (gloss != null) {
                append(GLOSS_SEPARATOR)
                appendMixed(gloss, type.secondary, type.tibetanGloss, latinColor = muted, tibetanColor = muted)
            }
        }
        val base = if (hasNonTibetan(title)) type.screenTitle else type.tibetanScreenTitle
        val involved = listOfNotNull(
            base,
            type.tibetanScreenTitle.takeIf { TibetanScript.hasTibetan(title) },
            type.tibetanGloss.takeIf { gloss != null && TibetanScript.hasTibetan(gloss) },
        )
        StyledText(text, base.copy(lineHeight = tallestLineHeight(*involved.toTypedArray())))
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            styled.text,
            Modifier
                .weight(1f)
                .alignByBaseline()
                .semantics { heading() },
            color = DictTheme.colors.ink,
            style = styled.style,
        )
        if (trailing != null) Box(Modifier.alignByBaseline()) { trailing() }
    }
}

/** Two spaces: the spec's 4dp gap between a title and its gloss. */
private const val GLOSS_SEPARATOR = "  "

/**
 * Small upper-cased label (11/600, tracked) in [color]; Tibetan runs draw in Jomolhari 15 (upper-casing leaves
 * them unchanged). Pass `DictTheme.type.overlineWotd` and the accent for the word-of-the-day variant.
 */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DictTheme.type.overline,
    color: Color = DictTheme.colors.muted,
) {
    MixedText(text.uppercase(), style, DictTheme.type.tibetanInline15, modifier, color = color)
}

/** The Home greeting line for [timeOfDay] in [pack]'s language ("Good afternoon · ཕྱི་དྲོ་བདེ་ལེགས།"), 12 muted. */
@Composable
fun Greeting(timeOfDay: TimeOfDay, modifier: Modifier = Modifier, pack: DictionaryPack = LocalActivePack.current) {
    val resources = pack.resources
    val text = stringResource(
        when (timeOfDay) {
            TimeOfDay.MORNING -> resources.greetingMorning
            TimeOfDay.AFTERNOON -> resources.greetingAfternoon
            TimeOfDay.EVENING -> resources.greetingEvening
        },
    )
    MixedText(text, DictTheme.type.caption, DictTheme.type.tibetanInline14, modifier, color = DictTheme.colors.muted)
}

@PreviewLightDark
@BoPreview
@Composable
private fun TextPreview() {
    ComponentPreview {
        ScreenTitle(
            title = stringResource(R.string.saved_title),
            gloss = stringResource(R.string.saved_gloss),
            trailing = {
                LatinText("42 words", style = DictTheme.type.caption, color = DictTheme.colors.muted)
            },
        )
        ScreenTitle(title = "སྒེར་གྱི་སྒྲིག་འགོད།", gloss = "Profile")
        Greeting(TimeOfDay.AFTERNOON)
        Overline("Today")
        Overline(stringResource(R.string.wotd_overline), style = DictTheme.type.overlineWotd, color = DictTheme.colors.accent)
        MixedText(
            "Tip: type Wylie, e.g. bkra shis, and it matches བཀྲ་ཤིས།",
            DictTheme.type.bodySmall,
            DictTheme.type.tibetanInline16,
            tibetanColor = DictTheme.colors.accent,
        )
        Greeting(TimeOfDay.MORNING, pack = DictionaryPack.DZONGKHA)
        TibetanText("བཀྲ་ཤིས་བདེ་ལེགས།", style = DictTheme.type.tibetanGreeting, color = DictTheme.colors.accent)
        LatinText("Which way do you look up words most?", style = DictTheme.type.onboardingTitle)
    }
}
