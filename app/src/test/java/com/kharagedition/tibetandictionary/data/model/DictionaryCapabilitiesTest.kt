package com.kharagedition.tibetandictionary.data.model

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import org.junit.Test

class DictionaryCapabilitiesTest {

    private val chineseTibetan = SampleData.enBo.copy(
        id = "zh-bo",
        sourceLang = "zh",
        sourceScript = Script.HAN,
        shortLabel = "中 → བོད",
        position = 3,
        entryCount = 1_200,
    )

    @Test
    fun totalEntriesSumsEveryDirection() {
        assertThat(SampleData.capabilities.totalEntries).isEqualTo(76_828 + 55_124 + 35_887)
    }

    @Test
    fun threeDirectionsAreAllPrimaryWithoutOverflow() {
        val caps = SampleData.capabilities

        assertThat(caps.primary.map { it.id }).containsExactly("bo-en", "bo-bo", "en-bo").inOrder()
        assertThat(caps.overflow).isEmpty()
        assertThat(caps.hasOverflow).isFalse()
    }

    @Test
    fun aFourthDirectionGoesToOverflow() {
        val caps = SampleData.capabilities.copy(directions = SampleData.directions + chineseTibetan)

        assertThat(caps.primary.map { it.id }).containsExactly("bo-en", "bo-bo", "en-bo").inOrder()
        assertThat(caps.overflow).containsExactly(chineseTibetan)
        assertThat(caps.hasOverflow).isTrue()
        assertThat(caps.totalEntries).isEqualTo(76_828 + 55_124 + 35_887 + 1_200)
    }

    @Test
    fun primaryAndOverflowFollowPositionNotListOrder() {
        val caps = SampleData.capabilities.copy(
            directions = listOf(chineseTibetan, SampleData.enBo, SampleData.boEn, SampleData.boBo),
        )

        assertThat(caps.primary.map { it.id }).containsExactly("bo-en", "bo-bo", "en-bo").inOrder()
        assertThat(caps.overflow.map { it.id }).containsExactly("zh-bo")
    }

    @Test
    fun hasWylieWhenAnyDirectionHasIt() {
        assertThat(SampleData.capabilities.hasWylie).isTrue()

        val englishOnly = SampleData.capabilities.copy(directions = listOf(SampleData.enBo))
        assertThat(englishOnly.hasWylie).isFalse()
    }
}
