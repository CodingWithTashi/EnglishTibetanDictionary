package com.kharagedition.tibetandictionary.ui.entry

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.startActivitySafely
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.ExtremeScale
import com.kharagedition.tibetandictionary.ui.components.ExtremeScalePreview
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples
import com.kharagedition.tibetandictionary.ui.components.SeeAlsoSection
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * One entry (1h/1l). [onOpenEntry] pushes a see-also entry, [onOpenSearch] searches for a headword that has no
 * entry id, [onBack] pops the route.
 */
@Composable
fun EntryScreen(entryId: Long, onBack: () -> Unit, onOpenEntry: (Long) -> Unit, onOpenSearch: (String) -> Unit) {
    val viewModel = hiltViewModel<EntryViewModel, EntryViewModel.Factory>(
        creationCallback = { factory -> factory.create(entryId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EntryEffects(viewModel.effects, onBack, onOpenEntry, onOpenSearch)
    EntryContent(state, viewModel::onEvent)
}

/** Performs [effects]: navigation, toasts (copy resolved in the app language), clipboard, share and e-mail. */
@Composable
private fun EntryEffects(
    effects: Flow<EntryEffect>,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenSearch: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    // Toasts outlive the collector's turn (they suspend until closed) but not the screen.
    val toastScope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    val openEntry by rememberUpdatedState(onOpenEntry)
    val openSearch by rememberUpdatedState(onOpenSearch)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                EntryEffect.NavigateBack -> back()
                is EntryEffect.NavigateEntry -> openEntry(effect.entryId)
                is EntryEffect.NavigateSearch -> openSearch(effect.query)
                is EntryEffect.Toast -> toastScope.launch {
                    toaster.show(
                        message = effect.message.resolve(resources),
                        action = effect.action?.let(resources::getString),
                        onAction = effect.onAction?.let { action -> suspend { action() } },
                    )
                }
                is EntryEffect.CopyToClipboard -> {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(effect.label, effect.text)))
                    // Android 13+ confirms copies itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        toastScope.launch { toaster.show(resources.getString(R.string.copied)) }
                    }
                }
                is EntryEffect.ShareText -> context.share(effect.text)
                is EntryEffect.OpenEmail -> context.email(effect)
            }
        }
    }
}

private fun Context.share(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivitySafely(Intent.createChooser(send, null))
}

private fun Context.email(email: EntryEffect.OpenEmail) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:${email.address}".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(email.address))
        .putExtra(Intent.EXTRA_SUBJECT, email.subject)
        .putExtra(Intent.EXTRA_TEXT, email.body)
    startActivitySafely(intent)
}

/**
 * The stateless entry screen: toolbar, then the entry (header, sense cards, see also, correction footer), the
 * missing notice or the loading skeleton; the folder picker sheet while [EntryUiState.folderPicker] is set.
 *
 * Test tags: `entry_screen`, `entry_back`, `entry_save`, `entry_audio`, `entry_sense_{index}`, `entry_related_{id}`
 * (`entry_related_search_{index}` for links without an id), `entry_report`, `entry_loading`, `entry_missing_back`.
 */
@Composable
fun EntryContent(state: EntryUiState, onEvent: (EntryEvent) -> Unit, modifier: Modifier = Modifier) {
    val entry = state.entry
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .testTag(SCREEN_TAG),
    ) {
        EntryToolbar(entryShown = entry != null, isSaved = state.isSaved, onEvent = onEvent)
        when {
            entry != null -> EntryBody(entry, onEvent, Modifier.weight(1f))
            state.missing -> EntryMissing({ onEvent(EntryEvent.Back) }, Modifier.weight(1f))
            else -> EntryLoading(Modifier.padding(top = BodyTop))
        }
    }
    state.folderPicker?.let { EntryFolderPicker(it, onEvent) }
}

@Composable
private fun EntryBody(entry: EntryUi, onEvent: (EntryEvent) -> Unit, modifier: Modifier = Modifier) {
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = dimens.screenPadding, top = BodyTop, end = dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        EntryHeader(entry) { onEvent(EntryEvent.PlayAudio) }
        if (entry.senses.isNotEmpty() || entry.isTruncated) EntrySenses(entry)
        if (entry.related.isNotEmpty()) {
            SeeAlsoSection(entry.related, onClick = { onEvent(EntryEvent.OpenRelated(it)) }, rowTag = ::relatedTag)
        }
        Column {
            ReportCorrectionFooter { onEvent(EntryEvent.ReportCorrection) }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Preview content built from the component samples. */
private object EntrySamples {
    val populated = EntryUi(
        id = 1_004_211,
        direction = PreviewSamples.boEn,
        headword = "བཀྲ་ཤིས།",
        headwordIsTibetan = true,
        wylie = "bkra shis",
        phonetic = "tashi",
        partsOfSpeech = listOf("adj.", "n."),
        label = null,
        senses = PreviewSamples.senses,
        related = PreviewSamples.related,
        hasAudio = true,
        audioFile = "bkra_shis.ogg",
        isTruncated = true,
    )

    val minimal = EntryUi(
        id = 3_008_120,
        direction = PreviewSamples.enBo,
        headword = "compassion",
        headwordIsTibetan = false,
        wylie = null,
        phonetic = null,
        partsOfSpeech = emptyList(),
        label = null,
        senses = listOf(SenseUi(null, null, "སྙིང་རྗེ། ཐུགས་རྗེ།")),
        related = emptyList(),
        hasAudio = false,
        audioFile = null,
        isTruncated = false,
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun EntryPopulatedPreview() {
    ComponentPreview(edgeToEdge = true) {
        EntryContent(EntryUiState(loading = false, entry = EntrySamples.populated, isSaved = true), onEvent = {})
    }
}

@PreviewLightDark
@Composable
private fun EntryMinimalPreview() {
    ComponentPreview(edgeToEdge = true) {
        EntryContent(EntryUiState(loading = false, entry = EntrySamples.minimal), onEvent = {})
    }
}

@PreviewLightDark
@Composable
private fun EntryLoadingPreview() {
    ComponentPreview(edgeToEdge = true) { EntryContent(EntryUiState(), onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun EntryMissingPreview() {
    ComponentPreview(edgeToEdge = true) {
        EntryContent(EntryUiState(loading = false, missing = true), onEvent = {})
    }
}

@ExtremeScalePreview
@Composable
private fun EntryExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        EntryContent(EntryUiState(loading = false, entry = EntrySamples.populated), onEvent = {})
    }
}

@ExtremeScalePreview
@Composable
private fun EntryMissingExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        EntryContent(EntryUiState(loading = false, missing = true), onEvent = {})
    }
}
