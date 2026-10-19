package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import kotlinx.coroutines.flow.Flow

/** Saved words and their folders (user.db). */
interface SavedRepository {
    fun observeAll(): Flow<List<SavedEntry>>

    /** Saved entries in [folderId]; null means entries with no folder (unfiled). "All" uses [observeAll]. */
    fun observeFolder(folderId: Long?): Flow<List<SavedEntry>>

    /** Saved entries whose headword, Wylie or gloss matches [query]. */
    fun search(query: String): Flow<List<SavedEntry>>

    fun observeIsSaved(entryId: Long): Flow<Boolean>

    fun observeSavedIds(): Flow<Set<Long>>

    fun observeCount(): Flow<Int>

    suspend fun save(entry: EntrySummary, folderId: Long?)

    suspend fun unsave(entryId: Long)

    suspend fun setFolder(entryId: Long, folderId: Long?)

    fun observeFolders(): Flow<List<Folder>>

    /**
     * Creates a folder and returns its id.
     * @throws IllegalArgumentException when a folder with the same name (trimmed, case-insensitive) exists.
     */
    suspend fun createFolder(name: String): Long

    /** @throws IllegalArgumentException when another folder has the same name (trimmed, case-insensitive). */
    suspend fun renameFolder(id: Long, name: String)

    /** Deletes the folder; its entries stay saved with no folder. */
    suspend fun deleteFolder(id: Long)
}
