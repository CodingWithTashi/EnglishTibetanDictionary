package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stub so the Hilt graph compiles; L1 (T1.7) replaces the bodies. */
@Singleton
class SavedRepositoryImpl @Inject constructor() : SavedRepository {
    override fun observeAll(): Flow<List<SavedEntry>> = TODO("L1")

    override fun observeFolder(folderId: Long?): Flow<List<SavedEntry>> = TODO("L1")

    override fun search(query: String): Flow<List<SavedEntry>> = TODO("L1")

    override fun observeIsSaved(entryId: Long): Flow<Boolean> = TODO("L1")

    override fun observeSavedIds(): Flow<Set<Long>> = TODO("L1")

    override fun observeCount(): Flow<Int> = TODO("L1")

    override suspend fun save(entry: EntrySummary, folderId: Long?): Unit = TODO("L1")

    override suspend fun unsave(entryId: Long): Unit = TODO("L1")

    override suspend fun setFolder(entryId: Long, folderId: Long?): Unit = TODO("L1")

    override fun observeFolders(): Flow<List<Folder>> = TODO("L1")

    override suspend fun createFolder(name: String): Long = TODO("L1")

    override suspend fun renameFolder(id: Long, name: String): Unit = TODO("L1")

    override suspend fun deleteFolder(id: Long): Unit = TODO("L1")
}
