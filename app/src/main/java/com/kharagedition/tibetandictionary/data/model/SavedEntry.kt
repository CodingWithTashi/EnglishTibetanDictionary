package com.kharagedition.tibetandictionary.data.model

/** A saved word. Fields are snapshots taken at save time, so Saved never joins across databases. */
data class SavedEntry(
    val entryId: Long,
    val directionId: String,
    val headword: String,
    val wylie: String?,
    val glossPreview: String,
    val folderId: Long?,
    val note: String?,
    /** Epoch milliseconds. */
    val savedAt: Long,
    /** When the row last changed (saved, re-saved or moved to a folder), epoch milliseconds. */
    val updatedAt: Long = savedAt,
)
