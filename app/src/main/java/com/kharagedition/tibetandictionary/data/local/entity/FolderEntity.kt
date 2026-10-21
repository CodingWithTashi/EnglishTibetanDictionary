package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user folder of saved words (user.db) in one dictionary pack; names are unique per pack. Times are epoch
 * milliseconds.
 */
@Entity(tableName = "folders", indices = [Index(value = ["pack_id", "name"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The [com.kharagedition.tibetandictionary.data.model.DictionaryPack] id. */
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
