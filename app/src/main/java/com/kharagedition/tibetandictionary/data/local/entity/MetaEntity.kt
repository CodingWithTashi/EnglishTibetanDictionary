package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Build metadata as key/value pairs. Keys: `schema_version`, `data_version`, `built_at`, `source_name`,
 * `source_author`, `source_url`, `source_license`, `attribution_text`, `wylie_converter`, `wotd_seed`, `wotd_count`,
 * `wotd_direction_id`, `build_tool_version`, `row_counts_json`.
 */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String?,
)
