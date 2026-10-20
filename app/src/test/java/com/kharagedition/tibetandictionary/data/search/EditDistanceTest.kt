package com.kharagedition.tibetandictionary.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditDistanceTest {

    private fun distance(a: String, b: String) = EditDistance.damerauLevenshtein(a, b)

    @Test
    fun substitutionsInsertionsAndDeletions() {
        assertThat(distance("kitten", "sitting")).isEqualTo(3)
        assertThat(distance("sitting", "kitten")).isEqualTo(3)
        assertThat(distance("lead", "leader")).isEqualTo(2)
    }

    @Test
    fun adjacentTranspositionCostsOne() {
        assertThat(distance("ab", "ba")).isEqualTo(1)
        assertThat(distance("bkra", "bkar")).isEqualTo(1)
    }

    @Test
    fun optimalStringAlignmentEditsEachSubstringOnce() {
        // Unrestricted Damerau-Levenshtein gives 2 (ca -> ac -> abc); OSA may not edit a transposed pair again.
        assertThat(distance("ca", "abc")).isEqualTo(3)
    }

    @Test
    fun identicalStringsAreZero() {
        assertThat(distance("lead", "lead")).isEqualTo(0)
        assertThat(distance("བཀྲ་ཤིས", "བཀྲ་ཤིས")).isEqualTo(0)
    }

    @Test
    fun emptyStringCostsTheOtherLength() {
        assertThat(distance("", "")).isEqualTo(0)
        assertThat(distance("", "abc")).isEqualTo(3)
        assertThat(distance("abc", "")).isEqualTo(3)
    }

    @Test
    fun countsCharsCaseSensitively() {
        // Each stacked letter or vowel sign is one char; callers compare normalised text.
        assertThat(distance("བཀྲ་ཤིས", "བཀྲ་ཤི")).isEqualTo(1)
        assertThat(distance("བཀྲ", "བཀ")).isEqualTo(1)
        assertThat(distance("Lead", "lead")).isEqualTo(1)
    }
}
