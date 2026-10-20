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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
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
 * Switcher taps change the default direction. The heart saves today's word to the last-used folder (unfiled
 * when that folder is gone) or removes it, with a plain toast; choosing a folder lives on the Entry screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    dictionary: DictionaryRepository,
    wordOfTheDay: WordOfTheDayRepository,
    activity: ActivityRepository,
    recentSearches: RecentSearchesRepository,
    private val saved: SavedRepository,
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

    /** Consecutive active days ending today or yesterday; null when the activity log cannot be read. */
    private val streak: Flow<Int?> = safely(fallback = null) {
        activity.observeActiveDays(STREAK_LOOKBACK_DAYS).map { days -> StreakCalculator.streak(days.toSet(), clock.today()) }
    }

    /** One chip per query, newest first: the same query searched in two directions searches the same way. */
    private val recents: Flow<List<RecentChipUi>> = safely(fallback = emptyList()) {
        recentSearches.observe(RECENT_LIMIT).map { rows ->
            rows.distinctBy { it.query }.map { RecentChipUi(query = it.query, directionId = it.directionId) }
        }
    }

    /** Today's word (for the day the screen started observing) while Home is shown; null when there is none. */
    private val wotd: StateFlow<Wotd?> = safely(fallback = null) {
        val date = clock.today()
        wordOfTheDay.observe(date).flatMapLatest { entry ->
            if (entry == null) {
                flowOf(null)
            } else {
                saved.observeIsSaved(entry.summary.id).map { isSaved -> Wotd(entry, date, isSaved) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<HomeUiState> = combine(
        capabilities,
        userPrefs,
        streak,
        wotd,
        recents,
    ) { caps, userPrefs, streakDays, today, recentChips ->
        val directions = caps?.directions.orEmpty()
        val selected = DirectionSlots.defaultDirection(directions, userPrefs.defaultDirectionId)
        HomeUiState(
            greeting = Greeting.timeOfDay(clock),
            streakDays = streakDays,
            slots = DirectionSlots.compute(directions, userPrefs.defaultDirectionId, selected?.id),
            selectedDirection = selected,
            wotd = today?.toUi(userPrefs.appLanguage),
            recents = recentChips,
            // Learn (Features.LEARN) has no stats source in release 1.
            stats = null,
            micAvailable = micAvailable,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        HomeUiState(greeting = Greeting.timeOfDay(clock), micAvailable = micAvailable),
    )

    fun onEvent(event: HomeEvent) {
        when (event) {
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
            player.play(AudioSource.Asset("$AUDIO_ASSET_DIR/$file"))
        } catch (e: Exception) {
            errors.record(e)
        }
    }

    /** Saves today's word as the heart showed it unsaved, or removes it; either way with a toast. */
    private fun toggleSave() {
        val current = wotd.value ?: return
        val summary = current.entry.summary
        launchSafely {
            if (current.isSaved) {
                saved.unsave(summary.id)
                send(HomeEffect.Toast(UiText(R.string.removed)))
            } else {
                saved.save(summary, lastUsedFolderId())
                send(HomeEffect.Toast(UiText(R.string.saved_simple, listOf(summary.headword))))
            }
        }
    }

    /** The folder the last word was saved to, while it still exists; otherwise the word is saved unfiled. */
    private suspend fun lastUsedFolderId(): Long? {
        val id = prefs.snapshot().lastUsedFolderId ?: return null
        return id.takeIf { saved.observeFolders().first().any { folder -> folder.id == id } }
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

        /** Bundled pronunciations live under this assets folder. */
        const val AUDIO_ASSET_DIR = "audio"

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
