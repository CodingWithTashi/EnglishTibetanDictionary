package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recent search (user.db): one row per query and direction, trimmed to the newest 50. [entryId] and [headword]
 * are set once the user opened a result from it. [searchedAt] is epoch milliseconds.
 */
@Entity(tableName = "recent_searches", indices = [Index(value = ["query", "direction_id"], unique = true)])
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "direction_id") val directionId: String,
    @ColumnInfo(name = "entry_id") val entryId: Long?,
    @ColumnInfo(name = "headword") val headword: String?,
    @ColumnInfo(name = "searched_at") val searchedAt: Long,
)
