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

/**
 * In-memory [SavedRepository] with user.db semantics:
 * - [saved] rows may be seeded in any order; every list is observed newest first by [SavedEntry.savedAt].
 * - `observeFolder(null)` returns unfiled entries; re-saving keeps the entry's note.
 * - Folder names are unique (trimmed, case-insensitive); a new folder goes after the highest position.
 * - Deleting a folder keeps its entries, with no folder.
 */
class FakeSavedRepository(
    initial: List<SavedEntry> = emptyList(),
    initialFolders: List<Folder> = emptyList(),
    private val clock: AppClock = FakeClock(),
) : SavedRepository {
    val saved = MutableStateFlow(initial)
    val folders = MutableStateFlow(initialFolders)
    private var nextFolderId = (initialFolders.maxOfOrNull { it.id } ?: 0L) + 1

    private val newestFirst: Flow<List<SavedEntry>> = saved.map { list -> list.sortedByDescending { it.savedAt } }

    override fun observeAll(): Flow<List<SavedEntry>> = newestFirst

    override fun observeFolder(folderId: Long?): Flow<List<SavedEntry>> =
        newestFirst.map { list -> list.filter { it.folderId == folderId } }

    override fun search(query: String): Flow<List<SavedEntry>> = newestFirst.map { list ->
        list.filter {
            it.headword.contains(query, ignoreCase = true) ||
                it.wylie?.contains(query, ignoreCase = true) == true ||
                it.glossPreview.contains(query, ignoreCase = true)
        }
    }

    override fun observeIsSaved(entryId: Long): Flow<Boolean> = saved.map { list -> list.any { it.entryId == entryId } }

    override fun observeSavedIds(): Flow<Set<Long>> = saved.map { list -> list.mapTo(mutableSetOf()) { it.entryId } }

    override fun observeCount(): Flow<Int> = saved.map { it.size }

    override suspend fun save(entry: EntrySummary, folderId: Long?) {
        val existing = saved.value.firstOrNull { it.entryId == entry.id }
        val row = SavedEntry(
            entryId = entry.id,
            directionId = entry.directionId,
            headword = entry.headword,
            wylie = entry.wylie,
            glossPreview = entry.glossPreview,
            folderId = folderId,
            note = existing?.note,
            savedAt = clock.now().toEpochMilli(),
        )
        saved.value = listOf(row) + saved.value.filterNot { it.entryId == entry.id }
    }

    override suspend fun unsave(entryId: Long) {
        saved.value = saved.value.filterNot { it.entryId == entryId }
    }

    override suspend fun setFolder(entryId: Long, folderId: Long?) {
        saved.value = saved.value.map { if (it.entryId == entryId) it.copy(folderId = folderId) else it }
    }

    override fun observeFolders(): Flow<List<Folder>> = combine(folders, saved) { folderRows, savedRows ->
        folderRows.sortedBy { it.position }.map { folder -> folder.copy(count = savedRows.count { it.folderId == folder.id }) }
    }

    override suspend fun createFolder(name: String): Long {
        requireUniqueName(name, exceptId = null)
        val id = nextFolderId++
        val position = (folders.value.maxOfOrNull { it.position } ?: -1) + 1
        folders.value = folders.value + Folder(id = id, name = name, position = position, count = 0)
        return id
    }

    override suspend fun renameFolder(id: Long, name: String) {
        requireUniqueName(name, exceptId = id)
        folders.value = folders.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteFolder(id: Long) {
        folders.value = folders.value.filterNot { it.id == id }
        saved.value = saved.value.map { if (it.folderId == id) it.copy(folderId = null) else it }
    }

    private fun requireUniqueName(name: String, exceptId: Long?) {
        val wanted = name.trim()
        require(folders.value.none { it.id != exceptId && it.name.trim().equals(wanted, ignoreCase = true) }) {
            "A folder named \"$wanted\" already exists"
        }
    }
}
