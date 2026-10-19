package com.kharagedition.tibetandictionary.data.search

import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.BO_EN
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.local.entry
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import com.kharagedition.tibetandictionary.data.local.sense
import com.kharagedition.tibetandictionary.data.search.FtsQueryBuilder.allTerms
import com.kharagedition.tibetandictionary.data.search.FtsQueryBuilder.anyTerm
import com.kharagedition.tibetandictionary.data.search.FtsQueryBuilder.phrase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The builder's MATCH expressions against the real FTS4 tables: they find syllables and words and never throw. */
@RunWith(AndroidJUnit4::class)
class FtsQueryBuilderDbTest {

    private lateinit var db: DictionaryDatabase
    private lateinit var dao: EntryDao

    @Before
    fun setUp() = runTest {
        db = inMemoryDictionaryDb()
        dao = db.entryDao()
        val write = db.testWriteDao()
        write.insertDirections(listOf(BO_EN))
        write.insertEntries(
            listOf(
                entry(1_000_010, "bo-en", "བཀྲ་ཤིས་", sortOrder = 10, wylie = "bkra shis"),
                entry(1_000_020, "bo-en", "ཤིང་", sortOrder = 20, wylie = "shing"),
                entry(1_000_030, "bo-en", "བཀྲ་ཤིས་བདེ་ལེགས་", sortOrder = 30, wylie = "bkra shis bde legs"),
            ),
        )
        write.insertSenses(
            listOf(
                sense(1, 1_000_010, senseNo = 1, gloss = "auspiciousness, good fortune"),
                sense(2, 1_000_020, senseNo = 1, gloss = "tree, wood"),
                sense(3, 1_000_030, senseNo = 1, gloss = "greetings; lead-in"),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tokens(raw: String) = FtsQueryBuilder.tokens(QueryNormalizer.normalize(raw))

    private suspend fun headwords(match: String?) =
        dao.ftsHeadword("bo-en", checkNotNull(match), limit = 10).map { it.id }

    private suspend fun glosses(match: String?) = dao.ftsGloss("bo-en", checkNotNull(match), limit = 10).map { it.id }

    @Test
    fun builtExpressionsMatchSyllablesAndPrefixes() = runTest {
        assertThat(headwords(phrase(tokens("བཀྲ་ཤི")))).containsExactly(1_000_010L, 1_000_030L).inOrder()
        assertThat(headwords(phrase(tokens("ཤིས་བདེ")))).containsExactly(1_000_030L)
        assertThat(headwords(allTerms(tokens("ཤི")))).containsExactly(1_000_010L, 1_000_020L, 1_000_030L).inOrder()
        assertThat(headwords(allTerms(tokens("bkra sh")))).containsExactly(1_000_010L, 1_000_030L).inOrder()
        assertThat(glosses(allTerms(tokens("Good fort")))).containsExactly(1_000_010L)
        assertThat(glosses(anyTerm(tokens("zzz woo")))).containsExactly(1_000_020L)
        assertThat(glosses(allTerms(tokens("lead-in")))).containsExactly(1_000_030L)
    }

    @Test
    fun hostileInputNeverThrows() = runTest {
        val inputs = listOf(
            "\"", "lead\"", "\"lead", "(lead", "lead)", "OR", "AND", "NOT lead", "lead OR", "NEAR", "NEAR/2 x",
            "-lead", "col:lead", "^lead", "*", "lead*astray", "'", "\u0F0D", "\u0F0B", "བཀྲ་ཤིས།", "བཀྲ OR ཤིས",
            // 5000 terms: FTS4 rejects AND/OR expressions of 4096+ terms, so the builder caps the token count.
            "a ".repeat(5000),
            "ཀ་".repeat(5000),
        )

        for (raw in inputs) {
            val tokens = tokens(raw)
            for (match in listOfNotNull(phrase(tokens), allTerms(tokens), anyTerm(tokens))) {
                dao.ftsHeadword("bo-en", match, limit = 10)
                dao.ftsGloss("bo-en", match, limit = 10)
            }
        }
    }

    @Test
    fun rawUserTextCanBeAMalformedMatch() = runTest {
        val failure = runCatching { dao.ftsHeadword("bo-en", "lead\"", limit = 10) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteException::class.java)
    }
}
