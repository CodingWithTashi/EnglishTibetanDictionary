package com.kharagedition.tibetandictionary.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.BO_BO
import com.kharagedition.tibetandictionary.data.local.BO_EN
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.EN_BO
import com.kharagedition.tibetandictionary.data.local.entry
import com.kharagedition.tibetandictionary.data.local.example
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import com.kharagedition.tibetandictionary.data.local.related
import com.kharagedition.tibetandictionary.data.local.sense
import com.kharagedition.tibetandictionary.data.search.PrefixBounds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryDaoTest {

    private lateinit var db: DictionaryDatabase
    private lateinit var dao: EntryDao
    private lateinit var write: TestWriteDao

    @Before
    fun setUp() {
        db = inMemoryDictionaryDb()
        dao = db.entryDao()
        write = db.testWriteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun prefixOrdersExactFirstThenMonlam() = runTest {
        write.insertDirections(listOf(BO_EN, BO_BO))
        write.insertEntries(
            listOf(
                entry(1_000_001, "bo-en", "ཀ་", sortOrder = 1),
                entry(1_000_005, "bo-en", "བཀྲག་", sortOrder = 5),
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10),
                entry(1_000_015, "bo-en", "བཀྲ་ཤིས་བདེ་ལེགས་", sortOrder = 15),
                entry(1_000_020, "bo-en", "བཀྲ་", sortOrder = 20),
                entry(1_000_030, "bo-en", "བཀྱེ་", sortOrder = 30),
                entry(2_000_001, "bo-bo", "བཀྲ་ཤིས་", sortOrder = 1),
            ),
        )

        val all = dao.prefixByHeadword(
            dir = "bo-en", prefix = "བཀྲ", upper = PrefixBounds.upper("བཀྲ"), exact = "བཀྲ", exactTsheg = "བཀྲ་",
            limit = 10, offset = 0,
        )
        val page = dao.prefixByHeadword(
            dir = "bo-en", prefix = "བཀྲ", upper = PrefixBounds.upper("བཀྲ"), exact = "བཀྲ", exactTsheg = "བཀྲ་",
            limit = 2, offset = 1,
        )

        // The exact headword (with its tsheg) leads; the rest follow Monlam order (sort_order), not code-point order.
        assertThat(all.map { it.headword })
            .containsExactly("བཀྲ་", "བཀྲག་", "བཀྲ་ཤིས་", "བཀྲ་ཤིས་བདེ་ལེགས་").inOrder()
        assertThat(all.map { it.directionId }.toSet()).containsExactly("bo-en")
        assertThat(page.map { it.headword }).containsExactly("བཀྲག་", "བཀྲ་ཤིས་").inOrder()
    }

    @Test
    fun englishPrefixIsCaseInsensitive() = runTest {
        write.insertDirections(listOf(EN_BO))
        write.insertEntries(
            listOf(
                entry(3_000_005, "en-bo", "hot", sortOrder = 5),
                entry(3_000_010, "en-bo", "household", sortOrder = 10),
                entry(3_000_020, "en-bo", "house", sortOrder = 20),
                entry(3_000_025, "en-bo", "Houston", sortOrder = 25),
                entry(3_000_030, "en-bo", "housework", sortOrder = 30),
            ),
        )

        val hou = dao.prefixByHeadword("en-bo", "HOU", PrefixBounds.upper("HOU"), "HOU", "HOU", limit = 10, offset = 0)
        val house = dao.prefixByHeadword(
            "en-bo", "hOuSe", PrefixBounds.upper("hOuSe"), "hOuSe", "hOuSe", limit = 10, offset = 0,
        )

        assertThat(hou.map { it.headword }).containsExactly("household", "house", "Houston", "housework").inOrder()
        assertThat(house.map { it.headword }).containsExactly("house", "household", "housework").inOrder()
    }

    @Test
    fun headwordIsUniquePerDirectionIgnoringCase() = runTest {
        write.insertDirections(listOf(EN_BO, BO_EN))
        write.insertEntries(listOf(entry(3_000_020, "en-bo", "house", sortOrder = 20)))

        val duplicate = runCatching {
            write.insertEntries(listOf(entry(3_000_021, "en-bo", "HOUSE", sortOrder = 21)))
        }.exceptionOrNull()
        write.insertEntries(listOf(entry(1_000_021, "bo-en", "house", sortOrder = 21)))

        assertThat(duplicate).isInstanceOf(SQLiteConstraintException::class.java)
        assertThat(dao.getByIds(listOf(3_000_020, 1_000_021)).map { it.id }).containsExactly(3_000_020L, 1_000_021L)
    }

    @Test
    fun wyliePrefixCaseInsensitiveAndNullSafe() = runTest {
        write.insertDirections(listOf(BO_EN))
        write.insertEntries(
            listOf(
                entry(1_000_001, "bo-en", "ཀ་", sortOrder = 1, wylie = "ka"),
                entry(1_000_005, "bo-en", "བཀྲག་", sortOrder = 5, wylie = "bkrag"),
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10, wylie = "bkra shis"),
                entry(1_000_012, "bo-en", "བཀྲ་ཤིས་བདེ་ལེགས་", sortOrder = 12, wylie = null),
                entry(1_000_020, "bo-en", "བཀྲ་", sortOrder = 20, wylie = "bkra"),
            ),
        )

        val hits =
            dao.prefixByWylie("bo-en", "BKRA", PrefixBounds.upper("BKRA"), exact = "BKRA", limit = 10, offset = 0)
        val none = dao.prefixByWylie("bo-en", "zz", PrefixBounds.upper("zz"), exact = "zz", limit = 10, offset = 0)

        assertThat(hits.map { it.wylie }).containsExactly("bkra", "bkrag", "bkra shis").inOrder()
        assertThat(hits.map { it.id }).doesNotContain(1_000_012L)
        assertThat(none).isEmpty()
    }

    @Test
    fun ftsHeadwordMatchesSyllableInsideWord() = runTest {
        write.insertDirections(listOf(BO_EN, BO_BO))
        write.insertEntries(
            listOf(
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10, wylie = "bkra shis"),
                entry(1_000_020, "bo-en", "ཤ་", sortOrder = 20, wylie = "sha"),
                entry(1_000_030, "bo-en", "ཤིང་", sortOrder = 30, wylie = "shing"),
                entry(2_000_010, "bo-bo", "བཀྲ་ཤིས་", sortOrder = 10, wylie = "bkra shis"),
            ),
        )

        suspend fun fts(match: String) = dao.ftsHeadword("bo-en", match, limit = 10).map { it.headword }

        // One token per syllable: vowel signs (U+0F72) and subjoined letters (U+0FB2) stay inside the token.
        assertThat(fts("ཤིས")).containsExactly("བཀྲ་ཤིས་")
        assertThat(fts("ཤ")).containsExactly("ཤ་")
        assertThat(fts("བཀ")).isEmpty()
        assertThat(fts("ཤི*")).containsExactly("བཀྲ་ཤིས་", "ཤིང་").inOrder()
        // The Wylie column is indexed too.
        assertThat(fts("shis")).containsExactly("བཀྲ་ཤིས་")
    }

    @Test
    fun ftsHeadwordOutsidePrefixSkipsPrefixRows() = runTest {
        write.insertDirections(listOf(BO_EN, EN_BO))
        write.insertEntries(
            listOf(
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10, wylie = "bkra shis"),
                entry(1_000_020, "bo-en", "དཔལ་བཀྲ་ཤིས་", sortOrder = 20, wylie = "dpal bkra shis"),
                entry(1_000_030, "bo-en", "ཤིས་པ་", sortOrder = 30, wylie = "shis pa"),
                entry(3_000_010, "en-bo", "Term", sortOrder = 10),
                entry(3_000_020, "en-bo", "long term", sortOrder = 20),
                entry(3_000_030, "en-bo", "terms", sortOrder = 30),
            ),
        )

        suspend fun contains(dir: String, match: String, prefix: String) =
            dao.ftsHeadwordOutsidePrefix(dir, match, prefix, PrefixBounds.upper(prefix), limit = 10).map { it.id }

        // A headword or Wylie starting with the prefix is a prefix-step row; only "contains" rows are left.
        assertThat(contains("bo-en", "\"བཀྲ ཤིས*\"", "བཀྲ་ཤིས")).containsExactly(1_000_020L)
        assertThat(contains("bo-en", "\"bkra shis*\"", "bkra shis")).containsExactly(1_000_020L)
        assertThat(contains("bo-en", "\"shis*\"", "shis")).containsExactly(1_000_010L, 1_000_020L).inOrder()
        // NOCASE like the prefix step ("Term"), and a NULL Wylie never hides a row ("long term").
        assertThat(contains("en-bo", "\"term*\"", "term")).containsExactly(3_000_020L)
    }

    @Test
    fun firstBySortOrderListsOneDirectionInMonlamOrder() = runTest {
        write.insertDirections(listOf(BO_EN, BO_BO))
        write.insertEntries(
            listOf(
                entry(1_000_030, "bo-en", "ག་", sortOrder = 30),
                entry(1_000_010, "bo-en", "ཀ་", sortOrder = 10),
                entry(1_000_040, "bo-en", "ང་", sortOrder = 40),
                entry(1_000_020, "bo-en", "ཁ་", sortOrder = 20),
                entry(2_000_001, "bo-bo", "ཀ་", sortOrder = 1),
            ),
        )

        assertThat(dao.firstBySortOrder("bo-en", limit = 3).map { it.id })
            .containsExactly(1_000_010L, 1_000_020L, 1_000_030L).inOrder()
        assertThat(dao.firstBySortOrder("bo-en", limit = 10).map { it.sortOrder })
            .containsExactly(10, 20, 30, 40).inOrder()
        assertThat(dao.firstBySortOrder("bo-bo", limit = 10).map { it.id }).containsExactly(2_000_001L)
        assertThat(dao.firstBySortOrder("en-bo", limit = 10)).isEmpty()
    }

    @Test
    fun ftsGlossReverseLookup() = runTest {
        write.insertDirections(listOf(BO_EN, EN_BO))
        write.insertEntries(
            listOf(
                entry(1_000_005, "bo-en", "ཤིང་", sortOrder = 5, glossPreview = "tree"),
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10, glossPreview = "auspiciousness; good fortune"),
                entry(1_000_020, "bo-en", "སྐལ་བ་", sortOrder = 20, glossPreview = "fortune"),
                entry(3_000_010, "en-bo", "fortune", sortOrder = 10, glossPreview = "སྐལ་བ་"),
            ),
        )
        write.insertSenses(
            listOf(
                sense(1, 1_000_005, senseNo = 1, gloss = "tree"),
                sense(2, 1_000_010, senseNo = 1, gloss = "auspiciousness, good fortune"),
                sense(3, 1_000_010, senseNo = 2, gloss = "fortune; luck"),
                sense(4, 1_000_020, senseNo = 1, gloss = "fortune; fate"),
                sense(5, 3_000_010, senseNo = 1, gloss = "སྐལ་བ་། བསོད་ནམས་", glossLang = "bo"),
            ),
        )

        val english = dao.ftsGloss("bo-en", "fortune", limit = 10)
        val tibetan = dao.ftsGloss("en-bo", "སྐལ", limit = 10)

        // Shortest preview first; an entry with several matching senses appears once.
        assertThat(english.map { it.headword }).containsExactly("སྐལ་བ་", "བཀྲ་ཤིས་").inOrder()
        assertThat(tibetan.map { it.headword }).containsExactly("fortune")
        assertThat(dao.ftsGloss("bo-en", "tree", limit = 10).map { it.id }).containsExactly(1_000_005L)
        assertThat(dao.ftsGloss("en-bo", "fortune", limit = 10)).isEmpty()
    }

    @Test
    fun wotdRankLookup() = runTest {
        write.insertDirections(listOf(BO_EN, BO_BO))
        write.insertEntries(
            listOf(
                entry(1_000_001, "bo-en", "ཀ་", sortOrder = 1, wotdRank = 2),
                entry(1_000_002, "bo-en", "ཁ་", sortOrder = 2, wotdRank = 0),
                entry(1_000_003, "bo-en", "ག་", sortOrder = 3, wotdRank = null),
                entry(1_000_004, "bo-en", "ང་", sortOrder = 4, wotdRank = 1),
                entry(2_000_001, "bo-bo", "ཀ་", sortOrder = 1),
            ),
        )

        assertThat(dao.byWotdRank(1)?.headword).isEqualTo("ང་")
        assertThat(dao.byWotdRank(0)?.id).isEqualTo(1_000_002L)
        assertThat(dao.byWotdRank(7)).isNull()
        assertThat(dao.wotdRange("bo-en", from = 0, to = 2).map { it.wotdRank }).containsExactly(0, 1).inOrder()
        assertThat(dao.wotdRange("bo-en", from = 1, to = 10).map { it.headword }).containsExactly("ང་", "ཀ་").inOrder()
        assertThat(dao.wotdRange("bo-bo", from = 0, to = 10)).isEmpty()
    }

    @Test
    fun detailsBundleSensesExamplesAndRelated() = runTest {
        write.insertDirections(listOf(BO_EN))
        write.insertEntries(
            listOf(
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10),
                entry(1_000_020, "bo-en", "བདེ་ལེགས་", sortOrder = 20),
            ),
        )
        write.insertSenses(
            listOf(
                sense(11, 1_000_010, senseNo = 2, gloss = "good fortune"),
                sense(12, 1_000_010, senseNo = 1, subNo = 2, gloss = "glory"),
                sense(13, 1_000_010, senseNo = 1, subNo = 1, gloss = "auspiciousness"),
                sense(21, 1_000_020, senseNo = 1, gloss = "goodness"),
            ),
        )
        write.insertExamples(
            listOf(
                example(101, senseId = 13, entryId = 1_000_010, position = 0, text = "བཀྲ་ཤིས་བདེ་ལེགས།", translation = "greetings"),
                example(102, senseId = 11, entryId = 1_000_010, position = 0, text = "ex"),
            ),
        )
        write.insertRelated(
            listOf(
                related(201, sourceEntryId = 1_000_010, targetEntryId = null, targetHeadword = "ཕུན་ཚོགས་", position = 1),
                related(202, sourceEntryId = 1_000_010, targetEntryId = 1_000_020, targetHeadword = "བདེ་ལེགས་", position = 0),
            ),
        )

        val details = checkNotNull(dao.getWithDetails(1_000_010))
        val senses = db.senseDao().forEntry(1_000_010)
        val links = db.relatedEntryDao().forEntry(1_000_010)

        assertThat(details.entry.headword).isEqualTo("བཀྲ་ཤིས་")
        assertThat(details.senses.map { it.sense.id }).containsExactly(11L, 12L, 13L)
        assertThat(details.senses.first { it.sense.id == 13L }.examples.map { it.translation }).containsExactly("greetings")
        assertThat(details.related.map { it.id }).containsExactly(201L, 202L)
        assertThat(senses.map { it.sense.gloss }).containsExactly("auspiciousness", "glory", "good fortune").inOrder()
        assertThat(senses.first().examples.map { it.id }).containsExactly(101L)
        assertThat(links.map { it.targetHeadword }).containsExactly("བདེ་ལེགས་", "ཕུན་ཚོགས་").inOrder()
        assertThat(dao.getWithDetails(9)).isNull()
        assertThat(dao.get(1_000_020)?.headword).isEqualTo("བདེ་ལེགས་")
        dao.observeWithDetails(1_000_020).test {
            assertThat(awaitItem()?.senses?.map { it.sense.gloss }).containsExactly("goodness")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun foreignKeysCascadeSensesAndUnlinkRelatedTargets() = runTest {
        write.insertDirections(listOf(BO_EN))
        write.insertEntries(
            listOf(
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10),
                entry(1_000_020, "bo-en", "བདེ་ལེགས་", sortOrder = 20),
            ),
        )
        write.insertSenses(listOf(sense(21, 1_000_020, senseNo = 1, gloss = "goodness")))
        write.insertExamples(listOf(example(301, senseId = 21, entryId = 1_000_020, position = 0, text = "ex")))
        write.insertRelated(
            listOf(related(201, sourceEntryId = 1_000_010, targetEntryId = 1_000_020, targetHeadword = "བདེ་ལེགས་", position = 0)),
        )

        db.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = 1000020")

        assertThat(db.senseDao().forEntry(1_000_020)).isEmpty()
        assertThat(db.relatedEntryDao().forEntry(1_000_010).single().targetEntryId).isNull()
        assertThat(dao.ftsHeadword("bo-en", "བདེ", limit = 10)).isEmpty()
    }
}
