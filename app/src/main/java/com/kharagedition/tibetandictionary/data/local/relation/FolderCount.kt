package com.kharagedition.tibetandictionary.data.local.relation

import androidx.room.ColumnInfo

/** Number of saved entries in one folder (folders with none are absent). */
data class FolderCount(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "count") val count: Int,
)
