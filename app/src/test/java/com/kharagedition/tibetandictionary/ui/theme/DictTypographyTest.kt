package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class DictTypographyTest {

    private val defaults = DictTypography()

    @Test
    fun `defaults are text scale 1 and line spacing 1_9`() {
        assertThat(defaults.textScale).isEqualTo(1f)
        assertThat(defaults.lineSpacing).isEqualTo(1.9f)
    }

    @Test
    fun `tibetanEntryHeadword is 46sp on an 82_8sp line at defaults`() {
        val style = defaults.tibetanEntryHeadword

        assertSp(style.fontSize, 46f)
        assertSp(style.lineHeight, 82.8f)
        assertThat(style.fontFamily).isEqualTo(Jomolhari)
        assertThat(style.lineBreak).isEqualTo(LineBreak.Paragraph)
    }

    @Test
    fun `screenTitle is Noto Sans 24sp semibold with -0_01em tracking`() {
        val style = defaults.screenTitle

        assertSp(style.fontSize, 24f)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.fontFamily).isEqualTo(NotoSans)
        assertThat(style.letterSpacing).isEqualTo(TextUnit(-0.01f, TextUnitType.Em))
    }

    @Test
    fun `Tibetan line height scales with lineSpacing over 1_9 and font size does not`() {
        val loose = DictTypography(textScale = 1f, lineSpacing = 2.3f)
        val tight = DictTypography(textScale = 1f, lineSpacing = 1.5f)

        assertSp(loose.tibetanEntryHeadword.fontSize, 46f)
        assertSp(loose.tibetanEntryHeadword.lineHeight, 46f * 1.8f * 2.3f / 1.9f)
        assertSp(tight.tibetanEntryHeadword.lineHeight, 46f * 1.8f * 1.5f / 1.9f)
        assertSp(loose.tibetanRow.lineHeight, 24f * 1.8f * 2.3f / 1.9f)
        assertSp(tight.tibetanGloss.lineHeight, 18f * 1.6f * 1.5f / 1.9f)
    }

    @Test
    fun `Latin line height ignores lineSpacing`() {
        for (spacing in listOf(1.5f, 1.9f, 2.3f)) {
            val type = DictTypography(textScale = 1f, lineSpacing = spacing)

            assertSp(type.body.lineHeight, 15f * 1.6f)
            assertSp(type.bodySmall.lineHeight, 14f * 1.5f)
            assertSp(type.headwordLatin.lineHeight, 16f * 1.5f)
            assertSp(type.onboardingTitle.lineHeight, 26f * 1.25f)
        }
    }

    @Test
    fun `textScale multiplies Latin and Tibetan sizes and line heights`() {
        val large = DictTypography(textScale = 1.3f, lineSpacing = 1.9f)

        assertSp(large.body.fontSize, 15f * 1.3f)
        assertSp(large.body.lineHeight, 15f * 1.6f * 1.3f)
        assertSp(large.screenTitle.fontSize, 24f * 1.3f)
        assertSp(large.tibetanEntryHeadword.fontSize, 46f * 1.3f)
        assertSp(large.tibetanEntryHeadword.lineHeight, 46f * 1.8f * 1.3f)
    }

    @Test
    fun `textScale and lineSpacing combine for Tibetan line heights`() {
        val type = DictTypography(textScale = 1.15f, lineSpacing = 1.5f)

        assertSp(type.tibetanRow.fontSize, 24f * 1.15f)
        assertSp(type.tibetanRow.lineHeight, 24f * 1.8f * 1.15f * (1.5f / 1.9f))
        assertSp(type.body.lineHeight, 15f * 1.6f * 1.15f)
    }

    @Test
    fun `every Latin role matches the design table`() {
        val type = DictTypography(textScale = 1f, lineSpacing = 2.3f)
        assertThat(type.latinRoles.keys).containsExactlyElementsIn(LATIN_SPEC.keys)
        for ((name, spec) in LATIN_SPEC) {
            val style = type.latinRoles.getValue(name)
            assertWithMessage("$name family").that(style.fontFamily).isEqualTo(NotoSans)
            assertRole(name, style, spec)
        }
    }

    @Test
    fun `every Tibetan role matches the design table at default spacing`() {
        assertThat(defaults.tibetanRoles.keys).containsExactlyElementsIn(TIBETAN_SPEC.keys)
        for ((name, spec) in TIBETAN_SPEC) {
            val style = defaults.tibetanRoles.getValue(name)
            assertWithMessage("$name family").that(style.fontFamily).isEqualTo(Jomolhari)
            assertWithMessage("$name line break").that(style.lineBreak).isEqualTo(LineBreak.Paragraph)
            assertRole(name, style, spec)
        }
    }

    @Test
    fun `every role trims font padding and centres its line height`() {
        for ((name, style) in defaults.all) {
            assertWithMessage("$name includeFontPadding")
                .that(style.platformStyle?.paragraphStyle?.includeFontPadding).isFalse()
            assertWithMessage("$name lineHeightStyle").that(style.lineHeightStyle)
                .isEqualTo(LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
        }
    }

    @Test
    fun `role maps expose the same instances as the named properties`() {
        assertThat(defaults.all.keys).containsExactlyElementsIn(LATIN_SPEC.keys + TIBETAN_SPEC.keys)
        for ((name, style) in defaults.all) {
            val getter = DictTypography::class.java.getMethod("get" + name.replaceFirstChar(Char::uppercaseChar))
            assertWithMessage(name).that(getter.invoke(defaults)).isSameInstanceAs(style)
        }
    }

    private data class Spec(
        val size: Float,
        val weight: Int = 400,
        val ratio: Float? = null,
        val trackingEm: Float? = null,
        val italic: Boolean = false,
    )

    private fun assertRole(name: String, style: TextStyle, spec: Spec) {
        assertWithMessage("$name size").that(style.fontSize.type).isEqualTo(TextUnitType.Sp)
        assertWithMessage("$name size").that(style.fontSize.value).isWithin(TOLERANCE).of(spec.size)
        assertWithMessage("$name weight").that(style.fontWeight).isEqualTo(FontWeight(spec.weight))
        assertWithMessage("$name style").that(style.fontStyle)
            .isEqualTo(if (spec.italic) FontStyle.Italic else FontStyle.Normal)
        if (spec.ratio == null) {
            assertWithMessage("$name line height").that(style.lineHeight).isEqualTo(TextUnit.Unspecified)
        } else {
            assertWithMessage("$name line height").that(style.lineHeight.type).isEqualTo(TextUnitType.Sp)
            assertWithMessage("$name line height").that(style.lineHeight.value)
                .isWithin(TOLERANCE).of(spec.size * spec.ratio)
        }
        if (spec.trackingEm == null) {
            assertWithMessage("$name tracking").that(style.letterSpacing).isEqualTo(TextUnit.Unspecified)
        } else {
            assertWithMessage("$name tracking").that(style.letterSpacing)
                .isEqualTo(TextUnit(spec.trackingEm, TextUnitType.Em))
        }
    }

    private fun assertSp(actual: TextUnit, expected: Float) {
        assertThat(actual.type).isEqualTo(TextUnitType.Sp)
        assertThat(actual.value).isWithin(TOLERANCE).of(expected)
    }

    private companion object {
        const val TOLERANCE = 0.001f

        /** Noto Sans roles from the design handoff (§6.3), including the named variants. */
        val LATIN_SPEC = mapOf(
            "screenTitle" to Spec(24f, 600, trackingEm = -0.01f),
            "onboardingTitle" to Spec(26f, 600, ratio = 1.25f),
            "noResultsTitle" to Spec(17f, 600),
            "fieldText" to Spec(17f),
            "fieldTextHome" to Spec(16f),
            "fieldPlaceholder" to Spec(16f),
            "headwordLatin" to Spec(16f, 500, ratio = 1.5f),
            "body" to Spec(15f, ratio = 1.6f),
            "bodyWotdGloss" to Spec(15f, ratio = 1.5f),
            "wylieEntry" to Spec(15f, italic = true),
            "bodySmall" to Spec(14f, ratio = 1.5f),
            "bodySmallResultGloss" to Spec(14f, ratio = 1.45f),
            "bodySmallMedium" to Spec(14f, 500),
            "buttonLarge" to Spec(15f, 600),
            "button" to Spec(14f, 600),
            "buttonOutlined" to Spec(14f, 500),
            "label" to Spec(13f, 500),
            "labelSemibold" to Spec(13f, 600),
            "wylie" to Spec(13f, italic = true),
            "secondary" to Spec(13f),
            "caption" to Spec(12f),
            "captionMedium" to Spec(12f, 500),
            "sectionLabel" to Spec(12f, 600),
            "overline" to Spec(11f, 600, trackingEm = 0.06f),
            "overlineWotd" to Spec(11f, 600, trackingEm = 0.08f),
            "navLabel" to Spec(11f),
            "navLabelActive" to Spec(11f, 600),
            "tiny" to Spec(11f),
            "badge" to Spec(10f, 600, trackingEm = 0.06f),
            "dayLabel" to Spec(10f),
            "navGlossLatin" to Spec(9f),
        )

        /** Jomolhari roles (size / line ratio) from the design handoff (§6.3), including the named variants. */
        val TIBETAN_SPEC = mapOf(
            "tibetanEntryHeadword" to Spec(46f, ratio = 1.8f),
            "tibetanWotd" to Spec(34f, ratio = 1.9f),
            "tibetanSplash" to Spec(26f, ratio = 1.8f),
            "tibetanGreeting" to Spec(26f, ratio = 1.7f),
            "tibetanScreenTitle" to Spec(26f, ratio = 1.7f),
            "tibetanRow" to Spec(24f, ratio = 1.8f),
            "tibetanSaved" to Spec(22f, ratio = 1.8f),
            "tibetanPreviewSense" to Spec(21f, ratio = 1.9f),
            "tibetanExample" to Spec(19f, ratio = 1.6f),
            "tibetanGloss" to Spec(18f, ratio = 1.6f),
            "tibetanChip" to Spec(17f, ratio = 1.5f),
            "tibetanInline16" to Spec(16f, ratio = 1.5f),
            "tibetanToast" to Spec(16f, ratio = 1.0f),
            "tibetanInline15" to Spec(15f, ratio = 1.4f),
            "tibetanSegment" to Spec(13f, ratio = 1.4f),
            "tibetanInline14" to Spec(14f, ratio = 1.4f),
            "tibetanNavPrimary" to Spec(13f, ratio = 1.3f),
            "tibetanBadge" to Spec(11f, ratio = 1.2f, trackingEm = 0f),
            "tibetanNavGloss" to Spec(10f, ratio = 1.2f),
        )
    }
}
