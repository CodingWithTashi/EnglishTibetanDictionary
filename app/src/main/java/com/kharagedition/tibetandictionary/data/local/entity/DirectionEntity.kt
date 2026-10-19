package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One dictionary direction (`bo-en`, `bo-bo`, `en-bo`, …) with its build-time counts. A count > 0 turns on the
 * matching feature; a direction with `entry_count = 0` is never shown.
 */
@Entity(tableName = "directions")
data class DirectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_lang") val sourceLang: String,
    @ColumnInfo(name = "target_lang") val targetLang: String,
    /** `tibt`, `latn` or `hani`. */
    @ColumnInfo(name = "source_script") val sourceScript: String,
    @ColumnInfo(name = "label_en") val labelEn: String,
    @ColumnInfo(name = "label_bo") val labelBo: String,
    /** Switcher label, e.g. "བོད→EN". */
    @ColumnInfo(name = "short_label") val shortLabel: String,
    /** 0–2 fixed switcher slots, ≥3 overflow ("More"). */
    @ColumnInfo(name = "position") val position: Int,
    /** Added to a source row id to form a stable entry id (1 000 000, 2 000 000, 3 000 000, …). */
    @ColumnInfo(name = "id_base") val idBase: Long,
    @ColumnInfo(name = "entry_count", defaultValue = "0") val entryCount: Int = 0,
    @ColumnInfo(name = "sense_count", defaultValue = "0") val senseCount: Int = 0,
    @ColumnInfo(name = "example_count", defaultValue = "0") val exampleCount: Int = 0,
    @ColumnInfo(name = "related_count", defaultValue = "0") val relatedCount: Int = 0,
    @ColumnInfo(name = "wylie_count", defaultValue = "0") val wylieCount: Int = 0,
    @ColumnInfo(name = "audio_count", defaultValue = "0") val audioCount: Int = 0,
    @ColumnInfo(name = "pos_count", defaultValue = "0") val posCount: Int = 0,
    @ColumnInfo(name = "label_count", defaultValue = "0") val labelCount: Int = 0,
    @ColumnInfo(name = "truncated_count", defaultValue = "0") val truncatedCount: Int = 0,
    @ColumnInfo(name = "wotd_count", defaultValue = "0") val wotdCount: Int = 0,
)
