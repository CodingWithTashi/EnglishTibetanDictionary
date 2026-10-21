package com.kharagedition.tibetandictionary.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DictionaryPackTest {
    @Test
    fun fromIdMatchesExactIds() {
        assertThat(DictionaryPack.fromId("bo")).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(DictionaryPack.fromId("dz")).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(DictionaryPack.fromId("BO")).isNull()
        assertThat(DictionaryPack.fromId("")).isNull()
        assertThat(DictionaryPack.fromId(null)).isNull()
    }

    @Test
    fun fileNamesAreDistinct() {
        val packs = DictionaryPack.entries
        assertThat(packs.map { it.dbName }.toSet()).hasSize(packs.size)
        assertThat(packs.map { it.assetPath }.toSet()).hasSize(packs.size)
        assertThat(packs.map { it.licenceAsset }.toSet()).hasSize(packs.size)
        assertThat(packs.map { it.defaultDirectionId }.toSet()).hasSize(packs.size)
    }

    @Test
    fun resourceMappingsAreDistinct() {
        val mappings: List<(DictionaryPack) -> Int> = listOf(
            { it.nameRes() }, { it.glossRes() }, { it.descriptionRes() }, { it.licenceTitleRes() },
            { it.entryCountRes() }, { it.assetBytesRes() },
        )
        for (mapping in mappings) {
            assertThat(DictionaryPack.entries.map(mapping).toSet()).hasSize(DictionaryPack.entries.size)
        }
    }
}
