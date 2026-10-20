package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Full-text index over `senses.gloss` (external content; `rowid` = `senses.id`), used for reverse lookup.
 * `matchinfo=fts3`: nothing ranks by `matchinfo()`, so the index keeps no `senses_fts_docsize` table.
 */
@Fts4(
    contentEntity = SenseEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = [TibetanTokenChars.ARG],
    matchInfo = FtsOptions.MatchInfo.FTS3,
)
@Entity(tableName = "senses_fts")
data class SenseFtsEntity(
    @ColumnInfo(name = "gloss") val gloss: String,
)
