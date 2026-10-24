package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.translation.Lang
import org.junit.Test

class PhraseSegmenterTest {

    private val phrase = "ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ།"

    @Test
    fun `Tibetan candidates are every run of one to four syllables`() {
        val candidates = PhraseSegmenter.candidates(phrase, Lang.BO)

        assertThat(candidates).containsAtLeast("ཁྱེད་རང", "སྐུ་གཟུགས", "ཁྱེད", "བདེ་པོ", "ཁྱེད་རང་སྐུ་གཟུགས")
        assertThat(candidates.maxOf { it.split('་').size }).isEqualTo(4)
        assertThat(candidates).containsNoDuplicates()
        // The shad ends the last syllable; it is not part of a candidate.
        assertThat(candidates.none { it.endsWith("།") }).isTrue()
    }

    @Test
    fun `Tibetan runs do not cross a shad or a space`() {
        val candidates = PhraseSegmenter.candidates("ཀ་ཁ། ག་ང", Lang.BO)

        assertThat(candidates).containsExactly("ཀ", "ཀ་ཁ", "ཁ", "ག", "ག་ང", "ང").inOrder()
    }

    @Test
    fun `the longest known run wins, left to right`() {
        val known = setOf("ཁྱེད་རང", "ཁྱེད", "སྐུ་གཟུགས", "བདེ་པོ")

        assertThat(PhraseSegmenter.segment(phrase, Lang.BO, known))
            .containsExactly("ཁྱེད་རང", "སྐུ་གཟུགས", "བདེ་པོ")
            .inOrder()
    }

    @Test
    fun `English words skip the stop-words`() {
        val known = setOf("happy", "beings", "may")

        assertThat(PhraseSegmenter.segment("May all beings be happy", Lang.EN, known))
            .containsExactly("beings", "happy")
            .inOrder()
        assertThat(PhraseSegmenter.candidates("May all beings, be happy!", Lang.EN))
            .containsExactly("beings", "happy")
            .inOrder()
    }

    @Test
    fun `a repeated word is listed once`() {
        assertThat(PhraseSegmenter.segment("water, water everywhere", Lang.EN, setOf("water", "everywhere")))
            .containsExactly("water", "everywhere")
            .inOrder()
    }

    @Test
    fun `at most six words are listed`() {
        val words = listOf("one", "two", "three", "four", "five", "six", "seven", "eight")

        val segmented = PhraseSegmenter.segment(words.joinToString(" "), Lang.EN, words.toSet())

        assertThat(segmented).containsExactlyElementsIn(words.take(6)).inOrder()
        assertThat(PhraseSegmenter.segment(words.joinToString(" "), Lang.EN, words.toSet(), max = 2))
            .containsExactly("one", "two")
            .inOrder()
    }
}
