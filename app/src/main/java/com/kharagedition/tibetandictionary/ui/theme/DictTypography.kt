package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing

/** Jomolhari, the Tibetan face (single weight). Every Tibetan run renders in it. */
val Jomolhari = FontFamily(Font(R.font.jomolhari_regular, FontWeight.Normal))

/** Noto Sans 400, 500, 600 and italic 400. Every Latin run renders in it. */
val NotoSans = FontFamily(
    Font(R.font.noto_sans_regular, FontWeight.Normal),
    Font(R.font.noto_sans_medium, FontWeight.Medium),
    Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

@Suppress("DEPRECATION")
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val CenterTrimNone = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val W4 = FontWeight.Normal
private val W5 = FontWeight.Medium
private val W6 = FontWeight.SemiBold

/**
 * Every named text style of the design system, named by role (Noto Sans for Latin, Jomolhari for Tibetan).
 *
 * Sizes are in sp (the system font scale still applies) times [textScale], the app-wide Text size setting.
 * Latin line heights are `size × ratio × textScale`; Tibetan line heights are additionally multiplied by
 * `lineSpacing / 1.9` ([LineSpacing.DEFAULT]), the user's Tibetan line-spacing setting. Roles without a ratio
 * keep the font's natural line height. Upper-case roles (`overline`, `overlineWotd`) need the caller to
 * upper-case the text; accent-coloured variants take their colour from the caller.
 */
@Immutable
class DictTypography(
    val textScale: Float = 1f,
    val lineSpacing: Float = LineSpacing.DEFAULT,
) {
    private val tibetanLineFactor = lineSpacing / LineSpacing.DEFAULT

    private fun style(
        family: FontFamily,
        size: Float,
        weight: FontWeight,
        lineHeightSp: Float?,
        letterSpacing: TextUnit,
        italic: Boolean,
        paragraph: Boolean,
    ): TextStyle = TextStyle(
        fontFamily = family,
        fontSize = (size * textScale).sp,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = lineHeightSp?.sp ?: TextUnit.Unspecified,
        letterSpacing = letterSpacing,
        platformStyle = NoFontPadding,
        lineHeightStyle = CenterTrimNone,
        lineBreak = if (paragraph) LineBreak.Paragraph else LineBreak.Simple,
    )

    /** Noto Sans role: line height (when [ratio] is given) is `size × ratio × textScale`. */
    private fun latin(
        size: Float,
        weight: FontWeight = W4,
        ratio: Float? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        italic: Boolean = false,
    ): TextStyle = style(
        family = NotoSans,
        size = size,
        weight = weight,
        lineHeightSp = ratio?.let { size * it * textScale },
        letterSpacing = letterSpacing,
        italic = italic,
        paragraph = false,
    )

    /** Jomolhari role: line height is `size × ratio × textScale × (lineSpacing / 1.9)`. */
    private fun tibetan(size: Float, ratio: Float, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
        style(
            family = Jomolhari,
            size = size,
            weight = W4,
            lineHeightSp = size * ratio * textScale * tibetanLineFactor,
            letterSpacing = letterSpacing,
            italic = false,
            paragraph = true,
        )

    // Latin (Noto Sans)
    val screenTitle = latin(24f, W6, letterSpacing = (-0.01).em)
    val onboardingTitle = latin(26f, W6, ratio = 1.25f)
    val noResultsTitle = latin(17f, W6)

    /** Search field text (Focused and Compact fields). */
    val fieldText = latin(17f)

    /** Search field text on the Home field. */
    val fieldTextHome = latin(16f)
    val fieldPlaceholder = latin(16f)
    val headwordLatin = latin(16f, W5, ratio = 1.5f)
    val body = latin(15f, ratio = 1.6f)

    /** Word-of-the-day gloss. */
    val bodyWotdGloss = latin(15f, ratio = 1.5f)

    /** Wylie line on the entry screen. */
    val wylieEntry = latin(15f, italic = true)
    val bodySmall = latin(14f, ratio = 1.5f)

    /** Gloss line of a search-result row. */
    val bodySmallResultGloss = latin(14f, ratio = 1.45f)
    val bodySmallMedium = latin(14f, W5)
    val buttonLarge = latin(15f, W6)
    val button = latin(14f, W6)

    /** Label of an outlined button. */
    val buttonOutlined = latin(14f, W5)

    /** Labels; also the light-theme selected segment of the direction switcher. */
    val label = latin(13f, W5)

    /** Labels at 600; also the dark-theme selected segment of the direction switcher. */
    val labelSemibold = latin(13f, W6)

    /** Wylie under a result headword; the matched prefix is drawn at 600 in accent by the caller. */
    val wylie = latin(13f, italic = true)
    val secondary = latin(13f)
    val caption = latin(12f)
    val captionMedium = latin(12f, W5)
    val sectionLabel = latin(12f, W6)

    /** Upper-cased by the caller. */
    val overline = latin(11f, W6, letterSpacing = 0.06.em)

    /** Word-of-the-day overline (accent, upper-cased by the caller). */
    val overlineWotd = latin(11f, W6, letterSpacing = 0.08.em)
    val navLabel = latin(11f)
    val navLabelActive = latin(11f, W6)
    val tiny = latin(11f)
    val badge = latin(10f, W6, letterSpacing = 0.06.em)
    val dayLabel = latin(10f)
    val navGlossLatin = latin(9f)

    // Tibetan (Jomolhari)
    val tibetanEntryHeadword = tibetan(46f, 1.8f)
    val tibetanWotd = tibetan(34f, 1.9f)
    val tibetanSplash = tibetan(26f, 1.8f)

    /** Onboarding greeting (accent colour from the caller). */
    val tibetanGreeting = tibetan(26f, 1.7f)

    /** Screen title in the bo locale. */
    val tibetanScreenTitle = tibetan(26f, 1.7f)
    val tibetanRow = tibetan(24f, 1.8f)
    val tibetanSaved = tibetan(22f, 1.8f)
    val tibetanPreviewSense = tibetan(21f, 1.9f)
    val tibetanExample = tibetan(19f, 1.6f)
    val tibetanGloss = tibetan(18f, 1.6f)
    val tibetanChip = tibetan(17f, 1.5f)
    val tibetanInline16 = tibetan(16f, 1.5f)

    /** Tibetan run inside a toast. */
    val tibetanToast = tibetan(16f, 1.0f)
    val tibetanInline15 = tibetan(15f, 1.4f)

    /** "བོད" inside a direction-switcher segment. */
    val tibetanSegment = tibetan(13f, 1.4f)
    val tibetanInline14 = tibetan(14f, 1.4f)
    val tibetanNavPrimary = tibetan(13f, 1.3f)
    val tibetanBadge = tibetan(11f, 1.2f, letterSpacing = 0.em)
    val tibetanNavGloss = tibetan(10f, 1.2f)

    /** Latin roles by name, for the token preview and tests. */
    val latinRoles: Map<String, TextStyle> by lazy {
        linkedMapOf(
            "screenTitle" to screenTitle, "onboardingTitle" to onboardingTitle, "noResultsTitle" to noResultsTitle,
            "fieldText" to fieldText, "fieldTextHome" to fieldTextHome, "fieldPlaceholder" to fieldPlaceholder,
            "headwordLatin" to headwordLatin, "body" to body, "bodyWotdGloss" to bodyWotdGloss,
            "wylieEntry" to wylieEntry, "bodySmall" to bodySmall, "bodySmallResultGloss" to bodySmallResultGloss,
            "bodySmallMedium" to bodySmallMedium, "buttonLarge" to buttonLarge, "button" to button,
            "buttonOutlined" to buttonOutlined, "label" to label, "labelSemibold" to labelSemibold, "wylie" to wylie,
            "secondary" to secondary, "caption" to caption, "captionMedium" to captionMedium,
            "sectionLabel" to sectionLabel, "overline" to overline, "overlineWotd" to overlineWotd,
            "navLabel" to navLabel, "navLabelActive" to navLabelActive, "tiny" to tiny, "badge" to badge,
            "dayLabel" to dayLabel, "navGlossLatin" to navGlossLatin,
        )
    }

    /** Tibetan roles by name, for the token preview and tests. */
    val tibetanRoles: Map<String, TextStyle> by lazy {
        linkedMapOf(
            "tibetanEntryHeadword" to tibetanEntryHeadword, "tibetanWotd" to tibetanWotd,
            "tibetanSplash" to tibetanSplash, "tibetanGreeting" to tibetanGreeting,
            "tibetanScreenTitle" to tibetanScreenTitle, "tibetanRow" to tibetanRow, "tibetanSaved" to tibetanSaved,
            "tibetanPreviewSense" to tibetanPreviewSense, "tibetanExample" to tibetanExample,
            "tibetanGloss" to tibetanGloss, "tibetanChip" to tibetanChip, "tibetanInline16" to tibetanInline16,
            "tibetanToast" to tibetanToast, "tibetanInline15" to tibetanInline15, "tibetanSegment" to tibetanSegment,
            "tibetanInline14" to tibetanInline14, "tibetanNavPrimary" to tibetanNavPrimary,
            "tibetanBadge" to tibetanBadge, "tibetanNavGloss" to tibetanNavGloss,
        )
    }

    /** Every role by name (Latin first, then Tibetan). */
    val all: Map<String, TextStyle> by lazy { latinRoles + tibetanRoles }
}

/** Current typography. */
val LocalDictTypography = staticCompositionLocalOf { DictTypography() }
