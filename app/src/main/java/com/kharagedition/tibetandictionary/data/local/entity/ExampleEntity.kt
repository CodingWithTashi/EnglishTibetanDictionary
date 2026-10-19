package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A usage example under a sense; [entryId] is denormalised for per-entry lookups. */
@Entity(
    tableName = "examples",
    foreignKeys = [
        ForeignKey(
            entity = SenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sense_id"]), Index(value = ["entry_id"])],
)
data class ExampleEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "sense_id") val senseId: Long,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "translation") val translation: String?,
    @ColumnInfo(name = "audio_file") val audioFile: String?,
)
