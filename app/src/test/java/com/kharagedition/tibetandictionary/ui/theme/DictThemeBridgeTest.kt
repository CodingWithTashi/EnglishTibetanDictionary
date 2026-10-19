package com.kharagedition.tibetandictionary.ui.theme

import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import org.junit.Test

class DictThemeBridgeTest {

    @Test
    fun `light and dark schemes map every bridged Material 3 role to its token`() {
        for (colors in listOf(DictColors.Light, DictColors.Dark)) {
            val scheme = colors.materialColorScheme()
            val expected = mapOf(
                "primary" to (scheme.primary to colors.accent),
                "onPrimary" to (scheme.onPrimary to colors.onAccent),
                "primaryContainer" to (scheme.primaryContainer to colors.accentContainer),
                "onPrimaryContainer" to (scheme.onPrimaryContainer to colors.onAccentContainer),
                "secondary" to (scheme.secondary to colors.muted),
                "background" to (scheme.background to colors.background),
                "onBackground" to (scheme.onBackground to colors.ink),
                "surface" to (scheme.surface to colors.background),
                "onSurface" to (scheme.onSurface to colors.ink),
                "surfaceVariant" to (scheme.surfaceVariant to colors.surface1),
                "onSurfaceVariant" to (scheme.onSurfaceVariant to colors.muted),
                "surfaceContainerLow" to (scheme.surfaceContainerLow to colors.surface1),
                "surfaceContainer" to (scheme.surfaceContainer to colors.surface1),
                "surfaceContainerHigh" to (scheme.surfaceContainerHigh to colors.surface2),
                "surfaceContainerHighest" to (scheme.surfaceContainerHighest to colors.surface2),
                "outline" to (scheme.outline to colors.outline),
                "outlineVariant" to (scheme.outlineVariant to colors.divider),
                "error" to (scheme.error to colors.danger),
                "inverseSurface" to (scheme.inverseSurface to colors.inverseSurface),
                "inverseOnSurface" to (scheme.inverseOnSurface to colors.inverseOnSurface),
                "inversePrimary" to (scheme.inversePrimary to colors.inverseAccent),
            )
            val palette = if (colors.isDark) "dark" else "light"
            for ((role, pair) in expected) {
                assertWithMessage("$palette $role").that(pair.first).isEqualTo(pair.second)
            }
        }
    }

    @Test
    fun `dimens grow settings rows for Tibetan UI only`() {
        assertWithMessage("en").that(DictDimens.forLanguage(AppLanguage.EN).settingsRow.value).isEqualTo(52f)
        assertWithMessage("bo").that(DictDimens.forLanguage(AppLanguage.BO).settingsRow.value).isEqualTo(56f)
        assertWithMessage("other rows").that(DictDimens.Bo.historyRow).isEqualTo(DictDimens.En.historyRow)
    }
}
