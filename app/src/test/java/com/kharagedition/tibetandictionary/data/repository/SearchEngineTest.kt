package com.kharagedition.tibetandictionary.data.repository

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.search.FtsQueryBuilder
import org.junit.Test

/**
 * The pure rules of the search pipeline: the full-text guard and the did-you-mean prefix candidates. The database steps
 * are covered by [DictionaryRepositoryImplTest].
 */
class SearchEngineTest {

    private fun fullText(q: String) = fullTextAllowed(FtsQueryBuilder.tokens(q))

    @Test
    fun fullTextWaitsForThreeLatinLetters() {
        assertThat(fullText("a")).isFalse()
        assertThat(fullText("ba")).isFalse()
        assertThat(fullText("bal")).isTrue()
        assertThat(fullText("pillow")).isTrue()
        // Only the last token counts: it is the one matched as a prefix.
        assertThat(fullText("bkra sh")).isFalse()
        assertThat(fullText("sh bkra")).isTrue()
        assertThat(fullText("zzz wo-")).isFalse()
        // No tokens, no full-text step.
        assertThat(fullText("")).isFalse()
        assertThat(fullText("* ( -")).isFalse()
    }

    @Test
    fun fullTextWaitsForATibetanSyllableOfTwoCodePoints() {
        assertThat(fullText("ཀ")).isFalse()
        assertThat(fullText("ཀ་བ")).isFalse()
        // A tsheg separates tokens; it does not make the last one longer.
        assertThat(fullText("ཀ་བ་")).isFalse()
        assertThat(fullText("བཀ")).isTrue()
        // A vowel sign or a subjoined letter is a code point of the syllable.
        assertThat(fullText("ཀུ")).isTrue()
        assertThat(fullText("བཀྲ་ཤིས")).isTrue()
        // Mixed input: the script of the last token decides.
        assertThat(fullText("shis བཀ")).isTrue()
        assertThat(fullText("བཀྲ sh")).isFalse()
        // Han, like Tibetan, writes a syllable per code point.
        assertThat(fullText("水")).isFalse()
        assertThat(fullText("水牛")).isTrue()
    }

    @Test
    fun tibetanCutsToThePreviousTshegThenDropsCharacters() {
        assertThat(shortenedPrefixes("བཀྲ་ཤིསས", Script.TIBETAN))
            .containsExactly("བཀྲ་", "བཀྲ", "བཀ", "བ").inOrder()
        // A trailing tsheg is not the "previous" one; at most four characters are dropped after the cut.
        assertThat(shortenedPrefixes("བཀྲ་ཤིས་བདེ་", Script.TIBETAN))
            .containsExactly("བཀྲ་ཤིས་", "བཀྲ་ཤིས", "བཀྲ་ཤི", "བཀྲ་ཤ", "བཀྲ་").inOrder()
    }

    @Test
    fun tibetanWithoutInnerTshegDropsCharacters() {
        assertThat(shortenedPrefixes("བཀྲ", Script.TIBETAN)).containsExactly("བཀ", "བ").inOrder()
        assertThat(shortenedPrefixes("བཀྲ་", Script.TIBETAN)).containsExactly("བཀྲ", "བཀ", "བ").inOrder()
        // Mixed input: spaces left at the end of a candidate are trimmed.
        assertThat(shortenedPrefixes("བཀྲ shis", Script.TIBETAN))
            .containsExactly("བཀྲ shi", "བཀྲ sh", "བཀྲ s", "བཀྲ").inOrder()
        assertThat(shortenedPrefixes("་", Script.TIBETAN)).isEmpty()
    }

    @Test
    fun latinDropsUpToFourCharacters() {
        assertThat(shortenedPrefixes("pillar", Script.LATIN)).containsExactly("pilla", "pill", "pil", "pi").inOrder()
        assertThat(shortenedPrefixes("bkra s", Script.LATIN)).containsExactly("bkra", "bkr", "bk").inOrder()
        assertThat(shortenedPrefixes("ab", Script.LATIN)).containsExactly("a")
        assertThat(shortenedPrefixes("a", Script.LATIN)).isEmpty()
        assertThat(shortenedPrefixes("", Script.LATIN)).isEmpty()
    }
}
