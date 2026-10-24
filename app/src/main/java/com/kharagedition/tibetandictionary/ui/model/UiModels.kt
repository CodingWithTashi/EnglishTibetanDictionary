package com.kharagedition.tibetandictionary.ui.model

import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.data.model.Direction

/**
 * Today's word for the Home card. [date] is formatted ("19 Oct") with Latin digits; the card shows them in the UI
 * language's digits, as every other count and number on screen.
 */
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
 * only has [examples] (every example of the gloss, in order). [label] holds the sense's canonical usage-label keys
 * as the data writes them, comma-joined ("poetic", "adm,law"), when they are not already the entry's label; the
 * card shows them as an outline badge (see `usageLabelText`). [note] is the sense's note ("broke, broken"), without
 * the source's bare "*" markers. Label and note go on a sense's first card only.
 */
@Immutable
data class SenseUi(
    val number: Int?,
    val partOfSpeech: String?,
    val text: String,
    val examples: List<ExampleUi> = emptyList(),
    val label: String? = null,
    val note: String? = null,
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

/** The signed-in Google account shown on Profile: its [name] (when it has one) over its [email]. */
@Immutable
data class AccountUi(
    val name: String?,
    val email: String,
    val initials: String,
)
