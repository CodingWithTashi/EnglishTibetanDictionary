package com.kharagedition.tibetandictionary.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.di.DatabaseModule
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryDatabasesTest {

    private val opened = mutableListOf<DictionaryDatabase>()

    @After
    fun tearDown() {
        opened.forEach { it.close() }
    }

    /** Counts how often each pack is opened; the instances are in-memory and closed after the test. */
    private fun countingDatabases(opens: MutableList<DictionaryPack>) = DictionaryDatabases { pack ->
        opens += pack
        inMemoryDictionaryDb().also { opened += it }
    }

    @Test
    fun activeDbThrowsBeforeActivation() {
        val databases = countingDatabases(mutableListOf())

        assertThat(databases.active.value).isNull()
        assertThrows(IllegalStateException::class.java) { databases.activeDb }
    }

    @Test
    fun getOpensEachPackOnceAndKeepsBoth() {
        val opens = mutableListOf<DictionaryPack>()
        val databases = countingDatabases(opens)
        assertThat(databases.isOpen(DictionaryPack.TIBETAN)).isFalse()

        val first = databases.get(DictionaryPack.TIBETAN)
        val again = databases.get(DictionaryPack.TIBETAN)
        val dzongkha = databases.get(DictionaryPack.DZONGKHA)

        assertThat(again).isSameInstanceAs(first)
        assertThat(dzongkha).isNotSameInstanceAs(first)
        assertThat(opens).containsExactly(DictionaryPack.TIBETAN, DictionaryPack.DZONGKHA).inOrder()
        assertThat(databases.isOpen(DictionaryPack.TIBETAN)).isTrue()
        assertThat(databases.isOpen(DictionaryPack.DZONGKHA)).isTrue()
    }

    @Test
    fun activateSwitchesActiveDb() = runTest {
        val databases = countingDatabases(mutableListOf())

        databases.active.test {
            assertThat(awaitItem()).isNull()
            databases.activate(DictionaryPack.TIBETAN)
            assertThat(awaitItem()).isEqualTo(DictionaryPack.TIBETAN)
            assertThat(databases.activeDb).isSameInstanceAs(databases.get(DictionaryPack.TIBETAN))
            databases.activate(DictionaryPack.DZONGKHA)
            assertThat(awaitItem()).isEqualTo(DictionaryPack.DZONGKHA)
            assertThat(databases.activeDb).isSameInstanceAs(databases.get(DictionaryPack.DZONGKHA))
        }
    }

    @Test
    fun providerBuildsDistinctFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databases = DatabaseModule.provideDictionaryDatabases(context)

        for (pack in DictionaryPack.entries) {
            val db = databases.get(pack).also { opened += it }
            assertThat(db.openHelper.databaseName).isEqualTo(pack.dbName)
        }
    }
}
