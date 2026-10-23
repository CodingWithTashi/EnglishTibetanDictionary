package com.kharagedition.tibetandictionary.ui.translate

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.startActivitySafely
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.ProSheet
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val ScreenTop = 8.dp

/**
 * The Translate tab (1b, 1s–1v): [onOpenSearch] opens Search for a tapped word or "Look up words". Each time the tab
 * is shown it asks the repository to prepare (the guest, a fresh status).
 */
@Composable
fun TranslateScreen(onOpenSearch: (String) -> Unit, viewModel: TranslateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onEvent(TranslateEvent.Shown) }
    TranslateEffects(viewModel.effects, onOpenSearch)
    val activity = LocalActivity.current
    TranslateContent(
        state,
        viewModel::onEvent,
        onSignIn = { source -> activity?.let { viewModel.signIn(it, source) } },
    )
}

/** Performs [effects]: the clipboard, the share sheet, Search and toasts (copy resolved in the app language). */
@Composable
private fun TranslateEffects(effects: Flow<TranslateEffect>, onOpenSearch: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    // Toasts outlive the collector's turn (they suspend until closed) but not the screen.
    val toastScope = rememberCoroutineScope()
    val openSearch by rememberUpdatedState(onOpenSearch)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is TranslateEffect.Copy -> {
                    val clip = ClipData.newPlainText(resources.getString(R.string.translate_title), effect.text)
                    clipboard.setClipEntry(ClipEntry(clip))
                    // Android 13+ confirms copies itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        toastScope.launch { toaster.show(resources.getString(R.string.copied)) }
                    }
                }
                is TranslateEffect.Share -> context.shareText(effect.text)
                is TranslateEffect.OpenSearch -> openSearch(effect.query)
                is TranslateEffect.Toast -> toastScope.launch { toaster.show(effect.message.resolve(resources)) }
            }
        }
    }
}

private fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivitySafely(Intent.createChooser(send, null))
}

/**
 * The stateless Translate tab, scrolling as a whole: the title and usage pill, the language bar, the input card
 * and its hint, Translate (or the limit card in its place), a failure card, the skeleton or the result, the "Try"
 * samples while the input is empty, and the recents; plus the Today and Pro sheets. [onSignIn] runs Google
 * sign-in from the guest card or the Today sheet.
 *
 * Test tags are listed on each part; the screen is `translate_screen`.
 */
@Composable
fun TranslateContent(
    state: TranslateUiState,
    onEvent: (TranslateEvent) -> Unit,
    onSignIn: (PromptSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .testTag("translate_screen")
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding)
            .padding(top = ScreenTop, bottom = dimens.sectionGap),
        verticalArrangement = Arrangement.spacedBy(dimens.blockGap),
    ) {
        TranslateHeader(state.pill, state.plan, onOpenToday = { onEvent(TranslateEvent.OpenToday) })
        state.pair?.let { pair ->
            LanguageBar(pair, state.canSwap, onSwap = { onEvent(TranslateEvent.Swap) })
            InputCard(state, pair.source, onEvent)
        }
        state.hint?.let { hint -> HintRow(hint, onSwitch = { onEvent(TranslateEvent.SwitchDirection) }) }
        val block = state.block
        if (block != null && block.replacesButton) {
            TranslateBlockCard(block, onEvent, onSignIn)
        } else {
            TranslateButton(state, onTranslate = { onEvent(TranslateEvent.Translate) })
            if (state.showProHint) ProHint(onClick = { onEvent(TranslateEvent.OpenPro(PromptSource.TOO_LONG)) })
            if (block != null) TranslateBlockCard(block, onEvent, onSignIn)
        }
        val result = state.result
        val target = state.pair?.target
        when {
            state.phase == TranslatePhase.LOADING -> TranslateSkeleton()
            result != null && target != null -> ResultCard(result, target, onEvent)
        }
        if (state.input.text.isEmpty() && state.samples.isNotEmpty()) {
            SampleChips(state.samples, onTap = { onEvent(TranslateEvent.SampleTap(it)) })
        }
        if (state.recents.isNotEmpty()) {
            RecentList(
                state.recents,
                onTap = { onEvent(TranslateEvent.RecentTap(it)) },
                onClear = { onEvent(TranslateEvent.ClearRecents) },
            )
        }
    }
    TranslateSheets(state, onEvent, onSignIn)
}

@Composable
private fun TranslateSheets(
    state: TranslateUiState,
    onEvent: (TranslateEvent) -> Unit,
    onSignIn: (PromptSource) -> Unit,
) {
    val dismiss = { onEvent(TranslateEvent.DismissSheet) }
    when (state.sheet) {
        TranslateSheet.TODAY -> state.pill?.let { usage ->
            TodaySheet(
                usage,
                state.plan,
                state.freeLimit,
                onSignIn = {
                    onSignIn(PromptSource.TODAY_SHEET)
                    dismiss()
                },
                onUpgrade = { onEvent(TranslateEvent.OpenPro(PromptSource.TODAY_SHEET)) },
                onDismiss = dismiss,
            )
        }
        TranslateSheet.PRO -> ProSheet(onDismiss = dismiss)
        null -> Unit
    }
}

/** Fixed states for previews. */
internal object TranslateSamples {
    private val boEn = LangPair(Lang.BO, Lang.EN)
    private const val SOURCE = "ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།"
    private const val OUTPUT = "How are you?"

    val idle = TranslateUiState(
        pair = boEn,
        canSwap = true,
        samples = listOf(SOURCE, "ང་བོད་པ་ཡིན།"),
        pill = UsagePillUi(remaining = 7, limit = 10, low = false),
        plan = Plan.FREE,
        freeLimit = 10,
        recents = listOf(
            RecentTranslationUi(1, "བཀའ་དྲིན་ཆེ།", "Thank you.", boEn),
            RecentTranslationUi(2, "See you tomorrow", "སང་ཉིན་མཇལ་ཡོང་།", boEn.swapped()),
        ),
    )

    val done = idle.copy(
        input = TextFieldValue(SOURCE, TextRange(SOURCE.length)),
        length = SOURCE.length,
        canTranslate = true,
        phase = TranslatePhase.DONE,
        result = TranslationResultUi(
            OUTPUT,
            TranslationText.words(OUTPUT, Lang.EN),
            cached = true,
            provider = Provider.GOOGLE,
            saved = true,
        ),
    )

    val guestLimit = idle.copy(
        input = TextFieldValue("ང་བོད་པ་ཡིན།"),
        length = 12,
        pill = UsagePillUi(remaining = 0, limit = 3, low = true),
        plan = Plan.GUEST,
        block = TranslateBlock.GuestLimit(used = 3, more = 7),
    )

    val offline = guestLimit.copy(
        pill = idle.pill,
        plan = Plan.FREE,
        canTranslate = true,
        block = TranslateBlock.Offline,
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun TranslateIdlePreview() {
    ComponentPreview(edgeToEdge = true) { TranslateContent(TranslateSamples.idle, onEvent = {}, onSignIn = {}) }
}

@PreviewLightDark
@Composable
private fun TranslateDonePreview() {
    ComponentPreview(edgeToEdge = true) { TranslateContent(TranslateSamples.done, onEvent = {}, onSignIn = {}) }
}

@PreviewLightDark
@Composable
private fun TranslateGuestLimitPreview() {
    ComponentPreview(edgeToEdge = true) { TranslateContent(TranslateSamples.guestLimit, onEvent = {}, onSignIn = {}) }
}

@PreviewLightDark
@Composable
private fun TranslateOfflinePreview() {
    ComponentPreview(edgeToEdge = true) { TranslateContent(TranslateSamples.offline, onEvent = {}, onSignIn = {}) }
}
