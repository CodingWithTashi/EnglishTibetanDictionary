package com.kharagedition.tibetandictionary.ui.paywall

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.ProSheet
import com.kharagedition.tibetandictionary.ui.model.UiText
import kotlinx.coroutines.launch

/**
 * A screen's Pro sheet: open while [source] is set, in [mode]. The screen keeps this in its composition while closed,
 * so a toast it shows ("Welcome to Pro") outlives the sheet. Each opening starts the sheet over; nothing is drawn
 * until it has, so a closed sheet's last state never flashes. A recreated activity reopens the same sheet as it was,
 * so a purchase in Play's sheet keeps its result.
 */
@Composable
fun PaywallSheet(source: PromptSource?, mode: PaywallMode, onDismiss: () -> Unit) {
    val viewModel = hiltViewModel<PaywallViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val toastScope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val currentSource by rememberUpdatedState(source)
    var opened by remember { mutableStateOf(false) }

    LaunchedEffect(source, mode) {
        opened = false
        if (source != null) {
            viewModel.open(source, mode)
            opened = true
        } else {
            viewModel.close()
        }
    }
    LaunchedEffect(viewModel) {
        fun toast(text: UiText) {
            toastScope.launch { toaster.show(text.resolve(resources)) }
        }
        viewModel.effects.collect { effect ->
            when (effect) {
                is PaywallEffect.Close -> {
                    // A sync can end after the user closed the sheet; the screen may show another sheet by then.
                    if (currentSource != null) dismiss()
                    effect.toast?.let(::toast)
                }
                is PaywallEffect.Toast -> toast(effect.text)
            }
        }
    }

    if (source != null && opened) {
        ProSheet(
            state,
            onEvent = viewModel::onEvent,
            onContinue = { activity?.let(viewModel::purchase) },
            onSignIn = { activity?.let(viewModel::signIn) },
            onDismiss = onDismiss,
        )
    }
}
