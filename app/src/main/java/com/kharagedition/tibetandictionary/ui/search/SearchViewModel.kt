package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.speech.SpeechAvailability
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.SearchHit
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepository
import com.kharagedition.tibetandictionary.domain.DetectScript
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.domain.InputScript
import com.kharagedition.tibetandictionary.domain.ResolveDirection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.BreakIterator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Search route (1g/3b/1k). Typing is debounced, then each query is detected ([DetectScript]), its direction
 * resolved from per-candidate hit counts ([ResolveDirection]) and its first page shown; the skeleton appears only
 * when a search runs longer than [SKELETON_DELAY_MS]. A tapped switcher segment pins its direction until the
 * query is cleared. Opening a result records the query in recents; a query is counted for the streak the first
 * time it finds something. Failed searches are reported through [errors] and shown as no results.
 *
 * Starts with [initialQuery] and, when [voice] is set and speech recognition is available, with voice input.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = SearchViewModel.Factory::class)
class SearchViewModel @AssistedInject constructor(
    @Assisted initialQuery: String,
    @Assisted voice: Boolean,
    private val dictionary: DictionaryRepository,
    private val recentSearches: RecentSearchesRepository,
    private val activity: ActivityRepository,
    prefs: UserPreferencesRepository,
    speech: SpeechAvailability,
    private val errors: ErrorReporter,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initialQuery: String, voice: Boolean): SearchViewModel
    }

    /** The user's direction: [directionId] (null = the default direction), [pinned] by a tapped segment. */
    private data class Selection(val directionId: String? = null, val pinned: Boolean = false)

    /** One search to run: the normalised [query], the user's [selectedId] and the visible [directions]. */
    private data class Request(
        val query: String,
        val selectedId: String,
        val pinned: Boolean,
        val directions: List<Direction>,
    )

    /**
     * What search [id] found for a [Request], in the resolved [directionId]; [pages] of [PAGE_SIZE] have been
     * fetched.
     */
    private data class Outcome(
        val id: Int,
        val query: String,
        val script: InputScript,
        val directionId: String,
        val autoSelected: Boolean,
        val alternateId: String?,
        val hits: List<SearchHit>,
        val hasMore: Boolean,
        val suggestions: List<Suggestion>,
        val pages: Int = 1,
    )

    private val micAvailable = Features.VOICE_SEARCH && speech.isAvailable()

    private val field = MutableStateFlow(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    private val selection = MutableStateFlow(Selection())

    /** The latest search's outcome; null while the query is empty or before its first search finishes. */
    private val outcome = MutableStateFlow<Outcome?>(null)

    /** True once the running search has taken longer than [SKELETON_DELAY_MS]. */
    private val loading = MutableStateFlow(false)

    private val capabilities: StateFlow<DictionaryCapabilities?> =
        dictionary.capabilities.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val defaultDirectionId: Flow<String> = prefs.prefs.map { it.defaultDirectionId }.distinctUntilChanged()

    /** The normalised query that was last counted with [ActivityRepository.markSearch]. */
    private var countedQuery: String? = null

    /** The id of the last search started; each [Outcome] carries its own. */
    private var lastSearchId = 0
    private var loadMoreJob: Job? = null

    private val effectChannel = Channel<SearchEffect>(Channel.BUFFERED)
    val effects: Flow<SearchEffect> = effectChannel.receiveAsFlow()

    private val recents: Flow<List<RecentRowUi>> = recentSearches.observe().map { rows -> rows.map { it.toRow() } }

    val uiState: StateFlow<SearchUiState> = combine(
        combine(field, selection, outcome, loading, ::Inputs),
        capabilities.filterNotNull(),
        defaultDirectionId,
        recents,
    ) { inputs, caps, defaultId, recentRows -> inputs.toUiState(caps, defaultId, recentRows) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SearchUiState(query = field.value, micAvailable = micAvailable),
        )

    init {
        // An emptied field passes at once, so typing the same query again right after clearing is a new search.
        val queries = field
            .map { normalize(it.text) }
            .distinctUntilChanged()
            .debounce { query -> if (query.isEmpty()) 0L else DEBOUNCE_MS }
            .distinctUntilChanged()
        combine(
            queries,
            selection,
            capabilities.filterNotNull(),
            defaultDirectionId,
        ) { query, sel, caps, defaultId -> request(query, sel, caps, defaultId) }
            .distinctUntilChanged()
            .mapLatest { request -> request?.let { search(it) } }
            .onEach { result ->
                // A search that finishes after the field was emptied has nothing left to show.
                if (result != null && normalize(field.value.text).isEmpty()) return@onEach
                outcome.value = result
                if (result != null) countSearch(result)
            }
            .launchIn(viewModelScope)

        val start = if (voice && micAvailable) SearchEffect.StartVoiceSearch else SearchEffect.FocusField
        effectChannel.trySend(start)
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> changeQuery(event.value)
            SearchEvent.Clear -> {
                changeQuery(TextFieldValue())
                effectChannel.trySend(SearchEffect.FocusField)
            }
            SearchEvent.Back -> effectChannel.trySend(SearchEffect.NavigateBack)
            is SearchEvent.SelectDirection -> selection.value = Selection(event.directionId, pinned = true)
            is SearchEvent.OpenResult -> openResult(event.entryId)
            is SearchEvent.RecentTap -> {
                changeQuery(TextFieldValue(event.query, TextRange(event.query.length)))
                selection.value = Selection(event.directionId, pinned = false)
            }
            SearchEvent.ClearRecents -> launchSafely { recentSearches.clear() }
            is SearchEvent.SuggestionTap -> effectChannel.trySend(SearchEffect.NavigateEntry(event.entryId))
            SearchEvent.TryAlternateDirection -> outcome.value?.alternateId?.let {
                selection.value = Selection(it, pinned = true)
            }
            SearchEvent.VoiceSearch -> effectChannel.trySend(SearchEffect.StartVoiceSearch)
            SearchEvent.LoadMore -> loadMore()
        }
    }

    /** Takes the field's new value; emptying it drops the shown outcome at once and unpins the direction. */
    private fun changeQuery(value: TextFieldValue) {
        field.value = value
        if (normalize(value.text).isEmpty()) {
            selection.update { it.copy(pinned = false) }
            outcome.value = null
        }
    }

    /** The search for [query], or null when there is nothing to search (empty field, no visible direction). */
    private fun request(query: String, sel: Selection, caps: DictionaryCapabilities, defaultId: String): Request? {
        // Clearing unpins the selection before the debounced query catches up; never search the old text then.
        if (query.isEmpty() || normalize(field.value.text).isEmpty()) return null
        val selectedId = selectedDirection(sel, caps, defaultId)?.id ?: return null
        return Request(query, selectedId, sel.pinned, caps.directions)
    }

    /**
     * Runs [request]: Latin and Wylie queries search every [ResolveDirection.candidates] direction (in parallel)
     * and count the hits on each candidate's field; the resolved direction's result is reused. Tibetan and Han
     * queries search the resolved direction once.
     */
    private suspend fun search(request: Request): Outcome = withSkeleton {
        loadMoreJob?.cancel()
        val id = ++lastSearchId
        val query = request.query
        val script = DetectScript.detect(query)
        try {
            val candidates = ResolveDirection.candidates(script, request.selectedId, request.directions)
            val results: Map<String, SearchResult> = coroutineScope {
                candidates.map { it.direction.id }
                    .map { id -> async { id to dictionary.search(query, id, PAGE_SIZE) } }
                    .awaitAll()
                    .toMap()
            }
            // Only hits on the field a candidate competes with count: a gloss match never moves the direction.
            val counted = candidates.associate { candidate ->
                val hits = results.getValue(candidate.direction.id).hits
                candidate.direction.id to hits.filter { it.field == candidate.field }
            }
            val resolution = ResolveDirection.resolve(
                script = script,
                selected = request.selectedId,
                pinned = request.pinned,
                directions = request.directions,
                hits = counted.mapValues { (_, hits) -> hits.size },
                exactHits = counted.mapValues { (_, hits) -> hits.count { it.isExact(query) } },
            )
            val result = results[resolution.directionId] ?: dictionary.search(query, resolution.directionId, PAGE_SIZE)
            Outcome(
                id = id,
                query = query,
                script = script,
                directionId = resolution.directionId,
                autoSelected = resolution.autoSelected,
                alternateId = resolution.alternateId,
                hits = result.hits,
                hasMore = result.hasMore,
                suggestions = result.suggestions,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.record(t)
            Outcome(
                id = id,
                query = query,
                script = script,
                directionId = request.selectedId,
                autoSelected = false,
                alternateId = null,
                hits = emptyList(),
                hasMore = false,
                suggestions = emptyList(),
            )
        }
    }

    /** Runs [block], flagging [loading] if it is still running after [SKELETON_DELAY_MS]. */
    private suspend fun <T> withSkeleton(block: suspend () -> T): T = coroutineScope {
        val skeleton = launch {
            delay(SKELETON_DELAY_MS)
            loading.value = true
        }
        try {
            block()
        } finally {
            skeleton.cancel()
            loading.value = false
        }
    }

    /** Counts a query for the streak the first time it finds something. */
    private fun countSearch(result: Outcome) {
        if (result.hits.isEmpty() || result.query == countedQuery) return
        countedQuery = result.query
        launchSafely { activity.markSearch() }
    }

    /**
     * Fetches the next page of the shown outcome and appends the rows not already listed. A page that fails or
     * adds nothing ends paging; one that lands after another search replaced the outcome is dropped.
     */
    private fun loadMore() {
        val current = outcome.value ?: return
        if (!current.hasMore || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            val page = try {
                dictionary.search(current.query, current.directionId, PAGE_SIZE, current.pages * PAGE_SIZE)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errors.record(t)
                null
            }
            outcome.update { latest ->
                when {
                    latest == null || latest.id != current.id -> latest
                    page == null -> latest.copy(hasMore = false)
                    else -> {
                        val hits = (latest.hits + page.hits).distinctBy { it.entry.id }
                        latest.copy(
                            hits = hits,
                            hasMore = page.hasMore && hits.size > latest.hits.size,
                            pages = latest.pages + 1,
                        )
                    }
                }
            }
        }
    }

    /** Opens a result; the query is recorded unless it is shorter than [MIN_RECORDED_LENGTH] characters. */
    private fun openResult(entryId: Long) {
        effectChannel.trySend(SearchEffect.NavigateEntry(entryId))
        val current = outcome.value ?: return
        val opened = current.hits.firstOrNull { it.entry.id == entryId }?.entry ?: return
        if (graphemeCount(current.query) < MIN_RECORDED_LENGTH) return
        launchSafely { recentSearches.record(current.query, current.directionId, opened) }
    }

    private fun launchSafely(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.record(t)
        }
    }

    /** Everything the user controls or the search produced, for [toUiState]. */
    private data class Inputs(
        val field: TextFieldValue,
        val selection: Selection,
        val outcome: Outcome?,
        val loading: Boolean,
    )

    private fun Inputs.toUiState(
        caps: DictionaryCapabilities,
        defaultId: String,
        recents: List<RecentRowUi>,
    ): SearchUiState {
        val directions = caps.directions
        val userSelected = selectedDirection(selection, caps, defaultId)
        val blank = normalize(field.text).isEmpty()
        val shown = outcome.takeUnless { blank }
        val searched = shown?.let { o -> directions.firstOrNull { it.id == o.directionId } }
        val selected = if (selection.pinned) userSelected else searched ?: userSelected
        val phase = when {
            blank -> SearchPhase.Suggestions
            loading -> SearchPhase.Loading
            shown == null || shown.hits.isNotEmpty() -> SearchPhase.Results
            else -> SearchPhase.NoResults
        }
        val results = shown?.hits.orEmpty().mapNotNull { it.toUi(directions, shown?.directionId) }
        val noResults = phase == SearchPhase.NoResults
        return SearchUiState(
            query = field,
            script = shown?.script ?: DetectScript.detect(field.text),
            slots = DirectionSlots.compute(directions, defaultId, selected?.id),
            selectedDirection = selected,
            autoSelected = !selection.pinned && shown?.autoSelected == true,
            phase = phase,
            results = results,
            highlightFirst = shown?.hits?.firstOrNull()?.isExact(shown.query) == true,
            resultCount = results.size,
            hasMore = shown?.hasMore == true,
            recents = recents,
            showTip = phase == SearchPhase.Suggestions && caps.hasWylie,
            suggestions = if (noResults) shown?.suggestions.orEmpty().map { it.toUi() } else emptyList(),
            alternateDirection = if (noResults) directions.firstOrNull { it.id == shown?.alternateId } else null,
            noMatchHint = noMatchHint(shown?.script),
            micAvailable = micAvailable,
            settledQuery = shown?.query.orEmpty(),
            searchId = shown?.id ?: 0,
            pagesLoaded = shown?.pages ?: 0,
        )
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val SKELETON_DELAY_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
        const val PAGE_SIZE = 50

        /**
         * Normalised queries of fewer characters, counted as the user sees them (one letter, one Tibetan stack
         * such as ཀྲ), are searched but never recorded.
         */
        const val MIN_RECORDED_LENGTH = 2

        private val Whitespace = Regex("\\s+")

        /**
         * The query as searched and recorded: non-breaking tshegs made plain, whitespace trimmed and collapsed,
         * trailing shads dropped and Latin lower-cased (the repository normalises the same way).
         */
        fun normalize(text: String): String {
            val collapsed = text
                .replace(TibetanScript.NON_BREAKING_TSHEG, TibetanScript.TSHEG)
                .trim()
                .replace(Whitespace, " ")
                .trimEnd('།', '༎', ' ')
            return if (TibetanScript.hasTibetan(collapsed)) collapsed else collapsed.lowercase()
        }

        /** The user's selection when it is visible, else the default direction (falling back to the first). */
        fun selectedDirection(sel: Selection, caps: DictionaryCapabilities, defaultId: String): Direction? =
            caps.directions.firstOrNull { it.id == sel.directionId && it.entryCount > 0 }
                ?: DirectionSlots.defaultDirection(caps.directions, defaultId)

        /** User-perceived characters in [text]: a Tibetan stack such as ཀྲ counts once. */
        fun graphemeCount(text: String): Int {
            val boundaries = BreakIterator.getCharacterInstance().apply { setText(text) }
            var count = 0
            while (boundaries.next() != BreakIterator.DONE) count++
            return count
        }
    }
}
