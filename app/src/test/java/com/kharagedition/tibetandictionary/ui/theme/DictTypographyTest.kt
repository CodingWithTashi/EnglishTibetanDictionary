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
    fun `tibetanEntryHeadword is 48sp on an 86_4sp line at defaults`() {
        val style = defaults.tibetanEntryHeadword

        assertSp(style.fontSize, 48f)
        assertSp(style.lineHeight, 86.4f)
        assertThat(style.fontFamily).isEqualTo(Jomolhari)
        assertThat(style.lineBreak).isEqualTo(LineBreak.Paragraph)
    }

    @Test
    fun `screenTitle is Noto Sans 26sp semibold with -0_01em tracking`() {
        val style = defaults.screenTitle

        assertSp(style.fontSize, 26f)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.fontFamily).isEqualTo(NotoSans)
        assertThat(style.letterSpacing).isEqualTo(TextUnit(-0.01f, TextUnitType.Em))
    }

    @Test
    fun `Tibetan line height scales with lineSpacing over 1_9 and font size does not`() {
        val loose = DictTypography(textScale = 1f, lineSpacing = 2.3f)
        val tight = DictTypography(textScale = 1f, lineSpacing = 1.5f)

        assertSp(loose.tibetanEntryHeadword.fontSize, 48f)
        assertSp(loose.tibetanEntryHeadword.lineHeight, 48f * 1.8f * 2.3f / 1.9f)
        assertSp(tight.tibetanEntryHeadword.lineHeight, 48f * 1.8f * 1.5f / 1.9f)
        assertSp(loose.tibetanRow.lineHeight, 26f * 1.8f * 2.3f / 1.9f)
        assertSp(tight.tibetanGloss.lineHeight, 20f * 1.6f * 1.5f / 1.9f)
    }

    @Test
    fun `Latin line height ignores lineSpacing`() {
        for (spacing in listOf(1.5f, 1.9f, 2.3f)) {
            val type = DictTypography(textScale = 1f, lineSpacing = spacing)

            assertSp(type.body.lineHeight, 17f * 1.6f)
            assertSp(type.bodySmall.lineHeight, 16f * 1.5f)
            assertSp(type.headwordLatin.lineHeight, 18f * 1.5f)
            assertSp(type.onboardingTitle.lineHeight, 28f * 1.25f)
        }
    }

    @Test
    fun `textScale multiplies Latin and Tibetan sizes and line heights`() {
        val large = DictTypography(textScale = 1.3f, lineSpacing = 1.9f)

        assertSp(large.body.fontSize, 17f * 1.3f)
        assertSp(large.body.lineHeight, 17f * 1.6f * 1.3f)
        assertSp(large.screenTitle.fontSize, 26f * 1.3f)
        assertSp(large.tibetanEntryHeadword.fontSize, 48f * 1.3f)
        assertSp(large.tibetanEntryHeadword.lineHeight, 48f * 1.8f * 1.3f)
    }

    @Test
    fun `textScale and lineSpacing combine for Tibetan line heights`() {
        val type = DictTypography(textScale = 1.15f, lineSpacing = 1.5f)

        assertSp(type.tibetanRow.fontSize, 26f * 1.15f)
        assertSp(type.tibetanRow.lineHeight, 26f * 1.8f * 1.15f * (1.5f / 1.9f))
        assertSp(type.body.lineHeight, 17f * 1.6f * 1.15f)
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

        /** Noto Sans roles: the design handoff (§6.3) sizes + 2sp (owner request), including the named variants. */
        val LATIN_SPEC = mapOf(
            "screenTitle" to Spec(26f, 600, trackingEm = -0.01f),
            "onboardingTitle" to Spec(28f, 600, ratio = 1.25f),
            "noResultsTitle" to Spec(19f, 600),
            "fieldText" to Spec(19f),
            "fieldTextHome" to Spec(18f),
            "fieldPlaceholder" to Spec(18f),
            "headwordLatin" to Spec(18f, 500, ratio = 1.5f),
            "body" to Spec(17f, ratio = 1.6f),
            "bodyWotdGloss" to Spec(17f, ratio = 1.5f),
            "wylieEntry" to Spec(17f, italic = true),
            "bodySmall" to Spec(16f, ratio = 1.5f),
            "bodySmallResultGloss" to Spec(16f, ratio = 1.45f),
            "bodySmallMedium" to Spec(16f, 500),
            "buttonLarge" to Spec(17f, 600),
            "button" to Spec(16f, 600),
            "buttonOutlined" to Spec(16f, 500),
            "label" to Spec(15f, 500),
            "labelSemibold" to Spec(15f, 600),
            "wylie" to Spec(15f, italic = true),
            "secondary" to Spec(15f),
            "caption" to Spec(14f),
            "captionMedium" to Spec(14f, 500),
            "sectionLabel" to Spec(14f, 600),
            "overline" to Spec(13f, 600, trackingEm = 0.06f),
            "overlineWotd" to Spec(13f, 600, trackingEm = 0.08f),
            "navLabel" to Spec(13f),
            "navLabelActive" to Spec(13f, 600),
            "tiny" to Spec(13f),
            "badge" to Spec(12f, 600, trackingEm = 0.06f),
            "dayLabel" to Spec(12f),
            "navGlossLatin" to Spec(11f),
        )

        /** Jomolhari roles (size / line ratio): the design handoff (§6.3) sizes + 2sp (owner request). */
        val TIBETAN_SPEC = mapOf(
            "tibetanEntryHeadword" to Spec(48f, ratio = 1.8f),
            "tibetanWotd" to Spec(36f, ratio = 1.9f),
            "tibetanSplash" to Spec(28f, ratio = 1.8f),
            "tibetanGreeting" to Spec(28f, ratio = 1.7f),
            "tibetanScreenTitle" to Spec(28f, ratio = 1.7f),
            "tibetanRow" to Spec(26f, ratio = 1.8f),
            "tibetanSaved" to Spec(24f, ratio = 1.8f),
            "tibetanSample" to Spec(24f, ratio = 1.9f),
            "tibetanPreviewSense" to Spec(23f, ratio = 1.9f),
            "tibetanExample" to Spec(21f, ratio = 1.6f),
            "tibetanGloss" to Spec(20f, ratio = 1.6f),
            "tibetanChip" to Spec(19f, ratio = 1.5f),
            "tibetanInline16" to Spec(18f, ratio = 1.5f),
            "tibetanToast" to Spec(18f, ratio = 1.0f),
            "tibetanInline15" to Spec(17f, ratio = 1.4f),
            "tibetanSegment" to Spec(15f, ratio = 1.4f),
            "tibetanInline14" to Spec(16f, ratio = 1.4f),
            "tibetanNavPrimary" to Spec(15f, ratio = 1.3f),
            "tibetanBadge" to Spec(13f, ratio = 1.2f, trackingEm = 0f),
            "tibetanNavGloss" to Spec(12f, ratio = 1.2f),
        )
    }
}
