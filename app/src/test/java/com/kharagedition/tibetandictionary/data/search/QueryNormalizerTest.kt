package com.kharagedition.tibetandictionary.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueryNormalizerTest {

    private fun normalize(raw: String) = QueryNormalizer.normalize(raw)

    @Test
    fun trailingShadIsStripped() {
        assertThat(normalize("བཀྲ་ཤིས།")).isEqualTo("བཀྲ་ཤིས")
        assertThat(normalize("བཀྲ་ཤིས\u0F0E")).isEqualTo("བཀྲ་ཤིས")
        assertThat(normalize("བཀྲ་ཤིས།།")).isEqualTo("བཀྲ་ཤིས")
        // Spaces around the shad go with it.
        assertThat(normalize("བཀྲ་ཤིས ། ")).isEqualTo("བཀྲ་ཤིས")
        // A tsheg before the shad is part of the text and stays.
        assertThat(normalize("ང་།")).isEqualTo("ང་")
        assertThat(normalize("།")).isEqualTo("")
    }

    @Test
    fun innerShadIsKept() {
        assertThat(normalize("ཀ། ཁ")).isEqualTo("ཀ། ཁ")
    }

    @Test
    fun nonBreakingTshegBecomesTsheg() {
        assertThat(normalize("བཀྲ\u0F0Cཤིས\u0F0C")).isEqualTo("བཀྲ\u0F0Bཤིས\u0F0B")
    }

    @Test
    fun whitespaceIsTrimmedAndCollapsed() {
        assertThat(normalize("  lead \t  astray\n")).isEqualTo("lead astray")
        assertThat(normalize("lead\u00A0astray")).isEqualTo("lead astray")
        assertThat(normalize("བཀྲ་  ཤིས")).isEqualTo("བཀྲ་ ཤིས")
    }

    @Test
    fun zeroWidthCharactersAreDropped() {
        assertThat(normalize("བཀྲ\u200B་ཤིས")).isEqualTo("བཀྲ་ཤིས")
        assertThat(normalize("\uFEFFlead")).isEqualTo("lead")
    }

    @Test
    fun latinIsLowercased() {
        assertThat(normalize("Bkra")).isEqualTo("bkra")
        assertThat(normalize("LEAD Astray")).isEqualTo("lead astray")
    }

    @Test
    fun textWithTibetanKeepsItsCase() {
        assertThat(normalize("བཀྲ་ཤིས")).isEqualTo("བཀྲ་ཤིས")
        assertThat(normalize("བཀྲ SHIS")).isEqualTo("བཀྲ SHIS")
    }

    /** Review Focus 1: mixed scripts, trailing shad, double spaces and 1-char input all normalise without throwing. */
    @Test
    fun reviewFocusOddQueries() {
        assertThat(normalize("བཀྲ shis")).isEqualTo("བཀྲ shis")
        assertThat(normalize("བཀྲ་ཤིས།")).isEqualTo("བཀྲ་ཤིས")
        assertThat(normalize("  a  ")).isEqualTo("a")
        assertThat(normalize("")).isEqualTo("")
        assertThat(normalize("   ")).isEqualTo("")
        assertThat(normalize("ཀ")).isEqualTo("ཀ")
    }
}
