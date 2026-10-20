package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A see-also or synonym link. [targetEntryId] is null when unresolved (a chip tap then runs a search). */
@Entity(
    tableName = "related_entries",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_entry_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["source_entry_id"]), Index(value = ["target_entry_id"])],
)
data class RelatedEntryEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "source_entry_id") val sourceEntryId: Long,
    @ColumnInfo(name = "target_entry_id") val targetEntryId: Long?,
    @ColumnInfo(name = "target_headword") val targetHeadword: String,
    @ColumnInfo(name = "target_wylie") val targetWylie: String?,
    /** `see_also` or `synonym`. */
    @ColumnInfo(name = "relation") val relation: String,
    @ColumnInfo(name = "position") val position: Int,
)
