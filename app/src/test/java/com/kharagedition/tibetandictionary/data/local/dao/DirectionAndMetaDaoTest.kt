package com.kharagedition.tibetandictionary.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.direction
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectionAndMetaDaoTest {

    private lateinit var db: DictionaryDatabase

    @Before
    fun setUp() {
        db = inMemoryDictionaryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun visibleDirectionsSkipEmptyOnesAndFollowPosition() = runTest {
        db.testWriteDao().insertDirections(
            listOf(
                direction("zh-bo", position = 3, sourceScript = "hani"),
                direction("en-bo", position = 2, sourceScript = "latn"),
                direction("bo-bo", position = 1, entryCount = 0),
                direction("bo-en", position = 0),
            ),
        )

        val visible = db.directionDao().observeVisible().first()

        assertThat(visible.map { it.id }).containsExactly("bo-en", "en-bo", "zh-bo").inOrder()
        assertThat(db.directionDao().get("bo-bo")?.entryCount).isEqualTo(0)
        assertThat(db.directionDao().get("xx-yy")).isNull()
    }

    @Test
    fun directionCountsDefaultToZero() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO directions (id, source_lang, target_lang, source_script, label_en, label_bo, short_label, " +
                "position, id_base) VALUES ('bo-en', 'bo', 'en', 'tibt', 'Tibetan → English', 'བོད་ → English', " +
                "'བོད→EN', 0, 1000000)",
        )

        val row = checkNotNull(db.directionDao().get("bo-en"))

        assertThat(
            listOf(
                row.entryCount, row.senseCount, row.exampleCount, row.relatedCount, row.wylieCount,
                row.audioCount, row.posCount, row.labelCount, row.truncatedCount, row.wotdCount,
            ),
        ).containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    @Test
    fun metaValuesByKey() = runTest {
        db.testWriteDao().insertMeta(
            listOf(MetaEntity("data_version", "2026.10.1"), MetaEntity("wotd_seed", "20261019"), MetaEntity("empty", null)),
        )

        assertThat(db.metaDao().get("data_version")).isEqualTo("2026.10.1")
        assertThat(db.metaDao().get("missing")).isNull()
        assertThat(db.metaDao().get("empty")).isNull()
        assertThat(db.metaDao().observeAll().first().associate { it.key to it.value })
            .containsExactly("data_version", "2026.10.1", "wotd_seed", "20261019", "empty", null)
    }
}
