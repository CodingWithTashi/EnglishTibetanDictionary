package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One gloss of an entry: sense [senseNo] (1-based, continuous per entry), optionally sub-sense [subNo]. */
@Entity(
    tableName = "senses",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entry_id", "sense_no", "sub_no"])],
)
data class SenseEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "sense_no") val senseNo: Int,
    /** ཀཿ ཁཿ … or 1> 2> as 1, 2, …; null for a sense without sub-senses. */
    @ColumnInfo(name = "sub_no") val subNo: Int?,
    /** One key: n, adj, adv, v, vt, vi, interj, prep, pron, conj, num, part, aux, prefix, suffix, abbr, comb, pred, pl. */
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String?,
    /** Canonical label keys, comma-joined. */
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "gloss") val gloss: String,
    /** `bo` or `en`. */
    @ColumnInfo(name = "gloss_lang") val glossLang: String,
    @ColumnInfo(name = "note") val note: String?,
    /** Source citation tags, `;`-joined (e.g. `C;TGP-77`). */
    @ColumnInfo(name = "source_tag") val sourceTag: String?,
    @ColumnInfo(name = "is_truncated") val isTruncated: Boolean,
    @ColumnInfo(name = "source_id") val sourceId: Int,
)
