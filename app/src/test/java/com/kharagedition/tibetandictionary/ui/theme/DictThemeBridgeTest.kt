package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import org.junit.Test

class DictThemeBridgeTest {

    @Test
    fun `every Material 3 role maps to its token in both palettes`() {
        for (colors in listOf(DictColors.Light, DictColors.Dark)) {
            val actual = colors.materialColorScheme().roles()
            val expected = expectedRoles(colors)

            assertThat(actual.keys).containsExactlyElementsIn(expected.keys)
            for ((role, color) in expected) {
                assertWithMessage("${colors.name} $role").that(actual.getValue(role)).isEqualTo(color)
            }
        }
    }

    @Test
    fun `no Material 3 role keeps its baseline colour`() {
        for (colors in listOf(DictColors.Light, DictColors.Dark)) {
            val baseline = (if (colors.isDark) darkColorScheme() else lightColorScheme()).roles()
            val actual = colors.materialColorScheme().roles()

            assertThat(actual.keys).containsExactlyElementsIn(baseline.keys)
            for ((role, color) in actual) {
                if (colors.name to role in TOKEN_EQUALS_BASELINE) {
                    // The token itself is the baseline value; the mapping is still pinned by the test above.
                    assertWithMessage("${colors.name} $role").that(color).isEqualTo(baseline.getValue(role))
                    continue
                }
                assertWithMessage("${colors.name} $role is the M3 baseline").that(color)
                    .isNotEqualTo(baseline.getValue(role))
            }
        }
    }

    @Test
    fun `dimens grow settings rows for Tibetan UI only`() {
        assertWithMessage("en").that(DictDimens.forLanguage(AppLanguage.EN).settingsRow.value).isEqualTo(52f)
        assertWithMessage("bo").that(DictDimens.forLanguage(AppLanguage.BO).settingsRow.value).isEqualTo(56f)
        assertWithMessage("other rows").that(DictDimens.Bo.historyRow).isEqualTo(DictDimens.En.historyRow)
    }

    private val DictColors.name get() = if (isDark) "dark" else "light"

    /** Every role of a Material 3 (1.4) colour scheme, by name. */
    private fun ColorScheme.roles(): Map<String, Color> = mapOf(
        "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer, "inversePrimary" to inversePrimary,
        "secondary" to secondary, "onSecondary" to onSecondary, "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary, "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background, "onBackground" to onBackground, "surface" to surface, "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant, "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
        "error" to error, "onError" to onError, "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline, "outlineVariant" to outlineVariant, "scrim" to scrim,
        "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "primaryFixed" to primaryFixed, "primaryFixedDim" to primaryFixedDim, "onPrimaryFixed" to onPrimaryFixed,
        "onPrimaryFixedVariant" to onPrimaryFixedVariant,
        "secondaryFixed" to secondaryFixed, "secondaryFixedDim" to secondaryFixedDim,
        "onSecondaryFixed" to onSecondaryFixed, "onSecondaryFixedVariant" to onSecondaryFixedVariant,
        "tertiaryFixed" to tertiaryFixed, "tertiaryFixedDim" to tertiaryFixedDim,
        "onTertiaryFixed" to onTertiaryFixed, "onTertiaryFixedVariant" to onTertiaryFixedVariant,
    )

    /** The bridge: plan §6.1 roles plus the controller's ruling for every other role. */
    private fun expectedRoles(c: DictColors): Map<String, Color> = mapOf(
        "primary" to c.accent, "onPrimary" to c.onAccent, "primaryContainer" to c.accentContainer,
        "onPrimaryContainer" to c.onAccentContainer, "inversePrimary" to c.inverseAccent,
        "secondary" to c.muted, "onSecondary" to c.background, "secondaryContainer" to c.surface2,
        "onSecondaryContainer" to c.ink,
        "tertiary" to c.accent, "onTertiary" to c.onAccent, "tertiaryContainer" to c.accentContainer,
        "onTertiaryContainer" to c.onAccentContainer,
        "background" to c.background, "onBackground" to c.ink, "surface" to c.background, "onSurface" to c.ink,
        "surfaceVariant" to c.surface1, "onSurfaceVariant" to c.muted, "surfaceTint" to c.background,
        "inverseSurface" to c.inverseSurface, "inverseOnSurface" to c.inverseOnSurface,
        "error" to c.danger, "onError" to c.onAccent, "errorContainer" to c.accentContainer,
        "onErrorContainer" to c.danger,
        "outline" to c.outline, "outlineVariant" to c.divider, "scrim" to c.scrim,
        "surfaceBright" to if (c.isDark) c.surface2 else c.background,
        "surfaceDim" to if (c.isDark) c.background else c.surface2,
        "surfaceContainerLowest" to c.background, "surfaceContainerLow" to c.surface1,
        "surfaceContainer" to c.surface1, "surfaceContainerHigh" to c.surface2,
        "surfaceContainerHighest" to c.surface2,
        "primaryFixed" to c.accentContainer, "primaryFixedDim" to c.accentContainer,
        "onPrimaryFixed" to c.onAccentContainer, "onPrimaryFixedVariant" to c.onAccentContainer,
        "secondaryFixed" to c.surface1, "secondaryFixedDim" to c.surface2,
        "onSecondaryFixed" to c.ink, "onSecondaryFixedVariant" to c.muted,
        "tertiaryFixed" to c.accentContainer, "tertiaryFixedDim" to c.accentContainer,
        "onTertiaryFixed" to c.onAccentContainer, "onTertiaryFixedVariant" to c.onAccentContainer,
    )

    private companion object {
        /** Light `onAccent` is #FFFFFF, which is also the M3 light baseline for these three "on" roles. */
        val TOKEN_EQUALS_BASELINE = setOf("light" to "onPrimary", "light" to "onTertiary", "light" to "onError")
    }
}
