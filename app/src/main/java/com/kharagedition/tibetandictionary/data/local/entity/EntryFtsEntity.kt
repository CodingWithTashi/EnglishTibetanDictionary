package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Full-text index over `entries` (external content; `rowid` = `entries.id`). One token per Tibetan syllable, see
 * [TibetanTokenChars]. Query it with `JOIN entries_fts ON entries_fts.rowid = entries.id WHERE entries_fts MATCH …`.
 */
@Fts4(
    contentEntity = EntryEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = [TibetanTokenChars.ARG],
)
@Entity(tableName = "entries_fts")
data class EntryFtsEntity(
    @ColumnInfo(name = "headword") val headword: String,
    @ColumnInfo(name = "wylie") val wylie: String?,
)
