package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.domain.InputScript.HAN
import com.kharagedition.tibetandictionary.domain.InputScript.LATIN
import com.kharagedition.tibetandictionary.domain.InputScript.TIBETAN
import com.kharagedition.tibetandictionary.domain.InputScript.WYLIE
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
class DetectScriptTest {

    @RunWith(Parameterized::class)
    class Detect(private val query: String, private val expected: InputScript) {

        @Test
        fun detect() {
            assertThat(DetectScript.detect(query)).isEqualTo(expected)
        }

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "\"{0}\" -> {1}")
            fun cases(): List<Array<Any>> = listOf(
                // Pinned cases (§7.5).
                arrayOf("བཀྲ", TIBETAN),
                arrayOf("bkra", WYLIE),
                arrayOf("bkra shis", WYLIE),
                arrayOf("snying rje", WYLIE),
                arrayOf("'jam dpal", WYLIE),
                arrayOf("compassion", LATIN),
                arrayOf("pa", LATIN),
                arrayOf("Love", LATIN),
                arrayOf("", LATIN),
                arrayOf("bkra shis bde legs", WYLIE),
                arrayOf("sangs rgyas", WYLIE),
                arrayOf("tashi delek", LATIN),
                arrayOf("the", LATIN),
                arrayOf("Bkra", WYLIE),
                arrayOf("12", LATIN),
                // Any Tibetan-block character wins, even mixed with Latin or Han (Review Focus 1).
                arrayOf("བཀྲ shis", TIBETAN),
                arrayOf("བཀྲ་ཤིས།", TIBETAN),
                arrayOf("༤༢", TIBETAN),
                arrayOf("བོད 中", TIBETAN),
                // Han ideographs.
                arrayOf("中", HAN),
                arrayOf("中文 zhong", HAN),
                // Wylie: an apostrophe anywhere, or two particles from the lexicon.
                arrayOf("pa'i", WYLIE),
                arrayOf("pa la", WYLIE),
                arrayOf("yin red", WYLIE),
                arrayOf("pa love", LATIN),
                // Typographic apostrophes count as the ASCII one.
                arrayOf("’jam dpal", WYLIE),
                arrayOf("ʼjam dpal", WYLIE),
                arrayOf("‘jam dpal", WYLIE),
                arrayOf("pa’i", WYLIE),
                // Upper case and extra whitespace are ignored.
                arrayOf("BKRA SHIS", WYLIE),
                arrayOf("  bkra   shis  ", WYLIE),
                // A token outside [a-z'+.~-] makes the whole query Latin.
                arrayOf("bkra shis 2", LATIN),
                arrayOf("bkra, shis", LATIN),
                // Blank.
                arrayOf("   ", LATIN),
            )
        }
    }

    class Tokens {

        @Test
        fun tokensAreLowerCasedWithTypographicApostrophesFolded() {
            assertThat(DetectScript.tokens("  Pa’i  KYI ʼdug ‘jam "))
                .containsExactly("pa'i", "kyi", "'dug", "'jam")
                .inOrder()
            assertThat(DetectScript.tokens("   ")).isEmpty()
        }

        @Test
        fun aFoldedTokenMatchesTheLexicon() {
            val inLexicon = DetectScript.tokens("pa’i ʼdug").filter { it in WylieLexicon }

            assertThat(inLexicon).containsExactly("pa'i", "'dug")
        }
    }
}
