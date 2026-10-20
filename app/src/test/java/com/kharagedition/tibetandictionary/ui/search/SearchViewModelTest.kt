package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.cash.turbine.Event
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.SampleData.boBo
import com.kharagedition.tibetandictionary.SampleData.boEn
import com.kharagedition.tibetandictionary.SampleData.enBo
import com.kharagedition.tibetandictionary.SampleData.house
import com.kharagedition.tibetandictionary.SampleData.recentHouse
import com.kharagedition.tibetandictionary.SampleData.recentTashi
import com.kharagedition.tibetandictionary.SampleData.tashi
import com.kharagedition.tibetandictionary.SampleData.tashiBoBo
import com.kharagedition.tibetandictionary.SampleData.tashiDelek
import com.kharagedition.tibetandictionary.data.model.MatchField
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.model.SearchHit
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.domain.InputScript
import com.kharagedition.tibetandictionary.fakes.FakeActivityRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository.SearchCall
import com.kharagedition.tibetandictionary.fakes.FakeRecentSearchesRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val dictionary = FakeDictionaryRepository()
    private val recents = FakeRecentSearchesRepository()
    private val activity = FakeActivityRepository()
    private val prefs = FakeUserPreferencesRepository(UserPrefs(onboarded = true, defaultDirectionId = boEn.id))
    private val errors = RecordingErrorReporter()
    private var speechAvailable = true

    /** A view model whose state is collected for the whole test, as the screen would. */
    private fun TestScope.viewModel(
        initialQuery: String = "",
        voice: Boolean = false,
        repository: DictionaryRepository = dictionary,
    ): SearchViewModel {
        val vm = SearchViewModel(initialQuery, voice, repository, recents, activity, prefs, { speechAvailable }, errors)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    /** [dictionary], with every search taking [delayMs]. */
    private fun slowDictionary(delayMs: Long) = object : DictionaryRepository by dictionary {
        override suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult {
            delay(delayMs)
            return dictionary.search(query, directionId, limit, offset)
        }
    }

    private fun SearchViewModel.type(text: String) =
        onEvent(SearchEvent.QueryChanged(TextFieldValue(text, TextRange(text.length))))

    private val SearchViewModel.state: SearchUiState get() = uiState.value

    private fun searchedQueries() = dictionary.searches.map { it.query }.distinct()

    // Debounce

    @Test
    fun `typing searches once, 150 ms after the last keystroke`() = runTest {
        val vm = viewModel()

        vm.type("b")
        advanceTimeBy(100)
        vm.type("bk")
        advanceTimeBy(100)
        vm.type("bkra")
        advanceTimeBy(149)
        assertThat(dictionary.searches).isEmpty()

        advanceTimeBy(2)
        assertThat(searchedQueries()).containsExactly("bkra")
        assertThat(vm.state.phase).isEqualTo(SearchPhase.Results)
    }

    @Test
    fun `an initial query is searched without typing`() = runTest {
        val vm = viewModel(initialQuery = "bkra")
        advanceUntilIdle()

        assertThat(vm.state.query.text).isEqualTo("bkra")
        assertThat(vm.state.results.map { it.entryId }).containsExactly(tashi.id, tashiDelek.id).inOrder()
    }

    // Direction resolution

    @Test
    fun `a Wylie query auto-selects the Wylie direction that has hits`() = runTest {
        prefs.setDefaultDirectionId(enBo.id)
        val vm = viewModel()

        vm.type("bkra")
        advanceUntilIdle()

        with(vm.state) {
            assertThat(script).isEqualTo(InputScript.WYLIE)
            assertThat(selectedDirection).isEqualTo(boEn)
            assertThat(autoSelected).isTrue()
            assertThat(phase).isEqualTo(SearchPhase.Results)
            assertThat(results.map { it.entryId }).containsExactly(tashi.id, tashiDelek.id).inOrder()
            assertThat(results.first().matchRange).isEqualTo(0..3)
            assertThat(results.first().showBadge).isFalse()
        }
        // One search per candidate; the winner's result is reused rather than searched again.
        assertThat(dictionary.searches).containsExactly(
            SearchCall("bkra", enBo.id, 50, 0),
            SearchCall("bkra", boEn.id, 50, 0),
        )
    }

    @Test
    fun `a Wylie query with no hits in Tibetan definitions moves to Tibetan to English`() = runTest {
        prefs.setDefaultDirectionId(boBo.id)
        val vm = viewModel()

        vm.type("snying")
        advanceUntilIdle()

        assertThat(vm.state.selectedDirection).isEqualTo(boEn)
        assertThat(vm.state.autoSelected).isTrue()
        assertThat(vm.state.results.map { it.entryId }).containsExactly(SampleData.nyingje.id)
    }

    @Test
    fun `the selected direction keeps a query it has hits for`() = runTest {
        val vm = viewModel()

        vm.type("bkra")
        advanceUntilIdle()

        assertThat(vm.state.selectedDirection).isEqualTo(boEn)
        assertThat(vm.state.autoSelected).isFalse()
    }

    @Test
    fun `gloss matches do not move the direction`() = runTest {
        prefs.setDefaultDirectionId(enBo.id)
        val vm = viewModel()

        // "fortune" is only in a bo-en gloss; hits are counted on headwords (en-bo) and Wylie (bo-en).
        vm.type("fortune")
        advanceUntilIdle()

        assertThat(vm.state.selectedDirection).isEqualTo(enBo)
        assertThat(vm.state.autoSelected).isFalse()
        assertThat(vm.state.phase).isEqualTo(SearchPhase.NoResults)
        assertThat(vm.state.alternateDirection).isEqualTo(boEn)
    }

    @Test
    fun `a Tibetan query is searched once in a Tibetan-source direction`() = runTest {
        prefs.setDefaultDirectionId(enBo.id)
        val vm = viewModel()

        vm.type("བཀྲ")
        advanceUntilIdle()

        assertThat(vm.state.script).isEqualTo(InputScript.TIBETAN)
        assertThat(vm.state.selectedDirection).isEqualTo(boEn)
        assertThat(vm.state.autoSelected).isTrue()
        assertThat(dictionary.searches).containsExactly(SearchCall("བཀྲ", boEn.id, 50, 0))
    }

    @Test
    fun `a tapped segment pins its direction until the query is cleared`() = runTest {
        val vm = viewModel()
        vm.type("bkra")
        advanceUntilIdle()

        vm.onEvent(SearchEvent.SelectDirection(enBo.id))
        advanceUntilIdle()

        with(vm.state) {
            assertThat(selectedDirection).isEqualTo(enBo)
            assertThat(autoSelected).isFalse()
            assertThat(phase).isEqualTo(SearchPhase.NoResults)
            assertThat(alternateDirection).isEqualTo(boEn)
        }

        vm.type("bkra shis")
        advanceUntilIdle()
        assertThat(vm.state.selectedDirection).isEqualTo(enBo)

        // Cleared: en-bo stays the selection but no longer holds a Wylie query.
        vm.onEvent(SearchEvent.Clear)
        vm.type("bkra")
        advanceUntilIdle()

        assertThat(vm.state.selectedDirection).isEqualTo(boEn)
        assertThat(vm.state.autoSelected).isTrue()
    }

    // Suggestions phase

    @Test
    fun `an empty query shows the recent searches and the tip`() = runTest {
        recents.recents.value = listOf(recentTashi, recentHouse)
        val vm = viewModel()
        advanceUntilIdle()

        with(vm.state) {
            assertThat(phase).isEqualTo(SearchPhase.Suggestions)
            assertThat(recents).containsExactly(
                RecentRowUi(recentHouse.id, "house", false, enBo.id, openedHeadword = null, openedIsTibetan = false),
                RecentRowUi(recentTashi.id, "bkra shis", false, boEn.id, openedHeadword = tashi.headword, openedIsTibetan = true),
            ).inOrder()
            assertThat(showTip).isTrue()
            assertThat(selectedDirection).isEqualTo(boEn)
            assertThat(slots.fixed).containsExactly(boEn, boBo, enBo).inOrder()
        }
        assertThat(dictionary.searches).isEmpty()
    }

    @Test
    fun `the tip is hidden when no direction has Wylie`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(
            directions = SampleData.directions.map { it.copy(features = it.features.copy(hasWylie = false)) },
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.phase).isEqualTo(SearchPhase.Suggestions)
        assertThat(vm.state.showTip).isFalse()
    }

    @Test
    fun `clearing returns to the suggestions at once and refocuses the field`() = runTest {
        val vm = viewModel()
        vm.type("bkra")
        advanceUntilIdle()

        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(SearchEffect.FocusField) // on start

            vm.onEvent(SearchEvent.Clear)

            assertThat(vm.state.query.text).isEmpty()
            assertThat(vm.state.phase).isEqualTo(SearchPhase.Suggestions)
            assertThat(vm.state.results).isEmpty()
            assertThat(awaitItem()).isEqualTo(SearchEffect.FocusField)
        }
    }

    @Test
    fun `clearing and quickly typing the same query again searches it again`() = runTest {
        val vm = viewModel()
        vm.type("bkra")
        advanceUntilIdle()

        vm.onEvent(SearchEvent.Clear)
        advanceTimeBy(50)
        vm.type("bkra")
        advanceUntilIdle()

        assertThat(vm.state.phase).isEqualTo(SearchPhase.Results)
        assertThat(vm.state.results.map { it.entryId }).containsExactly(tashi.id, tashiDelek.id).inOrder()
    }

    @Test
    fun `a recent search runs again in its direction`() = runTest {
        recents.recents.value = listOf(recentHouse)
        val vm = viewModel()

        vm.onEvent(SearchEvent.RecentTap("house", enBo.id))
        advanceUntilIdle()

        with(vm.state) {
            assertThat(query.text).isEqualTo("house")
            assertThat(query.selection).isEqualTo(TextRange(5))
            assertThat(selectedDirection).isEqualTo(enBo)
            assertThat(autoSelected).isFalse()
            assertThat(results.map { it.entryId }).containsExactly(house.id)
            assertThat(highlightFirst).isTrue()
        }
    }

    @Test
    fun `clear recents empties the list`() = runTest {
        recents.recents.value = listOf(recentTashi, recentHouse)
        val vm = viewModel()

        vm.onEvent(SearchEvent.ClearRecents)
        advanceUntilIdle()

        assertThat(recents.recents.value).isEmpty()
        assertThat(vm.state.recents).isEmpty()
    }

    // Results

    @Test
    fun `only an exact match highlights the first row`() = runTest {
        prefs.setDefaultDirectionId(enBo.id)
        val vm = viewModel()

        vm.type("hous")
        advanceUntilIdle()
        assertThat(vm.state.results.map { it.entryId }).containsExactly(house.id)
        assertThat(vm.state.highlightFirst).isFalse()

        vm.type("House")
        advanceUntilIdle()
        assertThat(vm.state.highlightFirst).isTrue()
        assertThat(vm.state.resultCount).isEqualTo(1)
        assertThat(vm.state.hasMore).isFalse()
    }

    @Test
    fun `the skeleton shows only once a search takes longer than 250 ms`() = runTest {
        val vm = viewModel(repository = slowDictionary(delayMs = 400))

        vm.type("bkra")
        advanceTimeBy(150 + 249)
        assertThat(vm.state.phase).isNotEqualTo(SearchPhase.Loading)

        advanceTimeBy(2)
        assertThat(vm.state.phase).isEqualTo(SearchPhase.Loading)

        advanceUntilIdle()
        assertThat(vm.state.phase).isEqualTo(SearchPhase.Results)
    }

    @Test
    fun `a search under 250 ms never shows the skeleton`() = runTest {
        val vm = viewModel(repository = slowDictionary(delayMs = 200))

        vm.uiState.test {
            vm.type("bkra")
            advanceUntilIdle()

            val phases = cancelAndConsumeRemainingEvents()
                .filterIsInstance<Event.Item<SearchUiState>>()
                .map { it.value.phase }
            assertThat(phases).doesNotContain(SearchPhase.Loading)
            assertThat(phases.last()).isEqualTo(SearchPhase.Results)
        }
    }

    @Test
    fun `load more appends the next page without repeating rows`() = runTest {
        val many = (1..60).map { i -> tashi.copy(id = 1_100_000L + i, wylie = "bkra shis $i") }
        val big = FakeDictionaryRepository(entries = many.map { SampleData.tashiEntry.copy(summary = it) })
        // Page 0 carries two full-text rows beyond the limit, as the real repository may.
        val repository = object : DictionaryRepository by big {
            override suspend fun search(query: String, directionId: String, limit: Int, offset: Int) =
                big.search(query, directionId, if (offset == 0) limit + 2 else limit, offset)
        }
        val vm = viewModel(repository = repository)

        vm.type("bkra")
        advanceUntilIdle()
        assertThat(vm.state.resultCount).isEqualTo(52)
        assertThat(vm.state.hasMore).isTrue()

        vm.onEvent(SearchEvent.LoadMore)
        advanceUntilIdle()

        assertThat(big.searches.last()).isEqualTo(SearchCall("bkra", boEn.id, 50, 50))
        assertThat(vm.state.results.map { it.entryId }).containsExactlyElementsIn(many.map { it.id }).inOrder()
        assertThat(vm.state.resultCount).isEqualTo(60)
        assertThat(vm.state.hasMore).isFalse()

        vm.onEvent(SearchEvent.LoadMore)
        advanceUntilIdle()
        assertThat(big.searches.last().offset).isEqualTo(50)
    }

    @Test
    fun `a failing search is reported and shows no results`() = runTest {
        val failure = IllegalStateException("database closed")
        val failing = object : DictionaryRepository by dictionary {
            override suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult =
                throw failure
        }
        val vm = viewModel(repository = failing)

        vm.type("bkra")
        advanceUntilIdle()

        // Coroutines may hand over a copy of the exception (stack-trace recovery), so compare the message.
        assertThat(errors.recorded.map { it.message }).containsExactly(failure.message)
        assertThat(vm.state.phase).isEqualTo(SearchPhase.NoResults)

        // The pipeline survives: the next query is searched too.
        vm.type("bkra shis")
        advanceUntilIdle()
        assertThat(errors.recorded.map { it.message }).containsExactly(failure.message, failure.message)
    }

    // No results

    @Test
    fun `no results offers did-you-mean rows and another direction`() = runTest {
        dictionary.searchResults["bkrashi" to boEn.id] = SearchResult(
            query = "bkrashi",
            directionId = boEn.id,
            script = Script.LATIN,
            hits = emptyList(),
            hasMore = false,
            suggestions = listOf(
                Suggestion(SearchHit(tashi, MatchField.WYLIE, null, null, null), boEn.id),
                Suggestion(SearchHit(tashiBoBo, MatchField.WYLIE, null, null, null), boBo.id),
            ),
        )
        val vm = viewModel()

        vm.type("bkrashi")
        advanceUntilIdle()

        with(vm.state) {
            assertThat(phase).isEqualTo(SearchPhase.NoResults)
            assertThat(selectedDirection).isEqualTo(boEn)
            assertThat(noMatchHint).isEqualTo(NoMatchHint.Wylie)
            assertThat(alternateDirection).isEqualTo(enBo)
            assertThat(suggestions).containsExactly(
                SuggestionUi(tashi.id, boEn.id, tashi.headword, true, "bkra shis · ${tashi.glossPreview}"),
                SuggestionUi(tashiBoBo.id, boBo.id, tashiBoBo.headword, true, "bkra shis · ${tashiBoBo.glossPreview}"),
            ).inOrder()
        }

        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(SearchEffect.FocusField) // on start
            vm.onEvent(SearchEvent.SuggestionTap(tashiBoBo.id, boBo.id))
            assertThat(awaitItem()).isEqualTo(SearchEffect.NavigateEntry(tashiBoBo.id))
        }
    }

    @Test
    fun `try the alternate direction runs the query pinned to it`() = runTest {
        prefs.setDefaultDirectionId(enBo.id)
        val vm = viewModel()
        vm.type("fortune")
        advanceUntilIdle()

        vm.onEvent(SearchEvent.TryAlternateDirection)
        advanceUntilIdle()

        with(vm.state) {
            assertThat(selectedDirection).isEqualTo(boEn)
            assertThat(autoSelected).isFalse()
            assertThat(phase).isEqualTo(SearchPhase.Results)
            assertThat(results.map { it.entryId }).containsExactly(tashi.id)
            assertThat(alternateDirection).isNull()
        }
    }

    @Test
    fun `the no-match hint follows the script`() = runTest {
        val vm = viewModel()

        vm.type("zzzz")
        advanceUntilIdle()
        assertThat(vm.state.noMatchHint).isEqualTo(NoMatchHint.English)

        vm.type("ཀཀཀ")
        advanceUntilIdle()
        assertThat(vm.state.noMatchHint).isEqualTo(NoMatchHint.Tibetan)
    }

    // Review Focus 1

    @Test
    fun oddQueries() = runTest {
        val vm = viewModel()

        for (query in listOf("བཀྲ shis", "བཀྲ་ཤིས།", " a ", "", "bkra  shis")) {
            vm.type(query)
            advanceUntilIdle()
            vm.state.results.firstOrNull()?.let { vm.onEvent(SearchEvent.OpenResult(it.entryId)) }
            advanceUntilIdle()
        }

        assertThat(errors.recorded).isEmpty()
        assertThat(searchedQueries()).containsExactly("བཀྲ shis", "བཀྲ་ཤིས", "a", "bkra shis").inOrder()
        // " a " opened a result too, but one character is never recorded; nor is a blank query.
        assertThat(recents.recents.value.map { it.query }).containsExactly("bkra shis", "བཀྲ་ཤིས").inOrder()
        assertThat(vm.state.phase).isEqualTo(SearchPhase.Results)
    }

    // Opening a result

    @Test
    fun `opening a result records the search and counts it once`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(SearchEffect.FocusField)

            vm.type("bkra")
            advanceUntilIdle()
            vm.onEvent(SearchEvent.OpenResult(tashi.id))

            assertThat(awaitItem()).isEqualTo(SearchEffect.NavigateEntry(tashi.id))
        }
        advanceUntilIdle()

        val recent = recents.recents.value.single()
        assertThat(recent.query).isEqualTo("bkra")
        assertThat(recent.directionId).isEqualTo(boEn.id)
        assertThat(recent.entryId).isEqualTo(tashi.id)
        assertThat(recent.headword).isEqualTo(tashi.headword)
        assertThat(activity.days.value.values.single().searches).isEqualTo(1)
    }

    @Test
    fun `a search is counted once per query, and only when it finds something`() = runTest {
        val vm = viewModel()

        vm.type("zzzz")
        advanceUntilIdle()
        assertThat(activity.days.value).isEmpty()

        vm.type("bkra")
        advanceUntilIdle()
        // The same query in another direction is not a new search.
        vm.onEvent(SearchEvent.SelectDirection(boBo.id))
        advanceUntilIdle()
        assertThat(activity.days.value.values.single().searches).isEqualTo(1)

        vm.type("bkra shis")
        advanceUntilIdle()
        assertThat(activity.days.value.values.single().searches).isEqualTo(2)
    }

    // Voice and navigation

    @Test
    fun `the mic is offered when speech recognition is available`() = runTest {
        assertThat(viewModel().state.micAvailable).isTrue()

        speechAvailable = false
        assertThat(viewModel().state.micAvailable).isFalse()
    }

    @Test
    fun `a route opened for voice starts voice input instead of focusing the field`() = runTest {
        viewModel(voice = true).effects.test {
            assertThat(awaitItem()).isEqualTo(SearchEffect.StartVoiceSearch)
            expectNoEvents()
        }
    }

    @Test
    fun `the mic and back buttons become effects`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(SearchEffect.FocusField)
            vm.onEvent(SearchEvent.VoiceSearch)
            assertThat(awaitItem()).isEqualTo(SearchEffect.StartVoiceSearch)
            vm.onEvent(SearchEvent.Back)
            assertThat(awaitItem()).isEqualTo(SearchEffect.NavigateBack)
        }
    }
}
