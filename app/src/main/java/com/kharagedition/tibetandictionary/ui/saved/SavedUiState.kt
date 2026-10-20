package com.kharagedition.tibetandictionary.ui.saved

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import com.kharagedition.tibetandictionary.domain.SavedPeriod
import com.kharagedition.tibetandictionary.ui.model.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi

/** The search-within field shows once this many words are saved. */
internal const val SEARCH_MIN_COUNT = 8

/**
 * The Saved tab (1i/3c). [loading] until the saved words are first read. [totalCount] counts every saved word;
 * [groups] are the words that pass the filter ([selectedFolderId], null = All, and [query]), by when they were
 * saved. [folderPicker] is set while the "Save to folder" sheet is open for today's word, [newFolder] while the
 * new-folder sheet is open.
 */
@Immutable
data class SavedUiState(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val folders: List<FolderUi> = emptyList(),
    val selectedFolderId: Long? = null,
    val query: String = "",
    val groups: List<SavedGroupUi> = emptyList(),
    val wotd: WordOfTheDayUi? = null,
    val folderPicker: FolderPickerUi? = null,
    val newFolder: NewFolderUi? = null,
) {
    /** Nothing is saved at all: the empty state (3c) instead of the list. */
    val isEmpty: Boolean get() = !loading && totalCount == 0

    val showSearch: Boolean get() = totalCount >= SEARCH_MIN_COUNT

    /** "Save today's word" under the empty state: there is a word of the day and it is not saved yet. */
    val showSaveWotd: Boolean get() = wotd != null && !wotd.isSaved

    /** Words are saved but none passes the filter: the inline "No matches". */
    val noMatches: Boolean get() = !loading && totalCount > 0 && groups.isEmpty()
}

/** One section of the list: the words saved in [period], newest first. */
@Immutable
data class SavedGroupUi(val period: SavedPeriod, val items: List<SavedItemUi>)

/**
 * One saved word, drawn from the snapshot taken when it was saved (so it shows even if a newer dictionary no
 * longer has the entry). [secondary] is "{wylie} · {gloss}", or the gloss alone when there is no Wylie.
 */
@Immutable
data class SavedItemUi(
    val entryId: Long,
    val headword: String,
    val headwordIsTibetan: Boolean,
    val secondary: AnnotatedString,
    val folderId: Long?,
)

/** The new-folder sheet: the [name] typed so far and an [error] (a duplicate name) shown under it. */
@Immutable
data class NewFolderUi(val name: String = "", val error: UiText? = null)

/** User actions on the Saved tab. */
sealed interface SavedEvent {
    data class QueryChanged(val query: String) : SavedEvent

    /** Shows the words of [folderId]; null shows all of them. */
    data class SelectFolder(val folderId: Long?) : SavedEvent

    /** Opens the new-folder sheet (the dashed "+" chip). */
    data object StartAddFolder : SavedEvent

    /** Edits the name in the new-folder sheet, or in the folder picker's new-folder field. */
    data class NewFolderNameChanged(val name: String) : SavedEvent

    /** Creates a folder called [name] from the new-folder sheet; a duplicate name shows an error instead. */
    data class AddFolder(val name: String) : SavedEvent

    data object DismissAddFolder : SavedEvent

    /** Removes the word from Saved; the screen then offers Undo. */
    data class Unsave(val entryId: Long) : SavedEvent

    /** Saves the word removed by [Unsave] again, back in [folderId]. */
    data class UndoUnsave(val entryId: Long, val folderId: Long?) : SavedEvent

    /** Opens the entry, or explains that a newer dictionary no longer has it. */
    data class OpenEntry(val entryId: Long) : SavedEvent

    /** Saves today's word (the empty state's button) to the last-used folder. */
    data object SaveWotd : SavedEvent

    /** Opens the folder picker for the saved word [entryId] (the "Change" action of the saved toast). */
    data class ChangeFolder(val entryId: Long) : SavedEvent

    /** Files the picker's word in [folderId] (null = no folder) and remembers it for the next save. */
    data class PickFolder(val folderId: Long?) : SavedEvent

    data object StartCreateFolder : SavedEvent

    data object CancelCreateFolder : SavedEvent

    /** Creates a folder called [name] in the picker and files the word in it. */
    data class CreateFolder(val name: String) : SavedEvent

    data object DismissFolderPicker : SavedEvent
}

/** One-shot effects the screen performs. */
sealed interface SavedEffect {
    data class NavigateEntry(val entryId: Long) : SavedEffect

    /** [entryId] was removed from [folderId]: a "Removed" toast whose Undo sends [SavedEvent.UndoUnsave]. */
    data class Removed(val entryId: Long, val folderId: Long?) : SavedEffect

    /**
     * A toast with [message] (resolved by the screen in the app language) and an optional [action] label;
     * tapping the action sends [actionEvent].
     */
    data class Toast(
        val message: UiText,
        @param:StringRes val action: Int? = null,
        val actionEvent: SavedEvent? = null,
    ) : SavedEffect
}
