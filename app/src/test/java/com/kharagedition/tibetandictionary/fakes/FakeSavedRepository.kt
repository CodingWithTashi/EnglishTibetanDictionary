package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [SavedRepository]. [saved] is newest first; [folders] holds folder rows (their [Folder.count] is
 * recomputed from [saved] when observed). Deleting a folder keeps its entries with no folder, as user.db does.
 */
class FakeSavedRepository(
    initial: List<SavedEntry> = emptyList(),
    initialFolders: List<Folder> = emptyList(),
    private val clock: AppClock = FakeClock(),
) : SavedRepository {
    val saved = MutableStateFlow(initial)
    val folders = MutableStateFlow(initialFolders)
    private var nextFolderId = (initialFolders.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<SavedEntry>> = saved

    override fun observeFolder(folderId: Long?): Flow<List<SavedEntry>> =
        saved.map { list -> list.filter { it.folderId == folderId } }

    override fun search(query: String): Flow<List<SavedEntry>> = saved.map { list ->
        list.filter {
            it.headword.contains(query, ignoreCase = true) ||
                it.wylie?.contains(query, ignoreCase = true) == true ||
                it.glossPreview.contains(query, ignoreCase = true)
        }
    }

    override fun observeIsSaved(entryId: Long): Flow<Boolean> = saved.map { list -> list.any { it.entryId == entryId } }

    override fun observeSavedIds(): Flow<Set<Long>> = saved.map { list -> list.mapTo(mutableSetOf()) { it.entryId } }

    override fun observeCount(): Flow<Int> = saved.map { it.size }

    override suspend fun save(entry: EntrySummary, folderId: Long?) = saved.update { list ->
        val row = SavedEntry(
            entryId = entry.id,
            directionId = entry.directionId,
            headword = entry.headword,
            wylie = entry.wylie,
            glossPreview = entry.glossPreview,
            folderId = folderId,
            note = null,
            savedAt = clock.now().toEpochMilli(),
        )
        listOf(row) + list.filterNot { it.entryId == entry.id }
    }

    override suspend fun unsave(entryId: Long) = saved.update { list -> list.filterNot { it.entryId == entryId } }

    override suspend fun setFolder(entryId: Long, folderId: Long?) = saved.update { list ->
        list.map { if (it.entryId == entryId) it.copy(folderId = folderId) else it }
    }

    override fun observeFolders(): Flow<List<Folder>> = combine(folders, saved) { folderRows, savedRows ->
        folderRows.sortedBy { it.position }.map { folder -> folder.copy(count = savedRows.count { it.folderId == folder.id }) }
    }

    override suspend fun createFolder(name: String): Long {
        require(folders.value.none { it.name == name }) { "Folder \"$name\" already exists" }
        val id = nextFolderId++
        folders.update { it + Folder(id = id, name = name, position = it.size, count = 0) }
        return id
    }

    override suspend fun renameFolder(id: Long, name: String) = folders.update { list ->
        list.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteFolder(id: Long) {
        folders.update { list -> list.filterNot { it.id == id } }
        saved.update { list -> list.map { if (it.folderId == id) it.copy(folderId = null) else it } }
    }
}
