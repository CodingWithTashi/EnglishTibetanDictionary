package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.domain.SavedPeriod
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.ExtremeScale
import com.kharagedition.tibetandictionary.ui.components.ExtremeScalePreview
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The Saved tab (1i/3c): [onOpenEntry] opens a saved entry. */
@Composable
fun SavedScreen(onOpenEntry: (Long) -> Unit) {
    val viewModel = hiltViewModel<SavedViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SavedEffects(viewModel.effects, viewModel::onEvent, onOpenEntry)
    SavedContent(state, viewModel::onEvent)
}

/** Performs [effects]: navigation and toasts (copy resolved in the app language); toast actions send events. */
@Composable
internal fun SavedEffects(effects: Flow<SavedEffect>, onEvent: (SavedEvent) -> Unit, onOpenEntry: (Long) -> Unit) {
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    // Toasts outlive the collector's turn (they suspend until closed) but not the screen.
    val toastScope = rememberCoroutineScope()
    val send by rememberUpdatedState(onEvent)
    val openEntry by rememberUpdatedState(onOpenEntry)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SavedEffect.NavigateEntry -> openEntry(effect.entryId)
                is SavedEffect.Removed -> toastScope.launch {
                    toaster.show(
                        message = resources.getString(R.string.removed),
                        action = resources.getString(R.string.undo),
                        onAction = { send(SavedEvent.UndoUnsave(effect.entryId, effect.folderId)) },
                    )
                }
                is SavedEffect.Toast -> toastScope.launch {
                    toaster.show(
                        message = effect.message.resolve(resources),
                        action = effect.action?.let(resources::getString),
                        onAction = effect.actionEvent?.let { event -> suspend { send(event) } },
                    )
                }
            }
        }
    }
}

/**
 * The stateless Saved tab: title (with the word count), the search-within field, folder chips, then the list in
 * sections, the inline "No matches", or the empty state; the folder picker and new-folder sheets while open.
 *
 * Test tags: `saved_screen`, `saved_search`, `folder_chip_all`, `folder_chip_{id}`, `saved_add_folder`,
 * `saved_group_{today|week|month|earlier}`, `saved_row_{id}`, `saved_no_matches`, `saved_empty_cta`,
 * `saved_folder_name`.
 */
@Composable
fun SavedContent(state: SavedUiState, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .testTag(SCREEN_TAG),
    ) {
        SavedHeader(state, onEvent)
        when {
            state.loading -> Unit
            state.isEmpty -> SavedEmpty(
                wotd = state.wotd?.takeIf { state.showSaveWotd },
                onSaveWotd = { onEvent(SavedEvent.SaveWotd) },
                modifier = Modifier.weight(1f),
            )
            else -> {
                FolderChips(state, onEvent, Modifier.padding(top = ChipRowTop))
                if (state.noMatches) {
                    val padding = DictTheme.dimens.screenPadding
                    SavedNoMatches(
                        Modifier
                            .weight(1f)
                            .padding(start = padding, top = ListTop + DictTheme.dimens.blockGap, end = padding),
                    )
                } else {
                    SavedList(state.groups, onEvent, Modifier.weight(1f))
                }
            }
        }
    }
    state.folderPicker?.let { SavedFolderPicker(it, onEvent) }
    state.newFolder?.let { NewFolderSheet(it, onEvent) }
}

/** The sections, each a header over its rows; every row but the very last has a divider. */
@Composable
private fun SavedList(groups: List<SavedGroupUi>, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(top = ListTop)) {
        groups.forEachIndexed { groupIndex, group ->
            item(key = group.period) { GroupHeader(group.period, first = groupIndex == 0) }
            val lastGroup = groupIndex == groups.lastIndex
            itemsIndexed(group.items, key = { _, item -> item.entryId }) { index, item ->
                SavedRow(
                    item,
                    onOpen = { onEvent(SavedEvent.OpenEntry(item.entryId)) },
                    onUnsave = { onEvent(SavedEvent.Unsave(item.entryId)) },
                    showDivider = !(lastGroup && index == group.items.lastIndex),
                )
            }
        }
    }
}

/** Preview content built from the component samples (1i). */
private object SavedSamples {
    private fun item(id: Long, headword: String, secondary: String, tibetan: Boolean = true) =
        SavedItemUi(id, headword, tibetan, AnnotatedString(secondary), folderId = null)

    val populated = SavedUiState(
        loading = false,
        totalCount = 42,
        folders = PreviewSamples.folders,
        groups = listOf(
            SavedGroupUi(
                SavedPeriod.TODAY,
                listOf(
                    item(1, "བཀྲ་ཤིས།", "bkra shis · auspicious, good fortune"),
                    item(2, "compassion", "སྙིང་རྗེ། snying rje · ཐུགས་རྗེ། (hon.)", tibetan = false),
                ),
            ),
            SavedGroupUi(
                SavedPeriod.THIS_WEEK,
                listOf(
                    item(3, "ཤེས་རབ།", "shes rab · wisdom, discriminating awareness"),
                    item(4, "བྱམས་པ།", "byams pa · loving-kindness; Maitreya"),
                    item(5, "བདེ་བ།", "bde ba · happiness, bliss, well-being"),
                ),
            ),
        ),
    )

    val empty = SavedUiState(loading = false, wotd = PreviewSamples.wotd)
}

@PreviewLightDark
@BoPreview
@Composable
private fun SavedPopulatedPreview() {
    ComponentPreview(edgeToEdge = true) { SavedContent(SavedSamples.populated, onEvent = {}) }
}

@PreviewLightDark
@BoPreview
@Composable
private fun SavedEmptyPreview() {
    ComponentPreview(edgeToEdge = true) { SavedContent(SavedSamples.empty, onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun SavedNoMatchesPreview() {
    ComponentPreview(edgeToEdge = true) {
        SavedContent(SavedSamples.populated.copy(query = "zzz", groups = emptyList()), onEvent = {})
    }
}

@ExtremeScalePreview
@Composable
private fun SavedExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) { SavedContent(SavedSamples.populated, onEvent = {}) }
}

@ExtremeScalePreview
@Composable
private fun SavedEmptyExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) { SavedContent(SavedSamples.empty, onEvent = {}) }
}
