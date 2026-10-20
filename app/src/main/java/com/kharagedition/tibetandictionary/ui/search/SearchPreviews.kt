package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.domain.InputScript
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples

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
        settledQuery = "bkra",
        searchId = 1,
        pagesLoaded = 1,
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
        settledQuery = "bkrashi",
        searchId = 1,
        pagesLoaded = 1,
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
