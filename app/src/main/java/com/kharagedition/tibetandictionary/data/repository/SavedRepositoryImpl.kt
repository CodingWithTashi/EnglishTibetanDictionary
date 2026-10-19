package com.kharagedition.tibetandictionary.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [SavedRepository] over the user.db DAOs. Stateless: every read is a Room flow, every write a short transaction. */
@Singleton
class SavedRepositoryImpl @Inject constructor(
    private val db: UserDatabase,
    private val clock: AppClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SavedRepository {
    private val savedDao = db.savedEntryDao()
    private val folderDao = db.folderDao()

    override fun observeAll(): Flow<List<SavedEntry>> = savedDao.observeAll().mapToDomain()

    override fun observeFolder(folderId: Long?): Flow<List<SavedEntry>> =
        (if (folderId == null) savedDao.observeUnfiled() else savedDao.observeInFolder(folderId)).mapToDomain()

    override fun search(query: String): Flow<List<SavedEntry>> = savedDao.search(query).mapToDomain()

    override fun observeIsSaved(entryId: Long): Flow<Boolean> =
        savedDao.observeIsSaved(entryId).distinctUntilChanged().flowOn(io)

    override fun observeSavedIds(): Flow<Set<Long>> =
        savedDao.observeIds().map { it.toSet() }.distinctUntilChanged().flowOn(io)

    override fun observeCount(): Flow<Int> = savedDao.observeCount().distinctUntilChanged().flowOn(io)

    /**
     * Saves a snapshot of [entry]. Saving an entry that is already saved refreshes the snapshot, folder and times
     * but keeps its note; the old row is replaced in one transaction, so observers never see it unsaved.
     */
    override suspend fun save(entry: EntrySummary, folderId: Long?) {
        withContext(io) {
            val now = clock.now().toEpochMilli()
            db.withTransaction {
                val existing = savedDao.get(entry.id)
                if (existing != null) savedDao.delete(entry.id)
                savedDao.insert(entry.toSavedEntity(now, folderId).copy(note = existing?.note))
            }
        }
    }

    override suspend fun unsave(entryId: Long) {
        withContext(io) { savedDao.delete(entryId) }
    }

    override suspend fun setFolder(entryId: Long, folderId: Long?) {
        withContext(io) { savedDao.setFolder(entryId, folderId, clock.now().toEpochMilli()) }
    }

    override fun observeFolders(): Flow<List<Folder>> =
        combine(folderDao.observeAll(), folderDao.observeCounts()) { folders, counts ->
            val countByFolder = counts.associate { it.folderId to it.count }
            folders.map { it.toDomain(count = countByFolder[it.id] ?: 0) }
        }.distinctUntilChanged().flowOn(io)

    override suspend fun createFolder(name: String): Long {
        val trimmed = validFolderName(name)
        return withContext(io) {
            try {
                db.withTransaction {
                    requireNameFree(trimmed, exceptId = null)
                    val now = clock.now().toEpochMilli()
                    folderDao.insert(
                        FolderEntity(
                            name = trimmed,
                            position = folderDao.nextPosition(),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            } catch (e: SQLiteConstraintException) {
                throw IllegalArgumentException(duplicateMessage(trimmed), e)
            }
        }
    }

    override suspend fun renameFolder(id: Long, name: String) {
        val trimmed = validFolderName(name)
        withContext(io) {
            try {
                db.withTransaction {
                    requireNameFree(trimmed, exceptId = id)
                    folderDao.rename(id, trimmed, clock.now().toEpochMilli())
                }
            } catch (e: SQLiteConstraintException) {
                throw IllegalArgumentException(duplicateMessage(trimmed), e)
            }
        }
    }

    override suspend fun deleteFolder(id: Long) {
        withContext(io) { folderDao.delete(id) }
    }

    private fun Flow<List<SavedEntryEntity>>.mapToDomain(): Flow<List<SavedEntry>> =
        map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    private fun validFolderName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "A folder name cannot be blank" } }

    private suspend fun requireNameFree(trimmedName: String, exceptId: Long?) {
        require(!folderDao.existsByNameIgnoreCase(trimmedName, exceptId)) { duplicateMessage(trimmedName) }
    }

    private fun duplicateMessage(name: String) = "A folder named \"$name\" already exists"
}
