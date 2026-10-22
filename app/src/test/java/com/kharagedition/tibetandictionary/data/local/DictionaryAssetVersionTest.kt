package com.kharagedition.tibetandictionary.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.resources
import java.io.File
import java.nio.ByteBuffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters

/**
 * Guards each shipped asset (`src/main/assets/dictionary/<pack>.db`, one run per [DictionaryPack]) and the files that
 * must agree with it: its `PRAGMA user_version` (header bytes 60–63, big-endian) is [DictionaryDatabase.VERSION], the
 * highest exported schema has that version, `res/values/dictionary_stats_<pack>.xml` holds the asset's total entry
 * count and its size in bytes (the installer's expected length, since the asset ships compressed), the build's
 * `wotd_direction_id` is the pack's default direction, and every direction belongs to the pack's language. When one
 * fails, rebuild the asset and the stats file together (tools/dictionary/README.md, "Shipping a data release").
 * A pack whose asset file is not in the checkout skips the other tests; `assetShipsInTheRepository` fails for it.
 *
 * The files are read as they are in the repository, without Room. The entry total needs SQL and the platform SQLite
 * is the only one on the unit-test classpath, so the class runs under Robolectric.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class DictionaryAssetVersionTest(private val pack: DictionaryPack) {

    /** Not skipped: a moved or missing asset must fail here instead of silently skipping every other test. */
    @Test
    fun assetShipsInTheRepository() {
        assertWithMessage("src/main/assets/${pack.assetPath}").that(pack.assetFileOrNull()).isNotNull()
    }

    @Test
    fun headerUserVersionIsTheDatabaseVersion() {
        val header = assetFile().inputStream().use { it.readNBytes(HEADER_SIZE) }

        assertThat(header.size).isEqualTo(HEADER_SIZE)
        assertThat(String(header, 0, SQLITE_MAGIC.length, Charsets.US_ASCII)).isEqualTo(SQLITE_MAGIC)
        assertThat(ByteBuffer.wrap(header, USER_VERSION_OFFSET, Int.SIZE_BYTES).int).isEqualTo(DictionaryDatabase.VERSION)
    }

    @Test
    fun databaseVersionIsTheHighestExportedSchema() {
        val schemas = projectFile("schemas/${DictionaryDatabase::class.java.name}")
        val versions = schemas.listFiles { file -> file.extension == "json" }.orEmpty().map { it.nameWithoutExtension.toInt() }
        val latest = Json.parseToJsonElement(File(schemas, "${versions.max()}.json").readText())
            .jsonObject["database"]!!.jsonObject["version"]!!.jsonPrimitive.int

        assertThat(versions.max()).isEqualTo(DictionaryDatabase.VERSION)
        assertThat(latest).isEqualTo(DictionaryDatabase.VERSION)
    }

    @Test
    fun statsResourceHoldsTheAssetsEntryTotal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val total = query("SELECT SUM(entry_count) FROM directions").single().getValue(0).toInt()

        assertThat(total).isGreaterThan(0)
        assertThat(context.resources.getInteger(pack.resources.entryCount)).isEqualTo(total)
    }

    @Test
    fun statsResourceHoldsTheAssetsSize() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(context.resources.getInteger(pack.resources.assetBytes).toLong()).isEqualTo(assetFile().length())
    }

    @Test
    fun wordOfTheDayDirectionIsThePacksDefaultDirection() {
        val wotd = query("SELECT value FROM meta WHERE key = 'wotd_direction_id'").single().getValue(0)

        assertThat(wotd).isEqualTo(pack.defaultDirectionId)
    }

    /** Every direction runs between the pack's language and English (or the pack's language and itself). */
    @Test
    fun everyDirectionBelongsToThePacksLanguage() {
        val directions = query("SELECT id, source_lang, target_lang FROM directions")

        assertThat(directions).isNotEmpty()
        for (row in directions) {
            val (id, source, target) = row.values
            assertWithMessage(id).that(pack.id).isAnyOf(source, target)
            assertWithMessage(id).that(id).isEqualTo("$source-$target")
        }
    }

    /** A row of a query as strings, in column order. */
    private class Row(val values: List<String>) {
        fun getValue(column: Int) = values[column]
    }

    private fun query(sql: String): List<Row> =
        SQLiteDatabase.openDatabase(
            assetFile().path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { db ->
            db.rawQuery(sql, null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(Row(List(cursor.columnCount) { cursor.getString(it) }))
                }
            }
        }

    /** The pack's asset; the test is skipped (not failed) when this checkout does not have it. */
    private fun assetFile(): File {
        val file = pack.assetFileOrNull()
        assumeTrue("No asset ${pack.assetPath} in this checkout", file != null)
        return checkNotNull(file)
    }

    /** [path] relative to the app module (see [findProjectFile]). */
    private fun projectFile(path: String): File =
        checkNotNull(findProjectFile(path)) { "No $path in ${File("").absoluteFile} or its parents" }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun packs(): List<Array<Any>> = packParameters()

        private const val HEADER_SIZE = 100
        private const val SQLITE_MAGIC = "SQLite format 3"
        private const val USER_VERSION_OFFSET = 60
    }
}
