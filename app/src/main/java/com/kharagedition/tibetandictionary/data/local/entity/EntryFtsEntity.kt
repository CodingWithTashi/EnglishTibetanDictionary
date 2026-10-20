package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Full-text index over `entries` (external content; `rowid` = `entries.id`). One token per Tibetan syllable, see
 * [TibetanTokenChars]. Query it from the index side with
 * `FROM entries_fts CROSS JOIN entries ON entries.id = entries_fts.rowid WHERE entries_fts MATCH …`: `CROSS JOIN` keeps
 * the full-text index the outer loop, so the MATCH runs once instead of once per entry (see `EntryDao.ftsHeadword`).
 *
 * `matchinfo=fts3`: nothing ranks by `matchinfo()`, so the index keeps no `entries_fts_docsize` table.
 */
@Fts4(
    contentEntity = EntryEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = [TibetanTokenChars.ARG],
    matchInfo = FtsOptions.MatchInfo.FTS3,
)
@Entity(tableName = "entries_fts")
data class EntryFtsEntity(
    @ColumnInfo(name = "headword") val headword: String,
    @ColumnInfo(name = "wylie") val wylie: String?,
)
