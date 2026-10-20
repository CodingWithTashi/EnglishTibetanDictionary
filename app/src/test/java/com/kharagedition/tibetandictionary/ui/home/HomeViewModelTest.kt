package com.kharagedition.tibetandictionary.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.SampleData.boBo
import com.kharagedition.tibetandictionary.SampleData.boEn
import com.kharagedition.tibetandictionary.SampleData.enBo
import com.kharagedition.tibetandictionary.SampleData.recentHouse
import com.kharagedition.tibetandictionary.SampleData.recentTashi
import com.kharagedition.tibetandictionary.SampleData.tashi
import com.kharagedition.tibetandictionary.SampleData.tashiEntry
import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import com.kharagedition.tibetandictionary.core.audio.AudioSource
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.fakes.FakeActivityRepository
import com.kharagedition.tibetandictionary.fakes.FakeActivityRepository.Day
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeRecentSearchesRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.FakeWordOfTheDayRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** 2026-10-19 09:00 UTC: a morning. */
    private val clock = FakeClock()
    private val today: LocalDate = clock.today()
    private val dictionary = FakeDictionaryRepository()
    private val wordOfTheDay = FakeWordOfTheDayRepository()
    private val activity = FakeActivityRepository(clock)
    private val recents = FakeRecentSearchesRepository(clock = clock)
    private val saved = FakeSavedRepository(clock = clock)
    private val prefs = FakeUserPreferencesRepository(UserPrefs(onboarded = true, defaultDirectionId = boEn.id))
    private val errors = RecordingErrorReporter()
    private val player = RecordingAudioPlayer()
    private var speechAvailable = true

    private class RecordingAudioPlayer : AudioPlayer {
        val played = mutableListOf<AudioSource>()

        override fun play(source: AudioSource) {
            played += source
        }

        override fun stop() = Unit
    }

    /** A view model whose state is collected for the whole test, as the screen would. */
    private fun TestScope.viewModel(
        wotdRepository: WordOfTheDayRepository = wordOfTheDay,
        savedRepository: SavedRepository = saved,
        audio: AudioPlayer? = player,
    ): HomeViewModel {
        val vm = HomeViewModel(
            dictionary = dictionary,
            wordOfTheDay = wotdRepository,
            activity = activity,
            recentSearches = recents,
            saved = savedRepository,
            saveActions = SavedWordActions(savedRepository, prefs),
            prefs = prefs,
            clock = clock,
            speech = { speechAvailable },
            audioPlayer = Optional.ofNullable(audio),
            errors = errors,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private val HomeViewModel.state: HomeUiState get() = uiState.value

    private fun activeOn(vararg daysAgo: Long) {
        activity.days.value = daysAgo.associate { today.minusDays(it) to Day(searches = 1) }
    }

    private fun wotdWith(audioFile: String? = null, wylie: String? = tashi.wylie): Entry =
        tashiEntry.copy(summary = tashi.copy(audioFile = audioFile, wylie = wylie))

    // State

    @Test
    fun `home shows the greeting, streak, default direction, word of the day and recents`() = runTest {
        activeOn(0, 1, 2)
        recents.recents.value = listOf(recentTashi, recentHouse)
        val vm = viewModel()
        advanceUntilIdle()

        with(vm.state) {
            assertThat(greeting).isEqualTo(TimeOfDay.MORNING)
            assertThat(streakDays).isEqualTo(3)
            assertThat(visibleStreak).isEqualTo(3)
            assertThat(selectedDirection).isEqualTo(boEn)
            assertThat(slots.fixed).containsExactly(boEn, boBo, enBo).inOrder()
            assertThat(slots.overflow).isEmpty()
            assertThat(wotd).isEqualTo(
                WordOfTheDayUi(
                    entryId = tashi.id,
                    date = "19 Oct",
                    headword = tashi.headword,
                    wylie = tashi.wylie,
                    gloss = tashi.glossPreview,
                    hasAudio = false,
                    audioFile = null,
                    isSaved = false,
                ),
            )
            // Newest first.
            assertThat(recents).containsExactly(
                RecentChipUi("house"),
                RecentChipUi("bkra shis"),
            ).inOrder()
            assertThat(micAvailable).isTrue()
        }
    }

    @Test
    fun `the greeting follows the clock`() = runTest {
        clock.instant = Instant.parse("2026-10-19T20:30:00Z")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.greeting).isEqualTo(TimeOfDay.EVENING)
    }

    @Test
    fun `Shown moves the greeting, streak and word of the day on to the current time and day`() = runTest {
        activeOn(0, 1, 2)
        val asked = mutableListOf<LocalDate>()
        val recording = object : WordOfTheDayRepository by wordOfTheDay {
            override fun observe(date: LocalDate): Flow<Entry?> = wordOfTheDay.observe(date).also { asked += date }
        }
        val vm = viewModel(wotdRepository = recording)
        advanceUntilIdle()
        assertThat(vm.state.greeting).isEqualTo(TimeOfDay.MORNING)
        assertThat(vm.state.streakDays).isEqualTo(3)
        assertThat(vm.state.wotd?.date).isEqualTo("19 Oct")

        // Home stayed open two days; nothing moves until it is shown again.
        clock.instant = Instant.parse("2026-10-21T20:30:00Z")
        vm.onEvent(HomeEvent.Shown)
        advanceUntilIdle()

        assertThat(vm.state.greeting).isEqualTo(TimeOfDay.EVENING)
        // The run ended on the 19th, two days ago.
        assertThat(vm.state.streakDays).isEqualTo(0)
        assertThat(vm.state.wotd?.date).isEqualTo("21 Oct")
        assertThat(asked).containsExactly(today, LocalDate.parse("2026-10-21")).inOrder()

        // Shown again on the same day reads nothing again.
        vm.onEvent(HomeEvent.Shown)
        advanceUntilIdle()
        assertThat(asked).hasSize(2)
    }

    @Test
    fun `no word of the day hides the card`() = runTest {
        wordOfTheDay.entry.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.wotd).isNull()
    }

    @Test
    fun `the word of the day date uses Tibetan digits in the Tibetan UI`() = runTest {
        prefs.prefs.value = prefs.prefs.value.copy(appLanguage = AppLanguage.BO)
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.wotd?.date).isEqualTo("༡༩ Oct")
    }

    @Test
    fun `a streak shorter than two days hides the pill`() = runTest {
        activeOn(0)
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.streakDays).isEqualTo(1)
        assertThat(vm.state.visibleStreak).isNull()

        // Two days, ending yesterday, still count.
        activeOn(1, 2)
        assertThat(vm.state.visibleStreak).isEqualTo(2)

        activeOn(2, 3, 4)
        assertThat(vm.state.streakDays).isEqualTo(0)
        assertThat(vm.state.visibleStreak).isNull()
    }

    @Test
    fun `stats have no source in release 1, so the card is hidden`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.stats).isNull()
        assertThat(vm.state.visibleStats).isNull()
    }

    @Test
    fun `the mic is hidden when speech recognition is unavailable`() = runTest {
        speechAvailable = false
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.micAvailable).isFalse()
    }

    @Test
    fun `the same query searched in two directions is one chip`() = runTest {
        recents.recents.value = listOf(recentTashi, recentTashi.copy(id = 7, directionId = boBo.id))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.recents.map { it.query }).containsExactly("bkra shis")
    }

    // Direction

    @Test
    fun `SelectDirection persists the default direction`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(HomeEvent.SelectDirection(enBo.id))
        advanceUntilIdle()

        assertThat(prefs.prefs.value.defaultDirectionId).isEqualTo(enBo.id)
        assertThat(vm.state.selectedDirection).isEqualTo(enBo)
    }

    @Test
    fun `a stored default direction that is gone falls back to the first visible direction`() = runTest {
        prefs.setDefaultDirectionId("zh-bo")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.selectedDirection).isEqualTo(boEn)
        assertThat(vm.state.slots.fixed).containsExactly(boEn, boBo, enBo).inOrder()
    }

    // Word of the day actions

    @Test
    fun `WotdToggleSave saves to the last used folder and names it, then removes with Undo`() = runTest {
        saved.folders.value = listOf(Folder(id = 5, name = "Dharma", position = 0, count = 0))
        prefs.setLastUsedFolderId(5)
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdToggleSave)
            assertThat(awaitItem())
                .isEqualTo(HomeEffect.Toast(UiText(R.string.saved_to_folder, listOf(tashi.headword, "Dharma"))))
            assertThat(saved.saved.value.single().folderId).isEqualTo(5)
            assertThat(vm.state.wotd?.isSaved).isTrue()

            vm.onEvent(HomeEvent.WotdToggleSave)
            val removed = awaitItem() as HomeEffect.Toast
            assertThat(removed.message).isEqualTo(UiText(R.string.removed))
            assertThat(removed.action).isEqualTo(R.string.undo)
            assertThat(saved.saved.value).isEmpty()
            assertThat(vm.state.wotd?.isSaved).isFalse()
        }
    }

    @Test
    fun `Undo after removing today's word puts it back in its folder with its save time`() = runTest {
        saved.folders.value = listOf(Folder(id = 5, name = "Dharma", position = 0, count = 0))
        prefs.setLastUsedFolderId(5)
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdToggleSave)
            awaitItem()
            val original = saved.saved.value.single()

            vm.onEvent(HomeEvent.WotdToggleSave)
            val removed = awaitItem() as HomeEffect.Toast
            checkNotNull(removed.onAction).invoke()
            advanceUntilIdle()

            assertThat(saved.saved.value).containsExactly(original)
            assertThat(vm.state.wotd?.isSaved).isTrue()
        }
    }

    @Test
    fun `a save with no folder says Saved and the headword`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdToggleSave)
            assertThat(awaitItem()).isEqualTo(HomeEffect.Toast(UiText(R.string.saved_simple, listOf(tashi.headword))))
        }
    }

    @Test
    fun `a double tap on the heart saves once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowSave = object : SavedRepository by saved {
            override suspend fun save(entry: EntrySummary, folderId: Long?) {
                gate.await()
                saved.save(entry, folderId)
            }
        }
        val vm = viewModel(savedRepository = slowSave)
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdToggleSave)
            vm.onEvent(HomeEvent.WotdToggleSave)
            gate.complete(Unit)
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(HomeEffect.Toast(UiText(R.string.saved_simple, listOf(tashi.headword))))
            expectNoEvents()
        }
        assertThat(saved.saved.value.map { it.entryId }).containsExactly(tashi.id)
        assertThat(vm.state.wotd?.isSaved).isTrue()
    }

    @Test
    fun `a deleted last used folder saves the word without a folder`() = runTest {
        prefs.setLastUsedFolderId(9)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(HomeEvent.WotdToggleSave)
        advanceUntilIdle()

        assertThat(saved.saved.value.single().entryId).isEqualTo(tashi.id)
        assertThat(saved.saved.value.single().folderId).isNull()
    }

    @Test
    fun `a failing save is recorded without a toast`() = runTest {
        val failingSave = object : SavedRepository by saved {
            override suspend fun save(entry: EntrySummary, folderId: Long?) = throw IOException("disk full")
        }
        val vm = viewModel(savedRepository = failingSave)
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdToggleSave)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertThat(errors.recorded.map { it.message }).containsExactly("disk full")
        assertThat(vm.state.wotd?.isSaved).isFalse()
    }

    @Test
    fun `WotdShare shares the headword, Wylie and first gloss`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdShare)
            assertThat(awaitItem())
                .isEqualTo(HomeEffect.Share("${tashi.headword} (bkra shis) — auspicious, fortunate, favourable"))

            wordOfTheDay.entry.value = wotdWith(wylie = null)
            vm.onEvent(HomeEvent.WotdShare)
            assertThat(awaitItem()).isEqualTo(HomeEffect.Share("${tashi.headword} — auspicious, fortunate, favourable"))
        }
    }

    @Test
    fun `WotdOpen opens the entry`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(HomeEvent.WotdOpen)
            assertThat(awaitItem()).isEqualTo(HomeEffect.NavigateEntry(tashi.id))
        }
    }

    @Test
    fun `WotdPlay plays the bundled audio file`() = runTest {
        wordOfTheDay.entry.value = wotdWith(audioFile = "bkra_shis.ogg")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.wotd?.hasAudio).isTrue()
        vm.onEvent(HomeEvent.WotdPlay)

        assertThat(player.played).containsExactly(AudioSource.Asset("audio/bkra_shis.ogg"))
    }

    @Test
    fun `without an audio player the speaker is hidden and play does nothing`() = runTest {
        wordOfTheDay.entry.value = wotdWith(audioFile = "bkra_shis.ogg")
        val vm = viewModel(audio = null)
        advanceUntilIdle()

        assertThat(vm.state.wotd?.hasAudio).isFalse()
        vm.onEvent(HomeEvent.WotdPlay)
        assertThat(errors.recorded).isEmpty()
    }

    // Navigation

    @Test
    fun `RecentTap opens search with the query`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(HomeEvent.RecentTap("bkra shis"))
            assertThat(awaitItem()).isEqualTo(HomeEffect.NavigateSearch("bkra shis", voice = false))
        }
    }

    @Test
    fun `the field opens search and the mic opens voice search`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(HomeEvent.OpenSearch)
            assertThat(awaitItem()).isEqualTo(HomeEffect.NavigateSearch(null, voice = false))
            vm.onEvent(HomeEvent.VoiceSearch)
            assertThat(awaitItem()).isEqualTo(HomeEffect.NavigateSearch(null, voice = true))
            vm.onEvent(HomeEvent.StatsTap)
            expectNoEvents()
        }
    }

    // Failures

    @Test
    fun `a failing word of the day read hides the card and is recorded`() = runTest {
        val failing = object : WordOfTheDayRepository {
            override fun observe(date: LocalDate): Flow<Entry?> = flow { throw IOException("wotd read failed") }
            override suspend fun forDate(date: LocalDate): Entry? = throw IOException("wotd read failed")
        }
        recents.recents.value = listOf(recentHouse)
        val vm = viewModel(wotdRepository = failing)
        advanceUntilIdle()

        assertThat(vm.state.wotd).isNull()
        assertThat(vm.state.recents).containsExactly(RecentChipUi("house"))
        assertThat(errors.recorded.map { it.message }).containsExactly("wotd read failed")
    }

    @Test
    fun `a word of the day that throws on request hides the card and is recorded`() = runTest {
        val failing = object : WordOfTheDayRepository {
            override fun observe(date: LocalDate): Flow<Entry?> = TODO("not installed")
            override suspend fun forDate(date: LocalDate): Entry? = TODO("not installed")
        }
        val vm = viewModel(wotdRepository = failing)
        advanceUntilIdle()

        assertThat(vm.state.wotd).isNull()
        assertThat(vm.state.selectedDirection).isEqualTo(boEn)
        assertThat(errors.recorded).hasSize(1)
    }
}
