package com.kharagedition.tibetandictionary.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import java.io.File
import java.nio.ByteBuffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the shipped asset `src/main/assets/dictionary/dictionary.db` and the files that must agree with it: its
 * `PRAGMA user_version` (header bytes 60–63, big-endian) is [DictionaryDatabase.VERSION], the highest exported
 * schema has that version, and `res/values/dictionary_stats.xml` holds the asset's total entry count. When one fails,
 * rebuild the asset and the stats file together (tools/dictionary/README.md, "Shipping a data release").
 *
 * The files are read as they are in the repository, without Room. The entry total needs SQL and the platform SQLite
 * is the only one on the unit-test classpath, so the class runs under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryAssetVersionTest {

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
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        val total = SQLiteDatabase.openDatabase(assetFile().path, null, flags).use { db ->
            db.rawQuery("SELECT SUM(entry_count) FROM directions", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }

        assertThat(total).isGreaterThan(0)
        assertThat(context.resources.getInteger(R.integer.dictionary_entry_count)).isEqualTo(total)
    }

    private fun assetFile(): File = projectFile("src/main/assets/${DictionaryDatabase.ASSET_PATH}")

    /**
     * [path] relative to the app module, looked up from the working directory and its parents: Gradle runs unit tests
     * in the module directory, an IDE run configuration may use the project root.
     */
    private fun projectFile(path: String): File {
        val workingDir = File("").absoluteFile
        val file = generateSequence(workingDir) { it.parentFile }
            .flatMap { dir -> sequenceOf(File(dir, path), File(dir, "app/$path")) }
            .firstOrNull { it.exists() }
        return checkNotNull(file) { "No $path in $workingDir or its parents" }
    }

    private companion object {
        const val HEADER_SIZE = 100
        const val SQLITE_MAGIC = "SQLite format 3"
        const val USER_VERSION_OFFSET = 60
    }
}
