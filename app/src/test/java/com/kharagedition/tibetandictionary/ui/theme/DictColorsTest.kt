package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class DictColorsTest {

    @Test
    fun `light palette matches the design table`() {
        assertThat(DictColors.Light.isDark).isFalse()
        assertPalette(DictColors.Light, LIGHT)
    }

    @Test
    fun `dark palette matches the design table`() {
        assertThat(DictColors.Dark.isDark).isTrue()
        assertPalette(DictColors.Dark, DARK)
    }

    @Test
    fun `the tables cover every colour field`() {
        // Color is an inline class, so each colour property compiles to a mangled getter returning long.
        val fields = DictColors::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 }
            .filter { it.returnType == Long::class.javaPrimitiveType }
            .map { it.name.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercaseChar) }

        assertThat(fields).containsExactlyElementsIn(LIGHT.keys)
        assertThat(DARK.keys).containsExactlyElementsIn(LIGHT.keys)
    }

    private fun assertPalette(colors: DictColors, expected: Map<String, Long>) {
        val actual = colors.roles()
        assertThat(actual.keys).containsExactlyElementsIn(expected.keys)
        for ((name, argb) in expected) {
            assertWithMessage(name).that(actual.getValue(name).hex()).isEqualTo(argb.toInt().hex())
        }
    }

    private fun Color.hex(): String = toArgb().hex()

    private fun Int.hex(): String = "#" + Integer.toHexString(this).uppercase().padStart(8, '0')

    private fun DictColors.roles(): Map<String, Color> = mapOf(
        "background" to background, "surface1" to surface1, "surface2" to surface2,
        "accentContainer" to accentContainer, "onAccentContainer" to onAccentContainer,
        "accent" to accent, "onAccent" to onAccent, "ink" to ink, "muted" to muted, "faint" to faint,
        "outline" to outline, "cardBorder" to cardBorder, "divider" to divider, "scrim" to scrim,
        "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface, "inverseAccent" to inverseAccent,
        "success" to success, "danger" to danger, "flagLine" to flagLine, "flagBlue" to flagBlue,
        "flagWhite" to flagWhite, "flagRed" to flagRed, "flagGreen" to flagGreen, "flagYellow" to flagYellow,
    )

    private companion object {
        /** Design handoff §6.1, light column (ARGB; alpha roles: scrim 42 %, flagLine 60 %). */
        val LIGHT = mapOf(
            "background" to 0xFFFBF8F3,
            "surface1" to 0xFFF3EDE3,
            "surface2" to 0xFFEADFCF,
            "accentContainer" to 0xFFF6DDD2,
            "onAccentContainer" to 0xFF4A1409,
            "accent" to 0xFF8A2E1C,
            "onAccent" to 0xFFFFFFFF,
            "ink" to 0xFF241E19,
            "muted" to 0xFF6B5F54,
            "faint" to 0xFFB5A89A,
            "outline" to 0xFFD8CDBB,
            "cardBorder" to 0xFFE7DECF,
            "divider" to 0xFFEFE7DA,
            "scrim" to 0x6B241E19,
            "inverseSurface" to 0xFF241E19,
            "inverseOnSurface" to 0xFFF1E9E0,
            "inverseAccent" to 0xFFE8A58C,
            "success" to 0xFF3F8F5E,
            "danger" to 0xFFA8322D,
            "flagLine" to 0x99B5A89A,
            "flagBlue" to 0xFF3F6FB5,
            "flagWhite" to 0xFFEDE8DC,
            "flagRed" to 0xFFC8402F,
            "flagGreen" to 0xFF3F8F5E,
            "flagYellow" to 0xFFE2B232,
        )

        /** Design handoff §6.1, dark column (ARGB; alpha roles: scrim 50 % black, flagLine 50 %). */
        val DARK = mapOf(
            "background" to 0xFF1B1715,
            "surface1" to 0xFF262019,
            "surface2" to 0xFF332B26,
            "accentContainer" to 0xFF5C2416,
            "onAccentContainer" to 0xFFF6DDD2,
            "accent" to 0xFFE8A58C,
            "onAccent" to 0xFF2E1109,
            "ink" to 0xFFF1E9E0,
            "muted" to 0xFFB5A89A,
            "faint" to 0xFF8C8075,
            "outline" to 0xFF4A403A,
            "cardBorder" to 0xFF332B26,
            "divider" to 0xFF332B26,
            "scrim" to 0x80000000,
            "inverseSurface" to 0xFFF1E9E0,
            "inverseOnSurface" to 0xFF241E19,
            "inverseAccent" to 0xFF8A2E1C,
            "success" to 0xFF3F8F5E,
            "danger" to 0xFFE08A86,
            "flagLine" to 0x80B5A89A,
            "flagBlue" to 0xFF3F6FB5,
            "flagWhite" to 0xFFEDE8DC,
            "flagRed" to 0xFFC8402F,
            "flagGreen" to 0xFF3F8F5E,
            "flagYellow" to 0xFFE2B232,
        )
    }
}
