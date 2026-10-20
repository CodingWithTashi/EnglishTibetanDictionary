package com.kharagedition.tibetandictionary.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import com.kharagedition.tibetandictionary.core.audio.AudioSource
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.speech.SpeechAvailability
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.domain.Greeting
import com.kharagedition.tibetandictionary.domain.StreakCalculator
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions.Companion.savedMessage
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home, the Search tab's landing page (1a/2a): greeting and streak, the search field and direction switcher,
 * today's word, recent searches. Every source is read defensively: a failure is reported through [errors] and
 * that part of the page is left out (a failing word of the day hides the card), so Home never crashes.
 *
 * The greeting, the streak and today's word are for the time and day Home was last shown ([HomeEvent.Shown]), so a
 * Home left open overnight moves on when it comes back. Switcher taps change the default direction. The heart
 * saves today's word to the last-used folder (unfiled when that folder is gone) or removes it through
 * [SavedWordActions]: the toast names the folder ("Saved {headword} to {folder}"), and a removal offers Undo, which
 * puts the row back as it was. Choosing a folder lives on the Entry screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    dictionary: DictionaryRepository,
    wordOfTheDay: WordOfTheDayRepository,
    activity: ActivityRepository,
    recentSearches: RecentSearchesRepository,
    saved: SavedRepository,
    private val saveActions: SavedWordActions,
    private val prefs: UserPreferencesRepository,
    private val clock: AppClock,
    speech: SpeechAvailability,
    audioPlayer: Optional<AudioPlayer>,
    private val errors: ErrorReporter,
) : ViewModel() {

    /** Today's word as loaded: the [entry] chosen for [date], and whether it is saved. */
    private data class Wotd(val entry: Entry, val date: LocalDate, val isSaved: Boolean)

    private val player: AudioPlayer? = audioPlayer.getOrNull()
    private val micAvailable = Features.VOICE_SEARCH && speech.isAvailable()

    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    private val capabilities: Flow<DictionaryCapabilities?> = safely(fallback = null) { dictionary.capabilities }
    private val userPrefs: Flow<UserPrefs> = safely(fallback = UserPrefs()) { prefs.prefs }

    /** The day Home shows: when it was last shown. */
    private val today = MutableStateFlow(clock.today())

    /** The time of day Home greets for: when it was last shown. */
    private val greeting = MutableStateFlow(Greeting.timeOfDay(clock))

    /** Consecutive active days ending [today] or the day before; null when the activity log cannot be read. */
    private val streak: Flow<Int?> = today.flatMapLatest { date ->
        safely(fallback = null) {
            activity.observeActiveDays(STREAK_LOOKBACK_DAYS).map { days -> StreakCalculator.streak(days.toSet(), date) }
        }
    }

    /** The greeting row: the time of day and the streak. */
    private val greetingRow: Flow<Pair<TimeOfDay, Int?>> = combine(greeting, streak, ::Pair)

    /** One chip per query, newest first: the same query searched in two directions searches the same way. */
    private val recents: Flow<List<RecentChipUi>> = safely(fallback = emptyList()) {
        recentSearches.observe(RECENT_LIMIT).map { rows ->
            rows.distinctBy { it.query }.map { RecentChipUi(it.query) }
        }
    }

    /** The word for [today] while Home is shown; null when there is none. */
    private val wotd: StateFlow<Wotd?> = today.flatMapLatest { date ->
        safely(fallback = null) {
            wordOfTheDay.observe(date).flatMapLatest { entry ->
                if (entry == null) {
                    flowOf(null)
                } else {
                    saved.observeIsSaved(entry.summary.id).map { isSaved -> Wotd(entry, date, isSaved) }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<HomeUiState> = combine(
        capabilities,
        userPrefs,
        greetingRow,
        wotd,
        recents,
    ) { caps, userPrefs, (timeOfDay, streakDays), todaysWord, recentChips ->
        val directions = caps?.directions.orEmpty()
        val selected = DirectionSlots.defaultDirection(directions, userPrefs.defaultDirectionId)
        HomeUiState(
            greeting = timeOfDay,
            streakDays = streakDays,
            slots = DirectionSlots.compute(directions, userPrefs.defaultDirectionId, selected?.id),
            selectedDirection = selected,
            wotd = todaysWord?.toUi(userPrefs.appLanguage),
            recents = recentChips,
            // Learn (Features.LEARN) has no stats source in release 1.
            stats = null,
            micAvailable = micAvailable,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        HomeUiState(greeting = greeting.value, micAvailable = micAvailable),
    )

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Shown -> {
                greeting.value = Greeting.timeOfDay(clock)
                today.value = clock.today()
            }
            HomeEvent.OpenSearch -> send(HomeEffect.NavigateSearch(query = null, voice = false))
            HomeEvent.VoiceSearch -> send(HomeEffect.NavigateSearch(query = null, voice = true))
            is HomeEvent.RecentTap -> send(HomeEffect.NavigateSearch(event.query, voice = false))
            is HomeEvent.SelectDirection -> launchSafely { prefs.setDefaultDirectionId(event.directionId) }
            HomeEvent.WotdPlay -> play()
            HomeEvent.WotdToggleSave -> toggleSave()
            HomeEvent.WotdShare -> wotd.value?.let { send(HomeEffect.Share(it.entry.shareText())) }
            HomeEvent.WotdOpen -> wotd.value?.let { send(HomeEffect.NavigateEntry(it.entry.summary.id)) }
            // The stats card only exists with Learn, which is not in release 1.
            HomeEvent.StatsTap -> Unit
        }
    }

    /** Plays today's word's bundled pronunciation, when it has one and a player is installed. */
    private fun play() {
        val file = wotd.value?.entry?.summary?.audioFile ?: return
        val player = player ?: return
        try {
            player.play(AudioSource.forDictionaryFile(file))
        } catch (t: Throwable) {
            errors.record(t)
        }
    }

    /**
     * Saves today's word, or removes it when it is saved; either way with a toast, the removal's with Undo. A second
     * tap while the first is still saving or removing does nothing.
     */
    private fun toggleSave() {
        val summary = wotd.value?.entry?.summary ?: return
        launchSafely {
            when (val result = saveActions.toggle(summary)) {
                null -> Unit
                is SavedWordActions.Toggle.Removed ->
                    send(HomeEffect.Toast(UiText(R.string.removed), R.string.undo) { undoUnsave(result.row) })
                is SavedWordActions.Toggle.Saved ->
                    send(HomeEffect.Toast(savedMessage(summary.headword, result.folder?.name)))
            }
        }
    }

    /** Undo: puts the removed [row] back unchanged (folder, note and save time). */
    private fun undoUnsave(row: SavedEntry) {
        launchSafely { saveActions.restore(row) }
    }

    private fun send(effect: HomeEffect) {
        effectChannel.trySend(effect)
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

    /**
     * [source] with any failure, including one thrown while asking for the flow, reported and replaced by
     * [fallback], so one broken source never takes Home down.
     */
    private fun <T> safely(fallback: T, source: () -> Flow<T>): Flow<T> =
        flow { emitAll(source()) }.catch { e ->
            if (e is CancellationException) throw e
            errors.record(e)
            emit(fallback)
        }

    private fun Wotd.toUi(language: AppLanguage): WordOfTheDayUi {
        val summary = entry.summary
        val day = date.format(WotdDate)
        return WordOfTheDayUi(
            entryId = summary.id,
            date = if (language.usesTibetanDigits) TibetanNumerals.toTibetan(day) else day,
            headword = summary.headword,
            wylie = summary.wylie?.takeIf { it.isNotBlank() },
            gloss = summary.glossPreview,
            // A speaker with nothing to play it would be a dead button.
            hasAudio = summary.hasAudio && player != null,
            audioFile = summary.audioFile,
            isSaved = isSaved,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Recent searches on the chip row. */
        const val RECENT_LIMIT = 10

        /** Active days read for the streak, which therefore counts at most a year. */
        const val STREAK_LOOKBACK_DAYS = 366

        /** "19 Oct" (2a); month names stay English until the bo strings are written. */
        val WotdDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

        /** "{headword} ({wylie}) — {first gloss}"; the Wylie and gloss parts are left out when missing. */
        fun Entry.shareText(): String {
            val gloss = senses.firstNotNullOfOrNull { sense ->
                sense.items.firstNotNullOfOrNull { item -> item.gloss.takeIf { it.isNotBlank() } }
            } ?: summary.glossPreview
            val wylie = summary.wylie?.takeIf { it.isNotBlank() }
            val head = if (wylie != null) "${summary.headword} ($wylie)" else summary.headword
            return if (gloss.isBlank()) head else "$head — $gloss"
        }
    }
}
