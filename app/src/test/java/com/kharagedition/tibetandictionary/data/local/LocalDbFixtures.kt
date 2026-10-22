package com.kharagedition.tibetandictionary.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.ExampleEntity
import com.kharagedition.tibetandictionary.data.local.entity.RelatedEntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.SenseEntity
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import java.io.File

/** In-memory dictionary.db with the production schema (Room creates the tables, indices and FTS sync triggers). */
fun inMemoryDictionaryDb(): DictionaryDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DictionaryDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** A [DictionaryDatabases] whose `open` returns the given database for each listed pack; an unlisted pack fails. */
fun databasesOf(vararg packs: Pair<DictionaryPack, DictionaryDatabase>): DictionaryDatabases {
    val byPack = packs.toMap()
    return DictionaryDatabases { pack -> checkNotNull(byPack[pack]) { "No test database for $pack" } }
}

fun inMemoryUserDb(): UserDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UserDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun direction(id: String, position: Int, sourceScript: String = "tibt", entryCount: Int = 1): DirectionEntity =
    DirectionEntity(
        id = id,
        sourceLang = id.substringBefore('-'),
        targetLang = id.substringAfter('-'),
        sourceScript = sourceScript,
        labelEn = id,
        labelBo = id,
        shortLabel = id,
        position = position,
        idBase = (position + 1) * 1_000_000L,
        entryCount = entryCount,
    )

val BO_EN = direction("bo-en", position = 0)
val BO_BO = direction("bo-bo", position = 1)
val EN_BO = direction("en-bo", position = 2, sourceScript = "latn")

fun entry(
    id: Long,
    directionId: String,
    headword: String,
    sortOrder: Int,
    wylie: String? = null,
    glossPreview: String = "gloss",
    wotdRank: Int? = null,
): EntryEntity = EntryEntity(
    id = id,
    directionId = directionId,
    sourceId = sortOrder,
    headword = headword,
    wylie = wylie,
    glossPreview = glossPreview,
    partOfSpeech = null,
    phonetic = null,
    audioFile = null,
    senseCount = 1,
    isTruncated = false,
    sortOrder = sortOrder,
    wotdRank = wotdRank,
)

fun sense(
    id: Long,
    entryId: Long,
    senseNo: Int,
    gloss: String,
    subNo: Int? = null,
    glossLang: String = "en",
): SenseEntity = SenseEntity(
    id = id,
    entryId = entryId,
    senseNo = senseNo,
    subNo = subNo,
    partOfSpeech = null,
    label = null,
    gloss = gloss,
    glossLang = glossLang,
    note = null,
    sourceTag = null,
    isTruncated = false,
    sourceId = 1,
)

fun example(id: Long, senseId: Long, entryId: Long, position: Int, text: String, translation: String? = null): ExampleEntity =
    ExampleEntity(
        id = id,
        senseId = senseId,
        entryId = entryId,
        position = position,
        text = text,
        translation = translation,
        audioFile = null,
    )

fun related(
    id: Long,
    sourceEntryId: Long,
    targetEntryId: Long?,
    targetHeadword: String,
    position: Int,
    relation: String = "see_also",
): RelatedEntryEntity = RelatedEntryEntity(
    id = id,
    sourceEntryId = sourceEntryId,
    targetEntryId = targetEntryId,
    targetHeadword = targetHeadword,
    targetWylie = null,
    relation = relation,
    position = position,
)

/**
 * [path] relative to the app module, looked up from the working directory and its parents: Gradle runs unit tests
 * in the module directory, an IDE run configuration may use the project root. Null when there is no such file.
 */
fun findProjectFile(path: String): File? {
    val workingDir = File("").absoluteFile
    return generateSequence(workingDir) { it.parentFile }
        .flatMap { dir -> sequenceOf(File(dir, path), File(dir, "app/$path")) }
        .firstOrNull { it.exists() }
}

/** The shipped asset of [pack] in the repository, or null when it is not there. */
fun DictionaryPack.assetFileOrNull(): File? = findProjectFile("src/main/assets/$assetPath")
