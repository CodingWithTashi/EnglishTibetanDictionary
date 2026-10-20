package com.kharagedition.tibetandictionary.ui.model

import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The saving steps Entry, Saved and Home share: save to the last-used folder (none once that folder is deleted),
 * unsave, put an unsaved word back for Undo, file a saved word in a folder (remembered for the next save) and create
 * a folder from the picker. Each screen words its own toasts from the results ([savedMessage] is the common
 * "Saved {headword}[ to {folder}]").
 *
 * Writes run one at a time. [toggle], [saveIfAbsent] and [unsave] are also single-flight per entry: while one of
 * them runs for an entry, another for the same entry is dropped (it returns null), so a double tap on a heart saves
 * once instead of saving and then removing. Failures propagate to the caller.
 */
@Singleton
class SavedWordActions @Inject constructor(
    private val saved: SavedRepository,
    private val prefs: UserPreferencesRepository,
) {
    /** What [toggle] did. */
    sealed interface Toggle {
        /** The word was saved, in [folder] (null = no folder). */
        data class Saved(val folder: Folder?) : Toggle

        /** The word was removed; [row] is the saved row as it was, for [restore]. */
        data class Removed(val row: SavedEntry) : Toggle
    }

    /** Where [fileIn] put a word: its [headword] and the [folderName] (null = no folder). */
    data class Filed(val headword: String, val folderName: String?)

    /** What [createFolder] did with a name. */
    sealed interface NewFolder {
        data class Created(val id: Long) : NewFolder

        /** The name is taken (trimmed, any case); [error] says so in the app language. */
        data class Rejected(val error: UiText) : NewFolder
    }

    /** Serialises every write, so quick taps on different controls cannot interleave. */
    private val writeLock = Mutex()

    /** Entries with a single-flight action running. */
    private val busy = mutableSetOf<Long>()

    /**
     * Removes [entry] when it is saved, otherwise saves it to the last-used folder. Null when another action on
     * the same entry is still running: nothing was done.
     */
    suspend fun toggle(entry: EntrySummary): Toggle? = singleFlight(entry.id) {
        writeLock.withLock {
            val row = savedRow(entry.id)
            if (row != null) {
                saved.unsave(entry.id)
                Toggle.Removed(row)
            } else {
                Toggle.Saved(saveToLastUsed(entry))
            }
        }
    }

    /**
     * Saves [entry] to the last-used folder unless it is already saved. Null when it was already saved, or when
     * another action on the same entry is still running.
     */
    suspend fun saveIfAbsent(entry: EntrySummary): Toggle.Saved? = singleFlight(entry.id) {
        writeLock.withLock {
            if (savedRow(entry.id) != null) null else Toggle.Saved(saveToLastUsed(entry))
        }
    }

    /**
     * Removes the saved word [entryId] and returns its row as it was, for [restore]. Null when it is not saved, or
     * when another action on it is still running.
     */
    suspend fun unsave(entryId: Long): SavedEntry? = singleFlight(entryId) {
        writeLock.withLock {
            savedRow(entryId)?.also { saved.unsave(entryId) }
        }
    }

    /** Puts a removed [row] back exactly as it was: folder, note and save time (Undo). */
    suspend fun restore(row: SavedEntry) {
        writeLock.withLock { saved.restore(row) }
    }

    /**
     * Files the saved word [entryId] in [folderId] (null = no folder) and remembers that folder for the next save.
     * A word that is not saved is saved there from [unsaved] when given; without it nothing happens and the
     * result is null.
     */
    suspend fun fileIn(entryId: Long, folderId: Long?, unsaved: EntrySummary? = null): Filed? {
        val headword = writeLock.withLock {
            val row = savedRow(entryId)
            when {
                row != null -> {
                    saved.setFolder(entryId, folderId)
                    row.headword
                }
                unsaved != null -> {
                    saved.save(unsaved, folderId)
                    unsaved.headword
                }
                else -> null
            }
        } ?: return null
        prefs.setLastUsedFolderId(folderId)
        return Filed(headword, folderName(folderId))
    }

    /**
     * Creates a folder named [name] (trimmed). Null for a blank name, [NewFolder.Rejected] with `folder_exists` when
     * the name is taken.
     */
    suspend fun createFolder(name: String): NewFolder? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return try {
            NewFolder.Created(writeLock.withLock { saved.createFolder(trimmed) })
        } catch (e: IllegalArgumentException) {
            // The repository rejects only duplicate names (trimmed, case-insensitive).
            NewFolder.Rejected(UiText(R.string.folder_exists, listOf(trimmed)))
        }
    }

    private suspend fun savedRow(entryId: Long): SavedEntry? =
        saved.observeAll().first().firstOrNull { it.entryId == entryId }

    /** Saves [entry] in the last-used folder while it exists, otherwise in no folder; returns that folder. */
    private suspend fun saveToLastUsed(entry: EntrySummary): Folder? {
        val lastUsed = prefs.snapshot().lastUsedFolderId
        val folder = lastUsed?.let { id -> saved.observeFolders().first().firstOrNull { it.id == id } }
        saved.save(entry, folder?.id)
        return folder
    }

    private suspend fun folderName(folderId: Long?): String? =
        folderId?.let { id -> saved.observeFolders().first().firstOrNull { it.id == id }?.name }

    /** Runs [block] unless an action on [entryId] is already running; then it returns null at once. */
    private inline fun <T> singleFlight(entryId: Long, block: () -> T?): T? {
        if (!synchronized(busy) { busy.add(entryId) }) return null
        try {
            return block()
        } finally {
            synchronized(busy) { busy.remove(entryId) }
        }
    }

    companion object {
        /** "Saved {headword} to {folder}", or "Saved {headword}" without a folder. */
        fun savedMessage(headword: String, folderName: String?): UiText =
            if (folderName != null) {
                UiText(R.string.saved_to_folder, listOf(headword, folderName))
            } else {
                UiText(R.string.saved_simple, listOf(headword))
            }
    }
}
