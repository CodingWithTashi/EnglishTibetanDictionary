package com.kharagedition.tibetandictionary.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Hand-written [UserDatabase] migrations. */
object UserDatabaseMigrations {

    /**
     * 1 → 2: folders, saved words and recent searches get a `pack_id`, and every existing row becomes the Tibetan
     * pack's (`bo`). Folder names become unique per pack and recent searches one row per (pack, query, direction);
     * saved words and recent searches gain a (pack, time) index for their newest-first lists. `activity_days` stays
     * shared and is not touched. The new indices are created exactly as in `schemas/…UserDatabase/2.json`.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `pack_id` TEXT NOT NULL DEFAULT 'bo'")
            db.execSQL("ALTER TABLE `saved_entries` ADD COLUMN `pack_id` TEXT NOT NULL DEFAULT 'bo'")
            db.execSQL("ALTER TABLE `recent_searches` ADD COLUMN `pack_id` TEXT NOT NULL DEFAULT 'bo'")
            db.execSQL("DROP INDEX `index_folders_name`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_pack_id_name` ON `folders` (`pack_id`, `name`)",
            )
            db.execSQL("DROP INDEX `index_recent_searches_query_direction_id`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_recent_searches_pack_id_query_direction_id` " +
                    "ON `recent_searches` (`pack_id`, `query`, `direction_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_saved_entries_pack_id_saved_at` " +
                    "ON `saved_entries` (`pack_id`, `saved_at`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recent_searches_pack_id_searched_at` " +
                    "ON `recent_searches` (`pack_id`, `searched_at`)",
            )
        }
    }
}
