package com.kharagedition.tibetandictionary.fakes

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Pins the per-pack behaviour of [FakeUserPreferencesRepository] to `UserPreferencesRepositoryImpl`. */
class FakeUserPreferencesRepositoryTest {

    private val repo = FakeUserPreferencesRepository()

    @Test
    fun switchingPackSwapsDirectionAndFolder() = runTest {
        repo.setDefaultDirectionId("en-bo")
        repo.setLastUsedFolderId(7L)

        repo.setDictionaryPack(DictionaryPack.DZONGKHA)
        assertThat(repo.snapshot().dictionaryPack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(repo.snapshot().dictionaryPackChosen).isTrue()
        assertThat(repo.snapshot().defaultDirectionId).isEqualTo("dz-en")
        assertThat(repo.snapshot().lastUsedFolderId).isNull()

        repo.setDefaultDirectionId("en-dz")
        repo.setLastUsedFolderId(9L)
        repo.setDictionaryPack(DictionaryPack.TIBETAN)
        assertThat(repo.snapshot().defaultDirectionId).isEqualTo("en-bo")
        assertThat(repo.snapshot().lastUsedFolderId).isEqualTo(7L)

        repo.setDictionaryPack(DictionaryPack.DZONGKHA)
        assertThat(repo.snapshot().defaultDirectionId).isEqualTo("en-dz")
        assertThat(repo.snapshot().lastUsedFolderId).isEqualTo(9L)
    }
}
