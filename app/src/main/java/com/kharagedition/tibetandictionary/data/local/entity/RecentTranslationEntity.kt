package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recent translation (user.db): one row per pack, direction and source text, trimmed to the newest 20 per pack.
 * [usedAt] is epoch milliseconds. Kept on the device only.
 */
@Entity(
    tableName = "recent_translations",
    indices = [
        Index(value = ["pack_id", "source_lang", "target_lang", "source_text"], unique = true),
        Index(value = ["pack_id", "used_at"]),
    ],
)
data class RecentTranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The [com.kharagedition.tibetandictionary.data.model.DictionaryPack] id. */
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "source_lang") val sourceLang: String,
    @ColumnInfo(name = "target_lang") val targetLang: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
    @ColumnInfo(name = "translation") val translation: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "used_at") val usedAt: Long,
)
