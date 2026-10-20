package com.kharagedition.tibetandictionary.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.domain.InputScript
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DirectionSwitcher
import com.kharagedition.tibetandictionary.ui.components.EntryRow
import com.kharagedition.tibetandictionary.ui.components.FullEmptyState
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.OutlinedButton
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples
import com.kharagedition.tibetandictionary.ui.components.SearchField
import com.kharagedition.tibetandictionary.ui.components.SearchHeader
import com.kharagedition.tibetandictionary.ui.components.SkeletonEntryRow
import com.kharagedition.tibetandictionary.ui.components.SkeletonPreset
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.components.TipBox
import com.kharagedition.tibetandictionary.ui.components.WylieOverlap
import com.kharagedition.tibetandictionary.ui.components.bottomDivider
import com.kharagedition.tibetandictionary.ui.components.directionLabelText
import com.kharagedition.tibetandictionary.ui.components.pullUp
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.flow.Flow

/** Suggestions body: 4dp under the header (3b). */
private val SuggestionsTop = 4.dp

/** Clock icon of a recent row, and the gap between the row's parts. */
private val RecentIcon = 18.dp
private val RecentGap = 12.dp

/** Space between the last recent row and the Wylie tip. */
private val TipTop = 16.dp

/** "N results" sits 8dp above the first row. */
private val CountBottom = 8.dp

/** No-results body (1k): 56 under the header; the did-you-mean card 16 under the hint, 32 from the sides. */
private val NoResultsTop = 56.dp
private val SuggestionCardTop = 16.dp
private val SuggestionCardSide = 32.dp

/** Chevron at the end of a did-you-mean row. */
private val SuggestionChevron = 20.dp

private const val SCREEN_TAG = "search_screen"
private const val LOAD_MORE_KEY = "load_more"

/**
 * Focused search (1g/3b/1k): starts with [initialQuery], and with voice input when [voice] is set; [onBack] pops
 * the route, [onOpenEntry] opens a result.
 */
@Composable
fun SearchScreen(initialQuery: String, voice: Boolean, onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
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
    SearchContent(state, viewModel::onEvent, focusRequester = focusRequester)
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

/** Performs [effects]: navigation, voice input and focusing the field. */
@Composable
private fun SearchEffects(
    effects: Flow<SearchEffect>,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    focusRequester: FocusRequester,
    startVoice: () -> Unit,
) {
    val back by rememberUpdatedState(onBack)
    val openEntry by rememberUpdatedState(onOpenEntry)
    val voice by rememberUpdatedState(startVoice)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                SearchEffect.NavigateBack -> back()
                is SearchEffect.NavigateEntry -> openEntry(effect.entryId)
                SearchEffect.StartVoiceSearch -> voice()
                SearchEffect.FocusField -> focusRequester.requestFocus()
            }
        }
    }
}

/**
 * The stateless Search route: the header (back, field, direction switcher), then by [SearchUiState.phase] the
 * recent searches and Wylie tip, the loading skeleton, the results, or the no-results state.
 *
 * Test tags: `search_screen`, `search_field`, `search_mic`, `direction_segment_{id}`, `search_recent_{id}`,
 * `search_clear_recents`, `search_tip`, `search_loading`, `search_results_count`, `result_row_{id}`,
 * `suggestion_row_{id}`, `search_try_alternate`.
 */
@Composable
fun SearchContent(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .testTag(SCREEN_TAG),
    ) {
        SearchHeader(
            onBack = { onEvent(SearchEvent.Back) },
            field = {
                SearchField(
                    value = state.query,
                    onValueChange = { onEvent(SearchEvent.QueryChanged(it)) },
                    placeholder = stringResource(R.string.search_placeholder),
                    modifier = Modifier.weight(1f),
                    showMic = state.micAvailable,
                    onMic = { onEvent(SearchEvent.VoiceSearch) },
                    onClear = { onEvent(SearchEvent.Clear) },
                    focusRequester = focusRequester,
                    onSearch = { keyboard?.hide() },
                )
            },
            switcher = state.selectedDirection?.let { selected ->
                {
                    DirectionSwitcher(
                        slots = state.slots,
                        selected = selected,
                        onSelect = { onEvent(SearchEvent.SelectDirection(it.id)) },
                        autoSelected = state.autoSelected,
                    )
                }
            },
        )
        val body = Modifier.weight(1f)
        when (state.phase) {
            SearchPhase.Suggestions -> SuggestionsBody(state.recents, state.showTip, onEvent, body)
            SearchPhase.Loading -> LoadingBody(body)
            SearchPhase.Results -> ResultsBody(state, onEvent, body)
            SearchPhase.NoResults -> NoResultsBody(state, onEvent, body)
        }
    }
}

/** Recent searches under a "Recent · Clear" line, then the Wylie tip (3b). */
@Composable
private fun SuggestionsBody(
    recents: List<RecentRowUi>,
    showTip: Boolean,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = dimens.screenPadding, top = SuggestionsTop, end = dimens.screenPadding),
    ) {
        if (recents.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MixedText(
                    stringResource(R.string.recent),
                    DictTheme.type.sectionLabel,
                    DictTheme.type.tibetanInline14,
                    Modifier.weight(1f),
                    color = DictTheme.colors.muted,
                )
                TextButton(
                    stringResource(R.string.clear),
                    { onEvent(SearchEvent.ClearRecents) },
                    Modifier.testTag("search_clear_recents"),
                    size = ButtonSize.Xs,
                )
            }
            recents.forEachIndexed { index, recent ->
                RecentRow(recent, showDivider = index < recents.lastIndex) {
                    onEvent(SearchEvent.RecentTap(recent.query, recent.directionId))
                }
            }
        }
        if (showTip) {
            TipBox(
                stringResource(R.string.wylie_tip),
                Modifier
                    .padding(top = if (recents.isEmpty()) 0.dp else TipTop)
                    .testTag("search_tip"),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

/** A 48dp recent-search row: clock, the query, and the headword opened from it (muted) when there is one. */
@Composable
private fun RecentRow(recent: RecentRowUi, showDivider: Boolean, onClick: () -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DictTheme.dimens.historyRow)
            .bottomDivider(colors.divider, show = showDivider)
            .clickable(onClick = onClick)
            .testTag("search_recent_${recent.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RecentGap),
    ) {
        Icon(DictIcons.History, contentDescription = null, Modifier.size(RecentIcon), tint = colors.faint)
        MixedText(
            recent.query,
            type.bodySmall,
            type.tibetanExample,
            Modifier.weight(1f),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (recent.openedHeadword != null) {
            MixedText(
                recent.openedHeadword,
                type.caption,
                type.tibetanInline16,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Placeholder rows while a slow search runs. */
@Composable
private fun LoadingBody(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("search_loading")) {
        SkeletonEntryRow(preset = SkeletonPreset.First)
        SkeletonEntryRow(preset = SkeletonPreset.Second)
    }
}

/** "N results" and the result rows (1g); the next page loads when the end of the list comes into view. */
@Composable
private fun ResultsBody(state: SearchUiState, onEvent: (SearchEvent) -> Unit, modifier: Modifier = Modifier) {
    val dimens = DictTheme.dimens
    val results = state.results
    Column(modifier.fillMaxWidth()) {
        if (results.isNotEmpty()) {
            val count = TibetanNumerals.formatCount(state.resultCount, DictTheme.language)
            MixedText(
                pluralStringResource(R.plurals.results_count, state.resultCount, if (state.hasMore) "$count+" else count),
                DictTheme.type.caption,
                DictTheme.type.tibetanInline14,
                Modifier
                    .padding(start = dimens.screenPadding, end = dimens.screenPadding, bottom = CountBottom)
                    .testTag("search_results_count"),
                color = DictTheme.colors.muted,
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            itemsIndexed(results, key = { _, result -> result.entryId }) { index, result ->
                EntryRow(
                    result,
                    onClick = { onEvent(SearchEvent.OpenResult(result.entryId)) },
                    modifier = Modifier.testTag("result_row_${result.entryId}"),
                    highlighted = index == 0 && state.highlightFirst,
                    showDivider = index < results.lastIndex || state.hasMore,
                )
            }
            if (state.hasMore && results.isNotEmpty()) {
                item(key = LOAD_MORE_KEY) {
                    // Composed once scrolled into view; asks again after each page while it stays in view.
                    LaunchedEffect(results.size) { onEvent(SearchEvent.LoadMore) }
                    SkeletonEntryRow()
                }
            }
        }
    }
}

/** "No match for …", the hint, did-you-mean rows and "Try X instead" (1k). */
@Composable
private fun NoResultsBody(state: SearchUiState, onEvent: (SearchEvent) -> Unit, modifier: Modifier = Modifier) {
    val dimens = DictTheme.dimens
    val hint = when {
        state.suggestions.isEmpty() -> R.string.no_match_body
        state.noMatchHint == NoMatchHint.Wylie -> R.string.no_match_hint_wylie
        state.noMatchHint == NoMatchHint.English -> R.string.no_match_hint_english
        else -> R.string.no_match_hint_tibetan
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = NoResultsTop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FullEmptyState(
            icon = DictIcons.EmptyState.Search,
            title = stringResource(R.string.no_match_title, state.query.text.trim()),
            body = stringResource(hint),
            circleSize = dimens.emptyStateCircleMedium,
            iconTint = DictTheme.colors.muted,
        )
        if (state.suggestions.isNotEmpty()) {
            SuggestionCard(
                state.suggestions,
                onEvent,
                Modifier.padding(start = SuggestionCardSide, top = SuggestionCardTop, end = SuggestionCardSide),
            )
        }
        state.alternateDirection?.let { alternate ->
            OutlinedButton(
                stringResource(R.string.try_direction_instead, directionLabelText(alternate)),
                { onEvent(SearchEvent.TryAlternateDirection) },
                Modifier
                    .padding(top = dimens.sectionGap)
                    .testTag("search_try_alternate"),
                size = ButtonSize.Small,
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

/** Did-you-mean rows on one surface1 card (radius 16), divided by hairlines. */
@Composable
private fun SuggestionCard(
    suggestions: List<SuggestionUi>,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .background(DictTheme.colors.surface1),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            SuggestionRow(suggestion, showDivider = index < suggestions.lastIndex) {
                onEvent(SearchEvent.SuggestionTap(suggestion.entryId, suggestion.directionId))
            }
        }
    }
}

/** Headword (Jomolhari 22, or Latin 16/500) over a muted "wylie · gloss" line, then a chevron. */
@Composable
private fun SuggestionRow(suggestion: SuggestionUi, showDivider: Boolean, onClick: () -> Unit) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .bottomDivider(colors.divider, show = showDivider)
            .clickable(onClick = onClick)
            .testTag("suggestion_row_${suggestion.entryId}")
            .padding(horizontal = dimens.cardPaddingHorizontal, vertical = dimens.entryRowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.blockGap),
    ) {
        Column(Modifier.weight(1f)) {
            MixedText(suggestion.headword, type.headwordLatin, type.tibetanSaved, color = colors.ink)
            if (suggestion.detail.isNotEmpty()) {
                MixedText(
                    suggestion.detail,
                    type.secondary,
                    type.tibetanInline14,
                    // Tucked into the tall Jomolhari line box, like the Wylie line of a result row.
                    if (suggestion.headwordIsTibetan) Modifier.pullUp(WylieOverlap) else Modifier,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(DictIcons.ChevronRight, contentDescription = null, Modifier.size(SuggestionChevron), tint = colors.faint)
    }
}

/** Preview states built from the component samples. */
private object SearchSamples {
    private val s = PreviewSamples

    private fun state(query: String, phase: SearchPhase, selected: Direction = s.boEn) = SearchUiState(
        query = TextFieldValue(query, TextRange(query.length)),
        script = if (query.isEmpty()) InputScript.LATIN else InputScript.WYLIE,
        slots = s.threeSlots,
        selectedDirection = selected,
        phase = phase,
        micAvailable = true,
    )

    val suggestions = state("", SearchPhase.Suggestions).copy(
        recents = listOf(
            RecentRowUi(1, "སྙིང་རྗེ།", true, "bo-en", openedHeadword = null, openedIsTibetan = false),
            RecentRowUi(2, "wisdom", false, "en-bo", openedHeadword = "ཤེས་རབ།", openedIsTibetan = true),
            RecentRowUi(3, "bkra shis", false, "bo-en", openedHeadword = "བཀྲ་ཤིས།", openedIsTibetan = true),
        ),
        showTip = true,
    )

    val loading = state("bkra", SearchPhase.Loading)

    val results = state("bkra", SearchPhase.Results).copy(
        autoSelected = true,
        results = listOf(
            s.tashi.copy(showBadge = false),
            s.tashiDelek,
            s.tashiDelek.copy(entryId = 9, headword = "བཀྲ་ཤིས་པ།", wylie = "bkra shis pa", gloss = "auspicious one"),
        ),
        highlightFirst = true,
        resultCount = 3,
        hasMore = false,
    )

    val noResults = state("bkrashi", SearchPhase.NoResults).copy(
        suggestions = listOf(
            SuggestionUi(1, "bo-en", "བཀྲ་ཤིས།", true, "bkra shis · auspicious"),
            SuggestionUi(2, "bo-en", "བཀྲ་ཤིས་པ།", true, "bkra shis pa · auspicious one"),
        ),
        alternateDirection = s.enBo,
        noMatchHint = NoMatchHint.Wylie,
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun SearchSuggestionsPreview() {
    ComponentPreview(edgeToEdge = true) { SearchContent(SearchSamples.suggestions, onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun SearchLoadingPreview() {
    ComponentPreview(edgeToEdge = true) { SearchContent(SearchSamples.loading, onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun SearchResultsPreview() {
    ComponentPreview(edgeToEdge = true) { SearchContent(SearchSamples.results, onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun SearchNoResultsPreview() {
    ComponentPreview(edgeToEdge = true) { SearchContent(SearchSamples.noResults, onEvent = {}) }
}
