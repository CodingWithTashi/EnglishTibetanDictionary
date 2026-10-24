package com.kharagedition.tibetandictionary.data.local

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.entity.TibetanTokenChars
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** The exported schema JSON is the DDL the Python pipeline executes; pin what it must contain. */
class SchemaExportTest {

    @Test
    fun dictionarySchemaMatchesVersionAndKeepsTheTibetanTokenizer() {
        val database = schema(DictionaryDatabase::class.java.name, DictionaryDatabase.VERSION)
        val tables = database.tables()

        assertThat(database["version"]!!.jsonPrimitive.int).isEqualTo(DictionaryDatabase.VERSION)
        assertThat(tables.keys).containsExactly(
            "directions", "entries", "senses", "examples", "related_entries", "meta", "entries_fts", "senses_fts",
        )
        for ((fts, content) in listOf("entries_fts" to "entries", "senses_fts" to "senses")) {
            val table = tables.getValue(fts)
            val createSql = table["createSql"]!!.jsonPrimitive.content
            assertThat(createSql).contains("USING FTS4(")
            assertThat(createSql).contains("tokenize=unicode61 `${TibetanTokenChars.ARG}`")
            assertThat(createSql).contains("content=`$content`")
            assertThat(table["contentSyncTriggers"]!!.jsonArray).isNotEmpty()
        }
    }

    @Test
    fun userSchemaIsExported() {
        val database = schema(UserDatabase::class.java.name, UserDatabase.VERSION)

        assertThat(database["version"]!!.jsonPrimitive.int).isEqualTo(UserDatabase.VERSION)
        assertThat(database.tables().keys).containsExactly(
            "folders", "saved_entries", "recent_searches", "activity_days", "recent_translations", "saved_translations",
        )
    }

    /**
     * `app/schemas/<databaseClass>/<version>.json`, looked up from the working directory and its parents: Gradle runs
     * unit tests in the module directory, an IDE run configuration may use the project root.
     */
    private fun schema(databaseClass: String, version: Int): JsonObject {
        val workingDir = File("").absoluteFile
        val file = generateSequence(workingDir) { it.parentFile }
            .flatMap { dir -> sequenceOf(File(dir, "schemas"), File(dir, "app/schemas")) }
            .map { schemas -> File(schemas, "$databaseClass/$version.json") }
            .firstOrNull { it.isFile }
        checkNotNull(file) { "No schemas/$databaseClass/$version.json in $workingDir or its parents" }
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun JsonObject.tables(): Map<String, JsonObject> =
        this["entities"]!!.jsonArray.associate { e -> e.jsonObject["tableName"]!!.jsonPrimitive.content to e.jsonObject }
}
