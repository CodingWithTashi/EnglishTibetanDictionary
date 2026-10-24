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
        // A pack copied from another and only half edited would show the other pack's name, greeting or icon.
        val mappings: List<(PackResources) -> Int> = listOf(
            PackResources::name, PackResources::gloss, PackResources::title, PackResources::titleLatin,
            PackResources::language, PackResources::description, PackResources::welcome,
            PackResources::greetingMorning, PackResources::greetingAfternoon, PackResources::greetingEvening,
            PackResources::licenceTitle, PackResources::tile, PackResources::entryCount, PackResources::assetBytes,
        )
        for (mapping in mappings) {
            assertThat(DictionaryPack.entries.map { mapping(it.resources) }.toSet()).hasSize(DictionaryPack.entries.size)
        }
    }

    @Test
    fun fileNamesFollowTheId() {
        for (pack in DictionaryPack.entries) {
            assertThat(pack.dbName).isEqualTo("dictionary-${pack.id}.db")
            assertThat(pack.assetPath).isEqualTo("dictionary/${pack.id}.db")
        }
    }

    @Test
    fun everyPackDescribesItsOwnDirections() {
        // directionDescription looks a direction up in every pack, so two packs must not claim the same id.
        val ids = DictionaryPack.entries.flatMap { it.resources.directionDescriptions.keys }
        assertThat(ids).containsNoDuplicates()
        for (pack in DictionaryPack.entries) {
            assertThat(pack.resources.directionDescriptions).containsKey(pack.defaultDirectionId)
        }
        val descriptions = DictionaryPack.entries.flatMap { it.resources.directionDescriptions.values }
        assertThat(descriptions).containsNoDuplicates()
    }
}
