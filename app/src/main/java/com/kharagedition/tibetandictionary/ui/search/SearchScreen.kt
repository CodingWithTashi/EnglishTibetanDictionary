package com.kharagedition.tibetandictionary.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.paywall.PaywallMode
import com.kharagedition.tibetandictionary.ui.paywall.PaywallSheet
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Focused search (1g/3b/1k): starts with [initialQuery], and with voice input when [voice] is set; [onBack] pops
 * the route, [onOpenEntry] opens a result, [onOpenTranslate] hands a phrase to the Translate tab.
 */
@Composable
fun SearchScreen(
    initialQuery: String,
    voice: Boolean,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenTranslate: (TranslateRequest) -> Unit,
) {
    val viewModel = hiltViewModel<SearchViewModel, SearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(initialQuery, voice) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        spokenText(result)?.let { text ->
            viewModel.onEvent(SearchEvent.QueryChanged(TextFieldValue(text, TextRange(text.length))))
        }
    }
    SearchEffects(
        effects = viewModel.effects,
        onBack = onBack,
        onOpenEntry = onOpenEntry,
        onOpenTranslate = onOpenTranslate,
        focusRequester = focusRequester,
        startVoice = {
            try {
                speechInput.launch(speechIntent())
            } catch (_: ActivityNotFoundException) {
                // A recognition service without the recogniser activity: fall back to typing.
                focusRequester.requestFocus()
            }
        },
    )
    val activity = LocalActivity.current
    SearchContent(
        state,
        onEvent = { event ->
            if (event == SearchEvent.SmartSignIn) activity?.let(viewModel::signIn) else viewModel.onEvent(event)
        },
        focusRequester = focusRequester,
    )
    PaywallSheet(
        PromptSource.SEARCH_LIMIT.takeIf { state.showProSheet },
        PaywallMode.PURCHASE,
        onDismiss = { viewModel.onEvent(SearchEvent.DismissProSheet) },
    )
}

private fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

/** The recogniser's first non-blank result, or null when it was cancelled or heard nothing. */
private fun spokenText(result: ActivityResult): String? =
    result.data
        ?.takeIf { result.resultCode == Activity.RESULT_OK }
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

/** Performs [effects]: navigation, voice input, focusing the field and toasts. */
@Composable
private fun SearchEffects(
    effects: Flow<SearchEffect>,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenTranslate: (TranslateRequest) -> Unit,
    focusRequester: FocusRequester,
    startVoice: () -> Unit,
) {
    val back by rememberUpdatedState(onBack)
    val openEntry by rememberUpdatedState(onOpenEntry)
    val openTranslate by rememberUpdatedState(onOpenTranslate)
    val voice by rememberUpdatedState(startVoice)
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    // Toasts suspend until closed, so they run beside the collector.
    val toastScope = rememberCoroutineScope()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                SearchEffect.NavigateBack -> back()
                is SearchEffect.NavigateEntry -> openEntry(effect.entryId)
                SearchEffect.StartVoiceSearch -> voice()
                SearchEffect.FocusField -> focusRequester.requestFocus()
                is SearchEffect.OpenTranslate -> openTranslate(effect.request)
                is SearchEffect.Toast -> toastScope.launch { toaster.show(effect.message.resolve(resources)) }
            }
        }
    }
}
