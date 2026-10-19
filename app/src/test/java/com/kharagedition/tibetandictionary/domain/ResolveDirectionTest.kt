package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Script
import org.junit.Test

class ResolveDirectionTest {

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
    private val directions = SampleData.directions

    private fun resolve(
        script: InputScript,
        selected: String,
        hits: Map<String, Int> = emptyMap(),
        pinned: Boolean = false,
        exactHits: Map<String, Int> = emptyMap(),
        directions: List<Direction> = this.directions,
    ) = ResolveDirection.resolve(script, selected, pinned, directions, hits, exactHits)

    // Pinned cases (§7.5)

    @Test
    fun wylieWithHitsOnlyInBoEnAutoSelectsBoEn() {
        assertThat(resolve(InputScript.WYLIE, selected = "en-bo", hits = mapOf("bo-en" to 38)))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = null))
    }

    @Test
    fun latinPaWithHitsInBothPicksEnBoWithBoEnAsAlternate() {
        assertThat(resolve(InputScript.LATIN, selected = "bo-bo", hits = mapOf("en-bo" to 3, "bo-en" to 12)))
            .isEqualTo(Resolution("en-bo", autoSelected = true, alternateId = "bo-en"))
    }

    @Test
    fun tibetanWhileEnBoIsSelectedAutoSelectsBoEn() {
        assertThat(resolve(InputScript.TIBETAN, selected = "en-bo"))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = null))
    }

    // Pinned

    @Test
    fun pinnedKeepsTheSelectedDirection() {
        assertThat(resolve(InputScript.TIBETAN, selected = "en-bo", pinned = true))
            .isEqualTo(Resolution("en-bo", autoSelected = false, alternateId = null))
        assertThat(resolve(InputScript.LATIN, selected = "bo-en", hits = mapOf("en-bo" to 7), pinned = true))
            .isEqualTo(Resolution("bo-en", autoSelected = false, alternateId = "en-bo"))
    }

    @Test
    fun pinnedStillOffersTheOtherCandidateAsAlternate() {
        assertThat(resolve(InputScript.WYLIE, selected = "en-bo", hits = mapOf("bo-en" to 38), pinned = true))
            .isEqualTo(Resolution("en-bo", autoSelected = false, alternateId = "bo-en"))
        // Pinned to a non-candidate: the best candidate with hits, else the one the script prefers.
        assertThat(resolve(InputScript.LATIN, selected = "bo-bo", hits = mapOf("bo-en" to 2), pinned = true))
            .isEqualTo(Resolution("bo-bo", autoSelected = false, alternateId = "bo-en"))
        assertThat(resolve(InputScript.LATIN, selected = "bo-bo", pinned = true))
            .isEqualTo(Resolution("bo-bo", autoSelected = false, alternateId = "en-bo"))
    }

    // Tibetan and Han

    @Test
    fun tibetanKeepsASelectedTibetanSourceDirection() {
        assertThat(resolve(InputScript.TIBETAN, selected = "bo-bo"))
            .isEqualTo(Resolution("bo-bo", autoSelected = false, alternateId = null))
        assertThat(resolve(InputScript.TIBETAN, selected = "bo-en"))
            .isEqualTo(Resolution("bo-en", autoSelected = false, alternateId = null))
    }

    @Test
    fun tibetanIgnoresHits() {
        assertThat(resolve(InputScript.TIBETAN, selected = "en-bo", hits = mapOf("bo-bo" to 9, "en-bo" to 4)))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = null))
    }

    @Test
    fun hanMovesToTheFirstHanSourceDirection() {
        assertThat(resolve(InputScript.HAN, selected = "bo-en", directions = directions + zhBo))
            .isEqualTo(Resolution("zh-bo", autoSelected = true, alternateId = null))
    }

    @Test
    fun hanWithoutAHanDirectionKeepsTheSelection() {
        assertThat(resolve(InputScript.HAN, selected = "bo-en"))
            .isEqualTo(Resolution("bo-en", autoSelected = false, alternateId = null))
    }

    // Latin and Wylie: hits decide

    @Test
    fun theOnlyCandidateWithHitsWins() {
        assertThat(resolve(InputScript.LATIN, selected = "en-bo", hits = mapOf("bo-en" to 5, "en-bo" to 0)))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = null))
        assertThat(resolve(InputScript.WYLIE, selected = "bo-en", hits = mapOf("en-bo" to 5)))
            .isEqualTo(Resolution("en-bo", autoSelected = true, alternateId = null))
    }

    @Test
    fun hitsInBothKeepTheSelectedCandidateRegardlessOfCounts() {
        assertThat(resolve(InputScript.LATIN, selected = "bo-en", hits = mapOf("en-bo" to 30, "bo-en" to 1)))
            .isEqualTo(Resolution("bo-en", autoSelected = false, alternateId = "en-bo"))
        assertThat(resolve(InputScript.LATIN, selected = "en-bo", hits = mapOf("en-bo" to 3, "bo-en" to 12)))
            .isEqualTo(Resolution("en-bo", autoSelected = false, alternateId = "bo-en"))
    }

    @Test
    fun hitsInBothWithANonCandidateSelectedFollowTheScript() {
        assertThat(resolve(InputScript.WYLIE, selected = "bo-bo", hits = mapOf("en-bo" to 3, "bo-en" to 12)))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = "en-bo"))
    }

    @Test
    fun exactHitsBreakATieOnHits() {
        val hits = mapOf("en-bo" to 3, "bo-en" to 12)

        assertThat(resolve(InputScript.LATIN, selected = "en-bo", hits = hits, exactHits = mapOf("bo-en" to 1)))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = "en-bo"))
        assertThat(resolve(InputScript.WYLIE, selected = "bo-en", hits = hits, exactHits = mapOf("en-bo" to 1)))
            .isEqualTo(Resolution("en-bo", autoSelected = true, alternateId = "bo-en"))
        // Exact hits in both: still a tie.
        assertThat(
            resolve(InputScript.LATIN, selected = "bo-en", hits = hits, exactHits = mapOf("bo-en" to 1, "en-bo" to 1)),
        ).isEqualTo(Resolution("bo-en", autoSelected = false, alternateId = "en-bo"))
    }

    @Test
    fun noHitsAnywhereKeepsTheSelectedCandidateAndOffersTheOther() {
        assertThat(resolve(InputScript.LATIN, selected = "bo-en"))
            .isEqualTo(Resolution("bo-en", autoSelected = false, alternateId = "en-bo"))
        assertThat(resolve(InputScript.WYLIE, selected = "en-bo", hits = mapOf("en-bo" to 0, "bo-en" to 0)))
            .isEqualTo(Resolution("en-bo", autoSelected = false, alternateId = "bo-en"))
    }

    @Test
    fun noHitsAnywhereWithANonCandidateSelectedFollowTheScript() {
        assertThat(resolve(InputScript.LATIN, selected = "bo-bo"))
            .isEqualTo(Resolution("en-bo", autoSelected = true, alternateId = "bo-en"))
        assertThat(resolve(InputScript.WYLIE, selected = "bo-bo"))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = "en-bo"))
    }

    // Candidates

    @Test
    fun hitsOutsideTheCandidatesAreIgnored() {
        // bo-bo has Wylie too, but bo-en is the first Tibetan-source direction with Wylie.
        assertThat(resolve(InputScript.WYLIE, selected = "bo-bo", hits = mapOf("bo-bo" to 50, "en-bo" to 2)))
            .isEqualTo(Resolution("en-bo", autoSelected = true, alternateId = null))
    }

    @Test
    fun theWylieCandidateIsTheFirstTibetanSourceDirectionWithWylie() {
        val noWylie = boEn.features.copy(hasWylie = false)
        val dirs = listOf(boEn.copy(features = noWylie), boBo, enBo)

        assertThat(resolve(InputScript.WYLIE, selected = "en-bo", hits = mapOf("bo-bo" to 4), directions = dirs))
            .isEqualTo(Resolution("bo-bo", autoSelected = true, alternateId = null))
    }

    @Test
    fun candidatesFollowPositionOrder() {
        val reversed = directions.reversed()

        assertThat(resolve(InputScript.WYLIE, selected = "en-bo", hits = mapOf("bo-en" to 1), directions = reversed))
            .isEqualTo(Resolution("bo-en", autoSelected = true, alternateId = null))
    }

    @Test
    fun directionsWithoutEntriesAreNeverCandidates() {
        val dirs = listOf(boEn.copy(entryCount = 0), boBo, enBo)

        val hits = mapOf("bo-en" to 9, "bo-bo" to 2)

        assertThat(resolve(InputScript.WYLIE, selected = "en-bo", hits = hits, directions = dirs))
            .isEqualTo(Resolution("bo-bo", autoSelected = true, alternateId = null))
        assertThat(resolve(InputScript.TIBETAN, selected = "en-bo", directions = dirs).directionId).isEqualTo("bo-bo")
    }

    @Test
    fun withoutAWylieDirectionLatinAndWylieUseTheHeadwordDirection() {
        val dirs = listOf(boEn.copy(features = boEn.features.copy(hasWylie = false)), enBo)

        assertThat(resolve(InputScript.WYLIE, selected = "bo-en", directions = dirs))
            .isEqualTo(Resolution("en-bo", autoSelected = true, alternateId = null))
    }

    @Test
    fun withoutCandidatesTheSelectionIsKept() {
        val noWylie = listOf(boBo.copy(features = boBo.features.copy(hasWylie = false)))

        assertThat(resolve(InputScript.LATIN, selected = "bo-bo", hits = mapOf("bo-bo" to 1), directions = noWylie))
            .isEqualTo(Resolution("bo-bo", autoSelected = false, alternateId = null))
        assertThat(resolve(InputScript.TIBETAN, selected = "en-bo", directions = emptyList()))
            .isEqualTo(Resolution("en-bo", autoSelected = false, alternateId = null))
    }
}
