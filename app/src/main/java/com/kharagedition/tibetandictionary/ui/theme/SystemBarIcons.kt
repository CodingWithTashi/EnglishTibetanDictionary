package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kharagedition.tibetandictionary.core.findActivity

/**
 * Sets light/dark status and navigation bar icons for [darkBackground] and restores the previous
 * values on dispose. No-op in previews.
 */
@Composable
fun SystemBarIcons(darkBackground: Boolean) {
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    DisposableEffect(view, darkBackground) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val prevStatus = controller.isAppearanceLightStatusBars
            val prevNav = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = !darkBackground
            controller.isAppearanceLightNavigationBars = !darkBackground
            onDispose {
                controller.isAppearanceLightStatusBars = prevStatus
                controller.isAppearanceLightNavigationBars = prevNav
            }
        }
    }
}
