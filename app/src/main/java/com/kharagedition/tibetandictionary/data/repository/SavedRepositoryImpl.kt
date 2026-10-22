package com.kharagedition.tibetandictionary.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.search.QueryNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [SavedRepository] over the user.db DAOs. Stateless: every read is a Room flow, every write a short transaction.
 * Everything is scoped to the active dictionary pack: reads re-query when the pack changes (and stay silent until one
 * is active), writes go to the pack active when they run.
 */
@Singleton
class SavedRepositoryImpl @Inject constructor(
    private val db: UserDatabase,
    private val databases: DictionaryDatabases,
    private val clock: AppClock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : SavedRepository {
    private val savedDao = db.savedEntryDao()
    private val folderDao = db.folderDao()

    /** The active pack's id; a write before the first activation is a programming error. */
    private val packId: String get() = checkNotNull(databases.active.value) { "No active dictionary" }.id

    /** [block] on the active pack's id, re-run on every pack switch; nothing is emitted while no pack is active. */
    private fun <T> perPack(block: (String) -> Flow<T>): Flow<T> =
        databases.active.filterNotNull().flatMapLatest { block(it.id) }

    override fun observeAll(): Flow<List<SavedEntry>> = perPack { savedDao.observeAll(it) }.mapToDomain()

    override fun observeFolder(folderId: Long?): Flow<List<SavedEntry>> =
        perPack { pack ->
            if (folderId == null) savedDao.observeUnfiled(pack) else savedDao.observeInFolder(pack, folderId)
        }.mapToDomain()

    /**
     * Matches [query] anywhere in the headword, Wylie or gloss preview. The query is normalised as the dictionary
     * search normalises it ([QueryNormalizer]: zero-width characters, non-breaking tsheg, typographic apostrophes,
     * spaces, a trailing shad) and loses a trailing tsheg, so "བཀྲ་ཤིས།" finds the stored "བཀྲ་ཤིས་" and "ཁང་པ་" a
     * gloss "ཁང་པ།". Case-insensitivity is ASCII-only (SQLite `LIKE`); full Unicode case folding is not attempted.
     * `%` and `_` in [query] match themselves.
     */
    override fun search(query: String): Flow<List<SavedEntry>> =
        TibetanScript.stripTrailingTsheg(QueryNormalizer.normalize(query)).let { normalised ->
            perPack { savedDao.search(it, normalised) }.mapToDomain()
        }

    /** Entry ids are unique across packs, so this is keyed by id alone. */
    override fun observeIsSaved(entryId: Long): Flow<Boolean> =
        savedDao.observeIsSaved(entryId).distinctUntilChanged().flowOn(io)

    override fun observeSavedIds(): Flow<Set<Long>> =
        perPack { savedDao.observeIds(it) }.map { it.toSet() }.distinctUntilChanged().flowOn(io)

    override fun observeCount(): Flow<Int> =
        perPack { savedDao.observeCount(it) }.distinctUntilChanged().flowOn(io)

    /**
     * Saves a snapshot of [entry]. Saving an entry that is already saved refreshes the snapshot, folder and times
     * but keeps its note; the old row is replaced in one transaction, so observers never see it unsaved. A
     * [folderId] that no longer exists in the active pack (a stale "last used folder", or a folder of the other
     * pack) saves the entry with no folder.
     */
    override suspend fun save(entry: EntrySummary, folderId: Long?) {
        withContext(io) {
            val now = clock.now().toEpochMilli()
            val pack = packId
            db.withTransaction {
                val existing = savedDao.get(entry.id)
                if (existing != null) savedDao.delete(entry.id)
                savedDao.insert(
                    entry.toSavedEntity(
                        packId = pack,
                        now = now,
                        folderId = existingFolderOrNull(pack, folderId),
                        note = existing?.note,
                    ),
                )
            }
        }
    }

    /** Entry ids are unique across packs, so this is keyed by id alone. */
    override suspend fun unsave(entryId: Long) {
        withContext(io) { savedDao.delete(entryId) }
    }

    /**
     * Puts [entry] back as it was before [unsave] (Undo): the same snapshot, note, folder, savedAt and updatedAt, so it
     * returns to its place in every newest-first list. A row saved again in the meantime is replaced. A folder deleted
     * in the meantime leaves the entry with no folder, as in [save]. The row returns to the pack it was saved in
     * ([SavedEntry.packId]), whichever pack is active now.
     */
    override suspend fun restore(entry: SavedEntry) {
        withContext(io) {
            db.withTransaction {
                savedDao.delete(entry.entryId)
                savedDao.insert(entry.toEntity(folderId = existingFolderOrNull(entry.packId, entry.folderId)))
            }
        }
    }

    /**
     * Moves the entry to [folderId]; a folder that no longer exists, or belongs to another pack than the entry's own
     * (not necessarily the active one), leaves the entry with no folder.
     */
    override suspend fun setFolder(entryId: Long, folderId: Long?) {
        withContext(io) {
            db.withTransaction {
                val entryPack = savedDao.get(entryId)?.packId ?: return@withTransaction
                savedDao.setFolder(entryId, existingFolderOrNull(entryPack, folderId), clock.now().toEpochMilli())
            }
        }
    }

    override fun observeFolders(): Flow<List<Folder>> =
        perPack { pack ->
            combine(folderDao.observeAll(pack), folderDao.observeCounts(pack)) { folders, counts ->
                val countByFolder = counts.associate { it.folderId to it.count }
                folders.map { it.toDomain(count = countByFolder[it.id] ?: 0) }
            }
        }.distinctUntilChanged().flowOn(io)

    /**
     * Creates a folder named [name] (trimmed) after the highest position. Duplicates are detected with ASCII-only case
     * folding (SQLite `NOCASE`), so "Verbs" and "verbs" clash but "É" and "é" do not.
     */
    override suspend fun createFolder(name: String): Long {
        val trimmed = validFolderName(name)
        return withContext(io) {
            try {
                db.withTransaction {
                    val pack = packId
                    requireNameFree(pack, trimmed, exceptId = null)
                    val now = clock.now().toEpochMilli()
                    folderDao.insert(
                        FolderEntity(
                            packId = pack,
                            name = trimmed,
                            position = folderDao.nextPosition(pack),
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

    /** Renames the folder to [name] (trimmed); the duplicate check is ASCII-case-insensitive, like [createFolder]. */
    override suspend fun renameFolder(id: Long, name: String) {
        val trimmed = validFolderName(name)
        withContext(io) {
            try {
                db.withTransaction {
                    requireNameFree(packId, trimmed, exceptId = id)
                    folderDao.rename(id, trimmed, clock.now().toEpochMilli())
                }
            } catch (e: SQLiteConstraintException) {
                throw IllegalArgumentException(duplicateMessage(trimmed), e)
            }
        }
    }

    /** Folder ids are unique across packs, so this is keyed by id alone. */
    override suspend fun deleteFolder(id: Long) {
        withContext(io) { folderDao.delete(id) }
    }

    private fun Flow<List<SavedEntryEntity>>.mapToDomain(): Flow<List<SavedEntry>> =
        map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    /** [folderId] if that folder exists in [pack], else null (unfiled), so a stale id cannot break the foreign key. */
    private suspend fun existingFolderOrNull(pack: String, folderId: Long?): Long? =
        folderId?.takeIf { folderDao.exists(it, pack) }

    private fun validFolderName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "A folder name cannot be blank" } }

    private suspend fun requireNameFree(pack: String, trimmedName: String, exceptId: Long?) {
        require(!folderDao.existsByNameIgnoreCase(pack, trimmedName, exceptId)) {
            duplicateMessage(trimmedName)
        }
    }

    private fun duplicateMessage(name: String) = "A folder named \"$name\" already exists"
}
