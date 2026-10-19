package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.ui.components.directionLabelText
import org.junit.Test

class DirectionSlotsTest {

    private val boEn = SampleData.boEn
    private val boBo = SampleData.boBo
    private val enBo = SampleData.enBo
    private val zhBo = enBo.copy(
        id = "zh-bo",
        sourceLang = "zh",
        sourceScript = Script.HAN,
        shortLabel = "中 → བོད",
        position = 3,
        entryCount = 1_200,
    )
    private val three = SampleData.directions
    private val four = three + zhBo

    /** (fixed ids, overflow ids, "More" label id). */
    private fun ids(slots: DirectionSlots) =
        Triple(slots.fixed.map { it.id }, slots.overflow.map { it.id }, slots.moreLabel?.id)

    // Pinned cases (§7.5)

    @Test
    fun threeDirectionsAreAllFixed() {
        val slots = DirectionSlots.compute(three, defaultId = "bo-en", selectedId = "bo-en")

        assertThat(slots).isEqualTo(DirectionSlots(listOf(boEn, boBo, enBo), overflow = emptyList(), moreLabel = null))
    }

    @Test
    fun fourDirectionsFixTheDefaultAndItsReverse() {
        val slots = DirectionSlots.compute(four, defaultId = "bo-en", selectedId = "bo-en")

        assertThat(slots).isEqualTo(DirectionSlots(listOf(boEn, enBo), overflow = listOf(boBo, zhBo), moreLabel = null))
    }

    @Test
    fun selectedOverflowDirectionLabelsTheMoreSlot() {
        val slots = DirectionSlots.compute(four, defaultId = "bo-en", selectedId = "zh-bo")

        assertThat(slots.moreLabel).isEqualTo(zhBo)
        assertThat(directionLabelText(slots.moreLabel!!)).isEqualTo("中→བོད")
    }

    // Review Focus 3

    @Test
    fun fallbackWhenDefaultHidden() {
        // The stored default was removed by a data release: the first visible direction takes its place.
        assertThat(ids(DirectionSlots.compute(four, defaultId = "zh-en", selectedId = "zh-en")))
            .isEqualTo(Triple(listOf("bo-en", "en-bo"), listOf("bo-bo", "zh-bo"), null))
        // The stored default now has no entries: hidden, so the same fallback applies.
        val boZh = zhBo.copy(id = "bo-zh", sourceLang = "bo", targetLang = "zh", position = 4, entryCount = 0)
        assertThat(ids(DirectionSlots.compute(four + boZh, defaultId = "bo-zh", selectedId = "bo-zh")))
            .isEqualTo(Triple(listOf("bo-en", "en-bo"), listOf("bo-bo", "zh-bo"), null))
        // The fallback is shared with screens that only need the default direction.
        assertThat(DirectionSlots.defaultDirection(four, "zh-en")).isEqualTo(boEn)
        assertThat(DirectionSlots.defaultDirection(four + boZh, "bo-zh")).isEqualTo(boEn)
        assertThat(DirectionSlots.defaultDirection(listOf(enBo, boBo), "zh-en")).isEqualTo(boBo)
    }

    @Test
    fun defaultDirectionIsTheStoredOneWhenVisible() {
        assertThat(DirectionSlots.defaultDirection(four, "en-bo")).isEqualTo(enBo)
        assertThat(DirectionSlots.defaultDirection(emptyList(), "bo-en")).isNull()
    }

    // Ordering and slotting

    @Test
    fun canonicalOrderIsByPosition() {
        val slots = DirectionSlots.compute(three.reversed(), defaultId = "en-bo", selectedId = null)

        assertThat(slots.fixed).containsExactly(boEn, boBo, enBo).inOrder()
        assertThat(slots.overflow).isEmpty()
    }

    @Test
    fun overflowFollowsCanonicalOrder() {
        val slots = DirectionSlots.compute(four.reversed(), defaultId = "en-bo", selectedId = null)

        assertThat(ids(slots)).isEqualTo(Triple(listOf("en-bo", "bo-en"), listOf("bo-bo", "zh-bo"), null))
    }

    @Test
    fun aDefaultWithoutAReverseTakesTheNextCanonicalDirection() {
        // bo-bo is its own reverse, so it pairs with the direction after it.
        assertThat(ids(DirectionSlots.compute(four, defaultId = "bo-bo", selectedId = null)))
            .isEqualTo(Triple(listOf("bo-bo", "en-bo"), listOf("bo-en", "zh-bo"), null))
        // The last direction wraps around to the first.
        assertThat(ids(DirectionSlots.compute(four, defaultId = "zh-bo", selectedId = null)))
            .isEqualTo(Triple(listOf("zh-bo", "bo-en"), listOf("bo-bo", "en-bo"), null))
    }

    @Test
    fun moreLabelIsSetOnlyForAnOverflowSelection() {
        assertThat(DirectionSlots.compute(four, defaultId = "bo-en", selectedId = "bo-bo").moreLabel).isEqualTo(boBo)
        assertThat(DirectionSlots.compute(four, defaultId = "bo-en", selectedId = "en-bo").moreLabel).isNull()
        assertThat(DirectionSlots.compute(four, defaultId = "bo-en", selectedId = "zh-en").moreLabel).isNull()
        assertThat(DirectionSlots.compute(three, defaultId = "bo-en", selectedId = "en-bo").moreLabel).isNull()
    }

    @Test
    fun directionsWithoutEntriesAreNeverSlotted() {
        val slots = DirectionSlots.compute(three + zhBo.copy(entryCount = 0), defaultId = "bo-en", selectedId = "zh-bo")

        assertThat(slots).isEqualTo(DirectionSlots(listOf(boEn, boBo, enBo), overflow = emptyList(), moreLabel = null))
    }

    @Test
    fun noDirectionsGiveEmptySlots() {
        assertThat(DirectionSlots.compute(emptyList(), defaultId = "bo-en", selectedId = "bo-en"))
            .isEqualTo(DirectionSlots(fixed = emptyList(), overflow = emptyList(), moreLabel = null))
    }
}
