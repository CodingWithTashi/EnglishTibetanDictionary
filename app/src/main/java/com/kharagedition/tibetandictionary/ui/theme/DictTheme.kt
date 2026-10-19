package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.core.os.ConfigurationCompat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing

/** Current UI language (en/bo). */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

/**
 * Material 3 colour scheme bridged from these tokens, so stock M3 components pick up the palette. Every role
 * is set, so no M3 baseline (purple) colour reaches a stock component. [ColorScheme.surfaceTint] is the
 * background, so tonal elevation never tints a surface.
 */
internal fun DictColors.materialColorScheme(): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = inverseAccent,
        secondary = muted,
        onSecondary = background,
        secondaryContainer = surface2,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = onAccentContainer,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = surface1,
        onSurfaceVariant = muted,
        surfaceTint = background,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = danger,
        onError = onAccent,
        errorContainer = accentContainer,
        onErrorContainer = danger,
        outline = outline,
        outlineVariant = divider,
        scrim = scrim,
        // Bright is the lightest surface and dim the darkest, so the two swap between palettes.
        surfaceBright = if (isDark) surface2 else background,
        surfaceDim = if (isDark) background else surface2,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface1,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface2,
        primaryFixed = accentContainer,
        primaryFixedDim = accentContainer,
        onPrimaryFixed = onAccentContainer,
        onPrimaryFixedVariant = onAccentContainer,
        secondaryFixed = surface1,
        secondaryFixedDim = surface2,
        onSecondaryFixed = ink,
        onSecondaryFixedVariant = muted,
        tertiaryFixed = accentContainer,
        tertiaryFixedDim = accentContainer,
        onTertiaryFixed = onAccentContainer,
        onTertiaryFixedVariant = onAccentContainer,
    )

private fun TextStyle.inNotoSans(scale: Float): TextStyle = copy(
    fontFamily = NotoSans,
    fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)

/** M3 type scale in Noto Sans, scaled by [textScale], for stock M3 components. */
private fun materialTypography(textScale: Float): Typography {
    val d = Typography()
    fun TextStyle.s() = inNotoSans(textScale)
    return Typography(
        displayLarge = d.displayLarge.s(), displayMedium = d.displayMedium.s(),
        displaySmall = d.displaySmall.s(), headlineLarge = d.headlineLarge.s(),
        headlineMedium = d.headlineMedium.s(), headlineSmall = d.headlineSmall.s(),
        titleLarge = d.titleLarge.s(), titleMedium = d.titleMedium.s(),
        titleSmall = d.titleSmall.s(), bodyLarge = d.bodyLarge.s(),
        bodyMedium = d.bodyMedium.s(), bodySmall = d.bodySmall.s(),
        labelLarge = d.labelLarge.s(), labelMedium = d.labelMedium.s(),
        labelSmall = d.labelSmall.s(),
    )
}

/**
 * Root theme: provides the colour, type, dimens and language locals, the content colour (ink), the ripple
 * colour (accent) and the Material 3 bridge (colour scheme, shapes, typography).
 *
 * [textScale] is the app-wide Text size setting ([com.kharagedition.tibetandictionary.data.model.AppTextSize]);
 * [lineSpacing] is the Tibetan line-spacing setting ([LineSpacing]).
 */
@Composable
fun DictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    lineSpacing: Float = LineSpacing.DEFAULT,
    language: AppLanguage = AppLanguage.fromTag(
        ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language,
    ),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DictColors.Dark else DictColors.Light
    val type = remember(textScale, lineSpacing) { DictTypography(textScale, lineSpacing) }
    val dimens = DictDimens.forLanguage(language)
    val scheme = remember(colors) { colors.materialColorScheme() }
    val m3Typography = remember(textScale) { materialTypography(textScale) }
    val ripple = remember(colors.accent) { RippleConfiguration(color = colors.accent) }
    CompositionLocalProvider(
        LocalDictColors provides colors,
        LocalDictTypography provides type,
        LocalDictDimens provides dimens,
        LocalAppLanguage provides language,
        LocalContentColor provides colors.ink,
        LocalRippleConfiguration provides ripple,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = DictShapes.material, typography = m3Typography, content = content)
    }
}

/** Accessors for the current theme locals. */
object DictTheme {
    val colors: DictColors @Composable @ReadOnlyComposable get() = LocalDictColors.current
    val type: DictTypography @Composable @ReadOnlyComposable get() = LocalDictTypography.current
    val dimens: DictDimens @Composable @ReadOnlyComposable get() = LocalDictDimens.current
    val language: AppLanguage @Composable @ReadOnlyComposable get() = LocalAppLanguage.current
}
