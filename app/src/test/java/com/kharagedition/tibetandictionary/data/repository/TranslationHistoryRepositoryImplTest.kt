package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.databasesOf
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.fakes.FakeClock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationHistoryRepositoryImplTest {
    private val boEn = LangPair(Lang.BO, Lang.EN)
    private val clock = FakeClock()
    private val databases = databasesOf()
    private lateinit var db: UserDatabase
    private lateinit var repo: TranslationHistoryRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        databases.activate(DictionaryPack.TIBETAN)
        repo = TranslationHistoryRepositoryImpl(db, databases, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun text(translation: String) = TranslatedText(translation, cached = false, provider = Provider.AZURE)

    @Test
    fun recordStoresThePairTextAndTime() = runTest {
        repo.record(boEn, "བཀྲ་ཤིས།", text("Tashi Delek"))

        val row = repo.observe().first().single()
        assertThat(row.copy(id = 0)).isEqualTo(
            TranslationRecord(0, boEn, "བཀྲ་ཤིས།", "Tashi Delek", Provider.AZURE, clock.now()),
        )
    }

    @Test
    fun recordUpsertsAndMovesToTheTop() = runTest {
        repo.record(boEn, "ཀ", text("ka"))
        tick()
        repo.record(boEn, "ཁ", text("kha"))
        val firstId = repo.observe().first().last().id
        tick()

        repo.record(boEn, "ཀ", text("ka again"))

        val rows = repo.observe().first()
        assertThat(rows.map { it.source }).containsExactly("ཀ", "ཁ").inOrder()
        assertThat(rows.first().id).isEqualTo(firstId)
        assertThat(rows.first().translation).isEqualTo("ka again")
    }

    @Test
    fun theSameSourceInTheOtherDirectionIsAnotherRow() = runTest {
        repo.record(boEn, "ཀ", text("ka"))
        repo.record(boEn.swapped(), "ཀ", text("ཀ"))

        assertThat(repo.observe().first()).hasSize(2)
    }

    @Test
    fun onlyTwentyPerPackAreKept() = runTest {
        repeat(21) { i ->
            repo.record(boEn, "source $i", text("t$i"))
            tick()
        }

        val sources = repo.observe(limit = 50).first().map { it.source }
        assertThat(sources).hasSize(20)
        assertThat(sources).doesNotContain("source 0")
        assertThat(sources.first()).isEqualTo("source 20")
    }

    @Test
    fun recentsArePerPack() = runTest {
        repo.record(boEn, "ཀ", text("ka"))

        databases.activate(DictionaryPack.DZONGKHA)
        assertThat(repo.observe().first()).isEmpty()
        repo.record(LangPair(Lang.DZ, Lang.EN), "ཀ", text("ka"))

        assertThat(repo.observe().first().map { it.pair }).containsExactly(LangPair(Lang.DZ, Lang.EN))
    }

    @Test
    fun aRecordIsFiledUnderItsOwnPackEvenAfterASwitch() = runTest {
        // A Tibetan translation that returns after the switch to Dzongkha still belongs to the Tibetan pack.
        databases.activate(DictionaryPack.DZONGKHA)
        repo.record(boEn, "ཀ", text("ka"))

        assertThat(repo.observe().first()).isEmpty()
        databases.activate(DictionaryPack.TIBETAN)
        assertThat(repo.observe().first().map { it.source }).containsExactly("ཀ")
    }

    @Test
    fun clearRemovesTheActivePacksOnly() = runTest {
        repo.record(boEn, "ཀ", text("ka"))
        databases.activate(DictionaryPack.DZONGKHA)
        repo.record(LangPair(Lang.DZ, Lang.EN), "ཁ", text("kha"))

        repo.clear()

        assertThat(repo.observe().first()).isEmpty()
        databases.activate(DictionaryPack.TIBETAN)
        assertThat(repo.observe().first().map { it.source }).containsExactly("ཀ")
    }

    private fun tick() {
        clock.instant = clock.instant.plus(Duration.ofSeconds(1))
    }
}
