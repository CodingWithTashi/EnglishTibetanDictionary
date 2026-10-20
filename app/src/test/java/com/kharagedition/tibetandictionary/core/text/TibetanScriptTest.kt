package com.kharagedition.tibetandictionary.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TibetanScriptTest {

    @Test
    fun isTibetanCoversLettersDigitsTshegAndShad() {
        assertThat(TibetanScript.isTibetan('ཀ')).isTrue()
        assertThat(TibetanScript.isTibetan('༤')).isTrue()
        assertThat(TibetanScript.isTibetan('་')).isTrue()
        assertThat(TibetanScript.isTibetan('།')).isTrue()
        assertThat(TibetanScript.isTibetan('a')).isFalse()
        assertThat(TibetanScript.isTibetan('4')).isFalse()
        assertThat(TibetanScript.isTibetan(' ')).isFalse()
        assertThat(TibetanScript.isTibetan('中')).isFalse()
    }

    @Test
    fun hasTibetanIsTrueWhenAnyCharacterIsTibetan() {
        assertThat(TibetanScript.hasTibetan("བཀྲ་ཤིས་")).isTrue()
        assertThat(TibetanScript.hasTibetan("བཀྲ shis")).isTrue()
        assertThat(TibetanScript.hasTibetan("bkra shis")).isFalse()
        assertThat(TibetanScript.hasTibetan("")).isFalse()
    }

    @Test
    fun ensureTshegAppendsAfterAFinalLetter() {
        assertThat(TibetanScript.ensureTsheg("ཀ་བ")).isEqualTo("ཀ་བ་")
        assertThat(TibetanScript.ensureTsheg("བཀྲ་ཤིས")).isEqualTo("བཀྲ་ཤིས་")
    }

    @Test
    fun ensureTshegLeavesTextThatNeedsNoTshegUnchanged() {
        assertThat(TibetanScript.ensureTsheg("ཀ་བ་")).isEqualTo("ཀ་བ་")
        assertThat(TibetanScript.ensureTsheg("ཀ་བ\u0F0C")).isEqualTo("ཀ་བ\u0F0C")
        assertThat(TibetanScript.ensureTsheg("ཀ་བ།")).isEqualTo("ཀ་བ།")
        assertThat(TibetanScript.ensureTsheg("༤༢")).isEqualTo("༤༢")
        assertThat(TibetanScript.ensureTsheg("house")).isEqualTo("house")
        assertThat(TibetanScript.ensureTsheg("")).isEqualTo("")
    }

    @Test
    fun ensureTshegLeavesVisargaAndPalutaEndingsUnchanged() {
        assertThat(TibetanScript.ensureTsheg("ཀཿ")).isEqualTo("ཀཿ")
        assertThat(TibetanScript.ensureTsheg("ཀ\u0F7F")).isEqualTo("ཀ\u0F7F")
        assertThat(TibetanScript.ensureTsheg("ཨ\u0F85")).isEqualTo("ཨ\u0F85")
        assertThat(TibetanScript.ensureTsheg("ཨོཾ")).isEqualTo("ཨོཾ་")
    }

    @Test
    fun stripTrailingTshegRemovesEveryTrailingTsheg() {
        assertThat(TibetanScript.stripTrailingTsheg("ཀ་བ་")).isEqualTo("ཀ་བ")
        assertThat(TibetanScript.stripTrailingTsheg("ཀ་བ་་")).isEqualTo("ཀ་བ")
        assertThat(TibetanScript.stripTrailingTsheg("ཀ་བ\u0F0C")).isEqualTo("ཀ་བ")
        assertThat(TibetanScript.stripTrailingTsheg("་")).isEqualTo("")
    }

    @Test
    fun stripTrailingTshegKeepsInnerTshegs() {
        assertThat(TibetanScript.stripTrailingTsheg("ཀ་བ")).isEqualTo("ཀ་བ")
        assertThat(TibetanScript.stripTrailingTsheg("house")).isEqualTo("house")
        assertThat(TibetanScript.stripTrailingTsheg("")).isEqualTo("")
    }

    @Test
    fun stripAndEnsureRoundTrip() {
        val headword = "བཀྲ་ཤིས་"
        assertThat(TibetanScript.ensureTsheg(TibetanScript.stripTrailingTsheg(headword))).isEqualTo(headword)
    }

    @Test
    fun runsSplitMixedText() {
        val text = "Good morning · བཀྲ་ཤིས་བདེ་ལེགས།"
        val runs = TibetanScript.runs(text)
        assertThat(runs).hasSize(1)
        assertThat(text.substring(runs[0].first, runs[0].last + 1)).isEqualTo("བཀྲ་ཤིས་བདེ་ལེགས།")
    }

    @Test
    fun runsJoinWhitespaceBetweenTibetan() {
        val text = "བཀྲ་ཤིས་ བདེ་ལེགས"
        assertThat(TibetanScript.runs(text)).containsExactly(text.indices)
    }

    @Test
    fun runsExcludeSurroundingLatinAndWhitespace() {
        assertThat(TibetanScript.runs("ab བཀྲ cd")).containsExactly(3..5)
        assertThat(TibetanScript.runs("EN → བོད")).containsExactly(5..7)
        assertThat(TibetanScript.runs("house")).isEmpty()
    }
}
