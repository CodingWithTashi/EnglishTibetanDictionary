package com.kharagedition.tibetandictionary.data.local.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TibetanTokenCharsTest {

    @Test
    fun argListsTheTibeticMarksAndSubjoinedLetters() {
        val expected = listOf(0x0F18, 0x0F19, 0x0F35, 0x0F37, 0x0F39, 0x0F3E, 0x0F3F) +
            (0x0F71..0x0F84) + listOf(0x0F86, 0x0F87) + (0x0F8D..0x0F97) + (0x0F99..0x0FBC) + listOf(0x0FC6)

        val arg = TibetanTokenChars.ARG
        val chars = arg.removePrefix("tokenchars=")

        assertThat(arg).startsWith("tokenchars=")
        assertThat(chars.codePoints().toArray().toList()).containsExactlyElementsIn(expected).inOrder()
        assertThat(expected).hasSize(77)
    }

    @Test
    fun separatorsAreNotTokenChars() {
        val chars = TibetanTokenChars.ARG.removePrefix("tokenchars=")

        // Tsheg, non-breaking tsheg and shad must keep splitting syllables.
        assertThat(chars).doesNotContain("་")
        assertThat(chars).doesNotContain("༌")
        assertThat(chars).doesNotContain("།")
    }
}
