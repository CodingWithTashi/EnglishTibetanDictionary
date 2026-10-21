package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recent search (user.db): one row per pack, query and direction, trimmed to the newest 50 per pack. [entryId]
 * and [headword] are set once the user opened a result from it. [searchedAt] is epoch milliseconds.
 */
@Entity(
    tableName = "recent_searches",
    indices = [
        Index(value = ["pack_id", "query", "direction_id"], unique = true),
        Index(value = ["pack_id", "searched_at"]),
    ],
)
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The [com.kharagedition.tibetandictionary.data.model.DictionaryPack] id. */
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "direction_id") val directionId: String,
    @ColumnInfo(name = "entry_id") val entryId: Long?,
    @ColumnInfo(name = "headword") val headword: String?,
    @ColumnInfo(name = "searched_at") val searchedAt: Long,
)
