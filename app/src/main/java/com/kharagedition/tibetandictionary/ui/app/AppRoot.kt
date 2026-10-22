package com.kharagedition.tibetandictionary.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.rememberNavController
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.nameRes
import com.kharagedition.tibetandictionary.navigation.AppNavHost
import com.kharagedition.tibetandictionary.navigation.Main
import com.kharagedition.tibetandictionary.navigation.Onboarding
import com.kharagedition.tibetandictionary.ui.components.AppToastHost
import com.kharagedition.tibetandictionary.ui.components.LocalToastAnchors
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.ToastAnchors
import com.kharagedition.tibetandictionary.ui.components.Toaster
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.SystemBarIcons

/** Test tag of the running app's root. */
const val APP_ROOT_TAG = "app_root"

/** Whether [this] appearance draws the dark palette, following the system for [Appearance.SYSTEM]. */
@Composable
private fun Appearance.isDark(): Boolean = when (this) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

/**
 * The running app: the user's theme settings, the toast host and the nav graph. A dictionary switch in progress or
 * failed ([AppRootState.Ready.switching]) is drawn over the still-mounted graph by [SwitchingOverlay].
 */
@Composable
fun AppRoot(state: AppRootState.Ready, onRetrySwitch: () -> Unit, onKeepCurrent: () -> Unit) {
    val isDark = state.appearance.isDark()
    // The language comes from the composition's configuration (DictTheme's default), so typography always
    // matches the resources on screen, including right after a per-app or system locale change.
    DictTheme(darkTheme = isDark, textScale = state.textSize.scale, lineSpacing = state.lineSpacing) {
        val hostState = remember { SnackbarHostState() }
        val accessibility = LocalAccessibilityManager.current
        val toaster = remember(hostState, accessibility) { Toaster(hostState, accessibility) }
        // Fixed at first composition: finishing onboarding must not rebuild the graph.
        val startDestination: Any = remember { if (state.onboarded) Main else Onboarding }
        val navController = rememberNavController()
        SystemBarIcons(darkBackground = isDark)
        var rootHeight by remember { mutableIntStateOf(0) }
        CompositionLocalProvider(
            LocalToaster provides toaster,
            LocalToastAnchors provides remember { ToastAnchors() },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootHeight = it.height }
                    .background(DictTheme.colors.background)
                    .testTag(APP_ROOT_TAG)
                    // Exposes testTag values as resource ids so UI-automation flows can select by id.
                    .semantics { testTagsAsResourceId = true },
            ) {
                AppNavHost(
                    startDestination = startDestination,
                    navController = navController,
                    activePack = state.pack,
                    onboarded = state.onboarded,
                    holdLinks = state.switching != SwitchState.Idle,
                )
                AppToastHost(hostState, rootHeight, Modifier.align(Alignment.BottomCenter))
                if (state.switching != SwitchState.Idle) SwitchingOverlay(state.switching, onRetrySwitch, onKeepCurrent)
            }
        }
    }
}

/** Test tag of the switching overlay. */
const val SWITCHING_OVERLAY_TAG = "switching_overlay"

/**
 * The installing content over the whole app while a dictionary switch runs or has failed. It swallows every pointer
 * event and the back press, so nothing below reacts; on a failure "Try again" calls [onRetry] and "Keep current
 * dictionary" calls [onKeepCurrent]. Callers show it only while [switching] is not [SwitchState.Idle].
 */
@Composable
fun SwitchingOverlay(switching: SwitchState, onRetry: () -> Unit, onKeepCurrent: () -> Unit) {
    // Composed after the nav host, so this handler is the one the back dispatcher calls.
    BackHandler(enabled = true) {}
    val pack = when (switching) {
        is SwitchState.InProgress -> switching.pack
        is SwitchState.Failed -> switching.pack
        SwitchState.Idle -> return
    }
    val failed = switching as? SwitchState.Failed
    InstallingContent(
        title = stringResource(R.string.switching_dictionary, stringResource(pack.nameRes())),
        progress = (switching as? SwitchState.InProgress)?.progress,
        totalEntries = null,
        failure = failed?.cause,
        onRetry = onRetry,
        secondaryActionLabel = failed?.let { stringResource(R.string.keep_current_dictionary) },
        onSecondaryAction = onKeepCurrent,
        modifier = Modifier
            .testTag(SWITCHING_OVERLAY_TAG)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
    )
}
