package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.local.entity.FolderEntity
import com.kharagedition.tibetandictionary.data.local.entity.RecentSearchEntity
import com.kharagedition.tibetandictionary.data.local.entity.SavedEntryEntity
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Folder
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import com.kharagedition.tibetandictionary.data.model.SavedEntry

internal fun SavedEntryEntity.toDomain(): SavedEntry = SavedEntry(
    entryId = entryId,
    directionId = directionId,
    headword = headword,
    wylie = wylie,
    glossPreview = glossPreview,
    folderId = folderId,
    note = note,
    savedAt = savedAt,
    updatedAt = updatedAt,
)

/** The row [this] was read from, filed in [folderId]. */
internal fun SavedEntry.toEntity(folderId: Long? = this.folderId): SavedEntryEntity = SavedEntryEntity(
    entryId = entryId,
    directionId = directionId,
    headword = headword,
    wylie = wylie,
    glossPreview = glossPreview,
    folderId = folderId,
    note = note,
    savedAt = savedAt,
    updatedAt = updatedAt,
)

/** Snapshots [this] for user.db at [now] (epoch milliseconds); [note] is carried over when re-saving. */
internal fun EntrySummary.toSavedEntity(now: Long, folderId: Long?, note: String? = null): SavedEntryEntity = SavedEntryEntity(
    entryId = id,
    directionId = directionId,
    headword = headword,
    wylie = wylie,
    glossPreview = glossPreview,
    folderId = folderId,
    note = note,
    savedAt = now,
    updatedAt = now,
)

internal fun FolderEntity.toDomain(count: Int): Folder = Folder(id = id, name = name, position = position, count = count)

internal fun RecentSearchEntity.toDomain(): RecentSearch = RecentSearch(
    id = id,
    query = query,
    directionId = directionId,
    entryId = entryId,
    headword = headword,
    searchedAt = searchedAt,
)
