package com.kharagedition.tibetandictionary.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.ui.model.LearnStatsUi
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi

/** A recent search on Home's chip row: tapping it searches [query] again, in the current direction. */
@Immutable
data class RecentChipUi(val query: String)

/**
 * The Home page of the Search tab (1a/2a). [streakDays] is the run of active days, null until the activity log
 * is read (or when it cannot be); [selectedDirection] is the default direction, null until the dictionary's
 * directions are known. [stats] has no source in release 1 (Learn ships behind `Features.LEARN`).
 * [micAvailable] already includes `Features.VOICE_SEARCH`.
 */
@Immutable
data class HomeUiState(
    val greeting: TimeOfDay,
    val streakDays: Int? = null,
    val slots: DirectionSlots = DirectionSlots(fixed = emptyList(), overflow = emptyList()),
    val selectedDirection: Direction? = null,
    val wotd: WordOfTheDayUi? = null,
    val recents: List<RecentChipUi> = emptyList(),
    val stats: LearnStatsUi? = null,
    val micAvailable: Boolean = false,
) {
    /** The streak pill's count; null hides the pill (no streak known, or a run shorter than two days). */
    val visibleStreak: Int? get() = streakDays?.takeIf { it >= MIN_STREAK_DAYS }

    /** The stats card's figures; null hides the card (Learn is off, or there are no stats). */
    val visibleStats: LearnStatsUi? get() = stats?.takeIf { Features.LEARN }

    private companion object {
        /** A one-day "streak" is just today: the pill starts at two days. */
        const val MIN_STREAK_DAYS = 2
    }
}

/** User actions on Home. */
sealed interface HomeEvent {
    /**
     * Home was shown (also back from another tab, a pushed screen or the background): the greeting, the streak and
     * today's word move on to the current time and day.
     */
    data object Shown : HomeEvent

    /** The search field: opens the Search route. */
    data object OpenSearch : HomeEvent

    /** The field's mic: opens the Search route with voice input. */
    data object VoiceSearch : HomeEvent

    /** A switcher segment: makes the direction the default. */
    data class SelectDirection(val directionId: String) : HomeEvent

    data object WotdPlay : HomeEvent

    /** The word of the day's heart: saves it (to the last-used folder) or removes it. */
    data object WotdToggleSave : HomeEvent

    data object WotdShare : HomeEvent

    data object WotdOpen : HomeEvent

    data class RecentTap(val query: String) : HomeEvent

    /** The Learn stats card; nothing to open in release 1. */
    data object StatsTap : HomeEvent
}

/** One-shot effects Home performs. */
sealed interface HomeEffect {
    /** Opens the Search route with [query] (null = empty field), and with voice input when [voice] is set. */
    data class NavigateSearch(val query: String?, val voice: Boolean) : HomeEffect

    data class NavigateEntry(val entryId: Long) : HomeEffect

    /** Shares [text] through the system share sheet. */
    data class Share(val text: String) : HomeEffect

    /**
     * A toast with [message] and an optional [action] label; tapping the action runs [onAction]. Copy is resolved by
     * the screen, so it follows the per-app language.
     */
    data class Toast(
        val message: UiText,
        @param:StringRes val action: Int? = null,
        val onAction: (() -> Unit)? = null,
    ) : HomeEffect
}
