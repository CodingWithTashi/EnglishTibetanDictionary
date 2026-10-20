package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.ui.text.AnnotatedString
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.domain.SavedGroup
import com.kharagedition.tibetandictionary.ui.entry.DOT_SEPARATOR
import com.kharagedition.tibetandictionary.ui.entry.isTibetanText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "19 Oct"; the Home card shows it, the Saved tab does not. */
private val WOTD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

internal fun SavedGroup.toUi(): SavedGroupUi = SavedGroupUi(period, items.map { it.toUi() })

/** The row as saved: headword, then "{wylie} · {gloss}" (the gloss alone without Wylie). */
internal fun SavedEntry.toUi(): SavedItemUi = SavedItemUi(
    entryId = entryId,
    headword = headword,
    headwordIsTibetan = isTibetanText(headword),
    secondary = AnnotatedString(
        listOfNotNull(wylie, glossPreview).filter { it.isNotBlank() }.joinToString(DOT_SEPARATOR),
    ),
    folderId = folderId,
)

/**
 * The summary to save this snapshot again (Undo): the fields the snapshot keeps; parts of speech, sense count and
 * audio are not stored with a saved word and are not needed to save it.
 */
internal fun SavedEntry.toSummary(): EntrySummary = EntrySummary(
    id = entryId,
    directionId = directionId,
    headword = headword,
    wylie = wylie,
    glossPreview = glossPreview,
    partOfSpeech = emptyList(),
    phonetic = null,
    audioFile = null,
    isTruncated = false,
    senseCount = 0,
)

/** Today's word ([date]) for the empty state's "Save today's word" button. */
internal fun Entry.toWotdUi(date: LocalDate, isSaved: Boolean): WordOfTheDayUi = WordOfTheDayUi(
    entryId = summary.id,
    date = date.format(WOTD_DATE),
    headword = summary.headword,
    wylie = summary.wylie?.takeIf { it.isNotBlank() },
    gloss = summary.glossPreview,
    hasAudio = summary.hasAudio,
    audioFile = summary.audioFile,
    isSaved = isSaved,
)
