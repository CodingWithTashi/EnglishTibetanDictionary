package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A headword in one direction. [id] = `directions.id_base + source_id`, stable across data releases.
 * [headword] and [wylie] compare case-insensitively (NOCASE), which the prefix range scans rely on.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(entity = DirectionEntity::class, parentColumns = ["id"], childColumns = ["direction_id"]),
    ],
    indices = [
        Index(value = ["direction_id", "headword"], unique = true),
        Index(value = ["direction_id", "wylie"]),
        Index(value = ["direction_id", "sort_order"]),
        Index(value = ["wotd_rank"]),
    ],
)
data class EntryEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "direction_id") val directionId: String,
    @ColumnInfo(name = "source_id") val sourceId: Int,
    /** Tibetan headwords end with a tsheg. */
    @ColumnInfo(name = "headword", collate = ColumnInfo.NOCASE) val headword: String,
    @ColumnInfo(name = "wylie", collate = ColumnInfo.NOCASE) val wylie: String?,
    /** At most 120 characters. */
    @ColumnInfo(name = "gloss_preview") val glossPreview: String,
    /** Comma-joined part-of-speech keys, e.g. `n,vt`. */
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String?,
    /** Romanised pronunciation slot (e.g. "tashi"); no source yet. */
    @ColumnInfo(name = "phonetic") val phonetic: String?,
    @ColumnInfo(name = "audio_file") val audioFile: String?,
    @ColumnInfo(name = "sense_count") val senseCount: Int,
    @ColumnInfo(name = "is_truncated") val isTruncated: Boolean,
    /** Monlam (Tibetan collation) order; equals [sourceId]. */
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Position in the seeded word-of-the-day shuffle; null when not eligible. */
    @ColumnInfo(name = "wotd_rank") val wotdRank: Int?,
)
