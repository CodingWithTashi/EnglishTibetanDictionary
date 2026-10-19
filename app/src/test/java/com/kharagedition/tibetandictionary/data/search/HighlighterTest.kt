package com.kharagedition.tibetandictionary.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HighlighterTest {

    // Char indices: བཀྲ 0..2, tsheg 3, ཤིས 4..6, tsheg 7, བདེ 8..10, tsheg 11, ལེགས 12..15, tsheg 16.
    private val tashi = "བཀྲ་ཤིས་"
    private val tashiDelek = "བཀྲ་ཤིས་བདེ་ལེགས་"

    // Wylie

    @Test
    fun wylieQueryHighlightsTheWylieAndTheFirstSyllable() {
        assertThat(Highlighter.wylieRange("bkra shis", "bkra")).isEqualTo(0..3)
        assertThat(Highlighter.headwordRangeForWylie(tashi, "bkra")).isEqualTo(0 until 3)
    }

    @Test
    fun partialSecondSyllableCoversTwoSyllables() {
        assertThat(Highlighter.wylieRange("bkra shis", "bkra sh")).isEqualTo(0 until 7)
        assertThat(Highlighter.headwordRangeForWylie(tashi, "bkra sh")).isEqualTo(0 until 7)
        assertThat(Highlighter.headwordRangeForWylie(tashiDelek, "bkra sh")).isEqualTo(0 until 7)
    }

    @Test
    fun trailingSpaceCountsOnlyTheCompletedSyllables() {
        assertThat(Highlighter.headwordRangeForWylie(tashiDelek, "bkra shis ")).isEqualTo(0 until 7)
        assertThat(Highlighter.headwordRangeForWylie(tashiDelek, "bkra ")).isEqualTo(0 until 3)
        assertThat(Highlighter.headwordRangeForWylie(tashiDelek, "bkra shis bde")).isEqualTo(0 until 11)
    }

    @Test
    fun syllableCountIsCappedAtTheHeadword() {
        assertThat(Highlighter.headwordRangeForWylie("བཀྲ་", "bkra shis bde")).isEqualTo(0 until 3)
        assertThat(Highlighter.headwordRangeForWylie(tashi, "bkra shis bde legs")).isEqualTo(0 until 7)
    }

    @Test
    fun wylieIgnoresCaseAndNeedsAPrefix() {
        assertThat(Highlighter.wylieRange("Bkra shis", "bKRA")).isEqualTo(0 until 4)
        assertThat(Highlighter.wylieRange("bkra shis", "shis")).isNull()
        assertThat(Highlighter.wylieRange("bkra shis", "")).isNull()
        assertThat(Highlighter.headwordRangeForWylie(tashi, "  ")).isNull()
        assertThat(Highlighter.headwordRangeForWylie("", "bkra")).isNull()
    }

    // Headword

    @Test
    fun headwordPrefixIgnoresCaseAndTheQueryTrailingTsheg() {
        assertThat(Highlighter.headwordRange("Lead astray", "lead")).isEqualTo(0 until 4)
        assertThat(Highlighter.headwordRange(tashi, "བཀྲ")).isEqualTo(0 until 3)
        assertThat(Highlighter.headwordRange(tashi, "བཀྲ་")).isEqualTo(0 until 3)
        assertThat(Highlighter.headwordRange(tashi, "བཀྲ་ཤིས་")).isEqualTo(0 until 7)
    }

    @Test
    fun headwordContainsHighlightsTheFirstToken() {
        assertThat(Highlighter.headwordRange(tashiDelek, "ཤིས")).isEqualTo(4 until 7)
        assertThat(Highlighter.headwordRange("go astray", "ASTRAY")).isEqualTo(3 until 9)
        assertThat(Highlighter.headwordRange("go astray", "astray far")).isEqualTo(3 until 9)
    }

    @Test
    fun headwordContainingTheWholeQueryHighlightsAllOfIt() {
        assertThat(Highlighter.headwordRange(tashiDelek, "ཤིས་བདེ")).isEqualTo(4 until 11)
    }

    @Test
    fun containsPrefersAMatchAtASyllableOrWordStart() {
        // ས is also the final letter of ཤིས; the syllable ས at index 8 is the real hit.
        assertThat(Highlighter.headwordRange("བཀྲ་ཤིས་ས་", "ས")).isEqualTo(8 until 9)
        assertThat(Highlighter.headwordRange("heart; art", "art")).isEqualTo(7 until 10)
        // Without a word-start match the first occurrence still counts.
        assertThat(Highlighter.headwordRange("heart", "art")).isEqualTo(2 until 5)
    }

    @Test
    fun headwordWithoutMatchIsNull() {
        assertThat(Highlighter.headwordRange(tashi, "ལེགས")).isNull()
        assertThat(Highlighter.headwordRange("lead", "")).isNull()
        assertThat(Highlighter.headwordRange("lead", "   ")).isNull()
        assertThat(Highlighter.headwordRange("lead", "\u0F0B")).isNull()
    }

    // Gloss

    @Test
    fun glossHighlightsTheFirstToken() {
        assertThat(Highlighter.glossRange("auspiciousness; good fortune", "fortune")).isEqualTo(21 until 28)
        assertThat(Highlighter.glossRange("Good fortune", "good luck")).isEqualTo(0 until 4)
        assertThat(Highlighter.glossRange("unlucky; luck", "luck")).isEqualTo(9 until 13)
        assertThat(Highlighter.glossRange("སྐལ་བ་། བསོད་ནམས་", "བསོད")).isEqualTo(8 until 12)
    }

    @Test
    fun glossWithoutMatchIsNull() {
        assertThat(Highlighter.glossRange("tree", "fortune")).isNull()
        assertThat(Highlighter.glossRange("tree", "")).isNull()
        assertThat(Highlighter.glossRange("tree", "\"*")).isNull()
    }
}
