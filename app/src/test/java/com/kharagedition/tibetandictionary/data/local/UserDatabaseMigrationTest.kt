package com.kharagedition.tibetandictionary.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** user.db migrations, run on files created from the exported schemas (`app/schemas`, exposed as debug assets). */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UserDatabase::class.java)

    @Test
    fun migrate1To2KeepsRowsAndTagsThemTibetan() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO folders (id, name, position, created_at, updated_at) VALUES (1, 'Verbs', 0, 10, 10)",
            )
            db.execSQL(
                """
                INSERT INTO saved_entries
                    (entry_id, direction_id, headword, wylie, gloss_preview, folder_id, note, saved_at, updated_at)
                VALUES (1000001, 'bo-en', 'བཀྲ་ཤིས་', 'bkra shis', 'auspicious', 1, 'greeting', 20, 20)
                """,
            )
            db.execSQL(
                "INSERT INTO recent_searches (id, query, direction_id, entry_id, headword, searched_at) " +
                    "VALUES (1, 'bkra', 'bo-en', 1000001, 'བཀྲ་ཤིས་', 30)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, UserDatabaseMigrations.MIGRATION_1_2)

        assertThat(db.rows("SELECT id, name, pack_id FROM folders")).containsExactly(listOf("1", "Verbs", "bo"))
        assertThat(db.rows("SELECT entry_id, folder_id, note, pack_id FROM saved_entries"))
            .containsExactly(listOf("1000001", "1", "greeting", "bo"))
        assertThat(db.rows("SELECT id, query, direction_id, entry_id, pack_id FROM recent_searches"))
            .containsExactly(listOf("1", "bkra", "bo-en", "1000001", "bo"))
    }

    @Test
    fun migrate1To2AllowsSameFolderNameInOtherPack() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO folders (name, position, created_at, updated_at) VALUES ('Verbs', 0, 10, 10)")
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, UserDatabaseMigrations.MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO folders (pack_id, name, position, created_at, updated_at) VALUES ('dz', 'Verbs', 0, 20, 20)",
        )

        assertThat(db.rows("SELECT pack_id, name FROM folders ORDER BY pack_id"))
            .containsExactly(listOf("bo", "Verbs"), listOf("dz", "Verbs")).inOrder()
    }

    @Test
    fun migrate2To3KeepsSavedWordsAndCreatesTheTranslationTables() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO saved_entries (entry_id, pack_id, direction_id, headword, wylie, gloss_preview, folder_id,
                    note, saved_at, updated_at)
                VALUES (1000001, 'bo', 'bo-en', 'བཀྲ་ཤིས་', 'bkra shis', 'auspicious', NULL, NULL, 20, 20)
                """,
            )
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME,
            3,
            true,
            UserDatabaseMigrations.MIGRATION_1_2,
            UserDatabaseMigrations.MIGRATION_2_3,
        )

        assertThat(db.rows("SELECT entry_id, pack_id FROM saved_entries")).containsExactly(listOf("1000001", "bo"))
        db.execSQL(
            "INSERT INTO recent_translations (pack_id, source_lang, target_lang, source_text, translation, provider, " +
                "used_at) VALUES ('bo', 'bo', 'en', 'བཀྲ་ཤིས།', 'Tashi Delek', 'azure', 30)",
        )
        db.execSQL(
            "INSERT INTO saved_translations (pack_id, source_lang, target_lang, source_text, translation, provider, " +
                "saved_at) VALUES ('bo', 'en', 'bo', 'Thank you', 'ཐུགས་རྗེ་ཆེ།', 'google', 40)",
        )
        assertThat(db.rows("SELECT source_text, translation, used_at FROM recent_translations"))
            .containsExactly(listOf("བཀྲ་ཤིས།", "Tashi Delek", "30"))
        assertThat(db.rows("SELECT source_text, translation, saved_at FROM saved_translations"))
            .containsExactly(listOf("Thank you", "ཐུགས་རྗེ་ཆེ།", "40"))
    }

    /** Every row of [sql], each column read as text. */
    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
        }
    }

    private companion object {
        const val DB_NAME = "user-migration-test.db"
    }
}
