package com.kharagedition.tibetandictionary.ui.model

import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.data.model.Direction

/** Today's word for the Home card; [date] is already formatted for display. */
@Immutable
data class WordOfTheDayUi(
    val entryId: Long,
    val date: String,
    val headword: String,
    val wylie: String?,
    val gloss: String,
    val hasAudio: Boolean,
    val audioFile: String?,
    val isSaved: Boolean,
)

/** Learn progress for the Home stats card; [weekFlags] has one flag per day of the week (true = studied). */
@Immutable
data class LearnStatsUi(
    val learnedThisWeek: Int,
    val dueCount: Int?,
    val weekFlags: List<Boolean>,
)

/**
 * One search-result row. [matchRange] highlights part of [wylie] and [headwordMatchRange] part of [headword]
 * (inclusive index ranges); [showBadge] marks a hit from a direction other than the selected one.
 */
@Immutable
data class SearchResultUi(
    val entryId: Long,
    val direction: Direction,
    val headword: String,
    val headwordIsTibetan: Boolean,
    val wylie: String?,
    val matchRange: IntRange?,
    val headwordMatchRange: IntRange?,
    val gloss: String,
    val showBadge: Boolean,
)

/**
 * One sense card of an entry. [number] is null when the entry has a single sense, and on the later cards of a
 * numbered sense ([reserveNumberColumn], which keep the number column's indent); [text] may be blank when the card
 * only has an [example].
 */
@Immutable
data class SenseUi(
    val number: Int?,
    val partOfSpeech: String?,
    val text: String,
    val example: ExampleUi?,
    val reserveNumberColumn: Boolean = false,
)

/** A usage example of a sense, with an optional translation. */
@Immutable
data class ExampleUi(
    val text: String,
    val translation: String?,
    val textIsTibetan: Boolean,
)

/** A see-also link; [entryId] is null when the target is only known by its headword (open as a search). */
@Immutable
data class RelatedUi(
    val entryId: Long?,
    val direction: Direction,
    val headword: String,
    val isTibetan: Boolean,
)

/** A saved-words folder and how many entries it holds. */
@Immutable
data class FolderUi(
    val id: Long,
    val name: String,
    val count: Int,
)

/** The signed-in account shown on Profile; [syncedText] is the formatted last-sync line, if any. */
@Immutable
data class AccountUi(
    val email: String,
    val initials: String,
    val syncedText: String?,
)
