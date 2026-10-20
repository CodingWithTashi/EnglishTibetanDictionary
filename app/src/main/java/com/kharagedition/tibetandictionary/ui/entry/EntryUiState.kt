package com.kharagedition.tibetandictionary.ui.entry

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.ui.model.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.model.UiText

/**
 * The entry screen (1h/1l). [loading] until the entry is read; then either [entry] or [missing] (the id is not in
 * the installed dictionary, or reading it failed); [folderPicker] is non-null while the "Save to folder" sheet is
 * open.
 */
@Immutable
data class EntryUiState(
    val loading: Boolean = true,
    val entry: EntryUi? = null,
    val isSaved: Boolean = false,
    /**
     * Folder the saved word is filed in (null when unsaved or unfiled): the folder the saved toast names, not drawn
     * yet and reserved for a folder line under the header.
     */
    val savedFolderName: String? = null,
    val folderPicker: FolderPickerUi? = null,
    val missing: Boolean = false,
)

/**
 * One entry ready to draw. [partsOfSpeech] are display abbreviations ("adj.", "n."); [label] holds the canonical
 * usage-label keys every sense shares, comma-joined (null when none; shown after the parts of speech through
 * `usageLabelText`); [senses] are flattened sub-senses, numbered only when the entry has more than one sense, and
 * then only on each sense's first card; [related] is empty when there is nothing to see also.
 */
@Immutable
data class EntryUi(
    val id: Long,
    val direction: Direction,
    val headword: String,
    val headwordIsTibetan: Boolean,
    val wylie: String?,
    val phonetic: String?,
    val partsOfSpeech: List<String>,
    val label: String?,
    val senses: List<SenseUi>,
    val related: List<RelatedUi>,
    val hasAudio: Boolean,
    val audioFile: String?,
    val isTruncated: Boolean,
)

/** User actions on the entry screen. */
sealed interface EntryEvent {
    data object Back : EntryEvent

    /** Saves to the last-used folder, or removes the saved word. */
    data object ToggleSave : EntryEvent

    /** Opens the folder picker (the "Change" action of the saved toast). */
    data object ChangeFolder : EntryEvent

    /** Files the saved word in [folderId] (null = no folder) and remembers it for the next save. */
    data class PickFolder(val folderId: Long?) : EntryEvent

    data object StartCreateFolder : EntryEvent

    data object CancelCreateFolder : EntryEvent

    data class NewFolderNameChanged(val name: String) : EntryEvent

    /** Creates a folder called [name] and files the word in it; a duplicate name shows an error instead. */
    data class CreateFolder(val name: String) : EntryEvent

    data object DismissFolderPicker : EntryEvent

    data object Copy : EntryEvent

    data object Share : EntryEvent

    data object PlayAudio : EntryEvent

    /** Opens a see-also link: the entry when its id is known, otherwise a search for its headword. */
    data class OpenRelated(val related: RelatedUi) : EntryEvent

    data object ReportCorrection : EntryEvent
}

/** One-shot effects the screen performs. */
sealed interface EntryEffect {
    data object NavigateBack : EntryEffect

    data class NavigateEntry(val entryId: Long) : EntryEffect

    data class NavigateSearch(val query: String) : EntryEffect

    /**
     * A toast with [message] and an optional [action] label; tapping the action runs [onAction]. Copy is resolved
     * by the screen, so it follows the per-app language (the application context does not below API 33).
     */
    data class Toast(
        val message: UiText,
        @param:StringRes val action: Int? = null,
        val onAction: (() -> Unit)? = null,
    ) : EntryEffect

    /** Copies [text]; [label] (the headword) names the clip for the system. */
    data class CopyToClipboard(val label: String, val text: String) : EntryEffect

    data class ShareText(val text: String) : EntryEffect

    /** Opens the e-mail app addressed to [address]. */
    data class OpenEmail(val address: String, val subject: String, val body: String) : EntryEffect
}
