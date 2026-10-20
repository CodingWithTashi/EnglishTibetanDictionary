package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved word (user.db). Headword, Wylie and gloss are snapshots taken at save time, so Saved never joins across
 * databases. Deleting the folder leaves the entry saved with no folder. Times are epoch milliseconds.
 */
@Entity(
    tableName = "saved_entries",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["folder_id"]), Index(value = ["saved_at"])],
)
data class SavedEntryEntity(
    @PrimaryKey @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "direction_id") val directionId: String,
    @ColumnInfo(name = "headword") val headword: String,
    @ColumnInfo(name = "wylie") val wylie: String?,
    @ColumnInfo(name = "gloss_preview") val glossPreview: String,
    @ColumnInfo(name = "folder_id") val folderId: Long?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
