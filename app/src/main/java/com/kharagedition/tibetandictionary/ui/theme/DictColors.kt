package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Every colour token of the design system; two instances: [Light] and [Dark]. */
@Immutable
data class DictColors(
    val isDark: Boolean,
    /** Screens and sheets. */
    val background: Color,
    /** Fields, switcher track, cards, nav bar, sense cards, pressed row. */
    val surface1: Color,
    /** Unfilled dots, slider track, strong skeleton bars. */
    val surface2: Color,
    /** Nav indicator, tonal button/chip/badge, streak pill, selected card. */
    val accentContainer: Color,
    val onAccentContainer: Color,
    /** Filled buttons, selected segment, links, sense numbers, caret, match highlight, saved heart. */
    val accent: Color,
    val onAccent: Color,
    /** Primary text and icons. */
    val ink: Color,
    /** Secondary text, Wylie, placeholders, inactive nav. */
    val muted: Color,
    /** Version line, history icon. */
    val faint: Color,
    /** Chip/button borders, dashed boxes, outlined badges, sheet handle. */
    val outline: Color,
    /** Stats and benefit card borders. */
    val cardBorder: Color,
    /** List separators. */
    val divider: Color,
    /** Behind sheets and dialogs (alpha included). */
    val scrim: Color,
    /** Toast background. */
    val inverseSurface: Color,
    /** Toast text. */
    val inverseOnSurface: Color,
    /** Toast action. */
    val inverseAccent: Color,
    /** Sync dot. */
    val success: Color,
    /** Errors (the Material 3 error role). */
    val danger: Color,
    /** Prayer-flag strand line (alpha included). */
    val flagLine: Color,
    val flagBlue: Color = Color(0xFF3F6FB5),
    val flagWhite: Color = Color(0xFFEDE8DC),
    val flagRed: Color = Color(0xFFC8402F),
    val flagGreen: Color = Color(0xFF3F8F5E),
    val flagYellow: Color = Color(0xFFE2B232),
) {
    companion object {
        val Light = DictColors(
            isDark = false,
            background = Color(0xFFFBF8F3),
            surface1 = Color(0xFFF3EDE3),
            surface2 = Color(0xFFEADFCF),
            accentContainer = Color(0xFFF6DDD2),
            onAccentContainer = Color(0xFF4A1409),
            accent = Color(0xFF8A2E1C),
            onAccent = Color(0xFFFFFFFF),
            ink = Color(0xFF241E19),
            muted = Color(0xFF6B5F54),
            faint = Color(0xFFB5A89A),
            outline = Color(0xFFD8CDBB),
            cardBorder = Color(0xFFE7DECF),
            divider = Color(0xFFEFE7DA),
            scrim = Color(0xFF241E19).copy(alpha = 0.42f),
            inverseSurface = Color(0xFF241E19),
            inverseOnSurface = Color(0xFFF1E9E0),
            inverseAccent = Color(0xFFE8A58C),
            success = Color(0xFF3F8F5E),
            danger = Color(0xFFA8322D),
            flagLine = Color(0xFFB5A89A).copy(alpha = 0.6f),
        )

        val Dark = DictColors(
            isDark = true,
            background = Color(0xFF1B1715),
            surface1 = Color(0xFF262019),
            surface2 = Color(0xFF332B26),
            accentContainer = Color(0xFF5C2416),
            onAccentContainer = Color(0xFFF6DDD2),
            accent = Color(0xFFE8A58C),
            onAccent = Color(0xFF2E1109),
            ink = Color(0xFFF1E9E0),
            muted = Color(0xFFB5A89A),
            faint = Color(0xFF8C8075),
            outline = Color(0xFF4A403A),
            cardBorder = Color(0xFF332B26),
            divider = Color(0xFF332B26),
            scrim = Color(0xFF000000).copy(alpha = 0.5f),
            inverseSurface = Color(0xFFF1E9E0),
            inverseOnSurface = Color(0xFF241E19),
            inverseAccent = Color(0xFF8A2E1C),
            success = Color(0xFF3F8F5E),
            danger = Color(0xFFE08A86),
            flagLine = Color(0xFFB5A89A).copy(alpha = 0.5f),
        )
    }
}

/** Current colour set. */
val LocalDictColors = staticCompositionLocalOf { DictColors.Light }
