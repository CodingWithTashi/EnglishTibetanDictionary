package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
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
class SavedTranslationsRepositoryImplTest {
    private val boEn = LangPair(Lang.BO, Lang.EN)
    private val tashi = TranslatedText("Tashi Delek", cached = false, provider = Provider.GOOGLE)
    private val clock = FakeClock()
    private val databases = databasesOf()
    private lateinit var db: UserDatabase
    private lateinit var repo: SavedTranslationsRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryUserDb()
        databases.activate(DictionaryPack.TIBETAN)
        repo = SavedTranslationsRepositoryImpl(db, databases, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveThenObserveIsSavedIsTrue() = runTest {
        repo.observeIsSaved(boEn, "བཀྲ་ཤིས།").test {
            assertThat(awaitItem()).isFalse()
            repo.save(boEn, "བཀྲ་ཤིས།", tashi)
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun isSavedIsPerDirection() = runTest {
        repo.save(boEn, "ཀ", tashi)

        assertThat(repo.observeIsSaved(boEn.swapped(), "ཀ").first()).isFalse()
    }

    @Test
    fun saveStoresTheRecordNewestFirst() = runTest {
        val first = repo.save(boEn, "ཀ", tashi)
        clock.instant = clock.instant.plus(Duration.ofMinutes(1))
        repo.save(boEn, "ཁ", tashi.copy(translation = "kha"))

        val rows = repo.observe().first()
        assertThat(rows.map { it.source }).containsExactly("ཁ", "ཀ").inOrder()
        assertThat(rows.last()).isEqualTo(
            TranslationRecord(first, boEn, "ཀ", "Tashi Delek", Provider.GOOGLE, clock.now().minus(Duration.ofMinutes(1))),
        )
    }

    @Test
    fun removeReturnsTheRecordAndRestorePutsBackTheSameId() = runTest {
        val id = repo.save(boEn, "ཀ", tashi)
        val saved = repo.observe().first().single()

        val removed = repo.remove(id)

        assertThat(removed).isEqualTo(saved)
        assertThat(repo.observe().first()).isEmpty()
        repo.restore(removed!!)
        assertThat(repo.observe().first()).containsExactly(saved)
    }

    @Test
    fun removingAMissingIdReturnsNull() = runTest {
        assertThat(repo.remove(42)).isNull()
    }

    @Test
    fun savingTheSamePairAndSourceTwiceKeepsOneRow() = runTest {
        val first = repo.save(boEn, "ཀ", tashi)
        val second = repo.save(boEn, "ཀ", tashi.copy(translation = "Tashi Delek!"))

        assertThat(second).isEqualTo(first)
        assertThat(repo.observe().first().map { it.translation }).containsExactly("Tashi Delek!")
    }

    @Test
    fun savedTranslationsArePerPack() = runTest {
        repo.save(boEn, "ཀ", tashi)
        databases.activate(DictionaryPack.DZONGKHA)

        assertThat(repo.observe().first()).isEmpty()
    }
}
