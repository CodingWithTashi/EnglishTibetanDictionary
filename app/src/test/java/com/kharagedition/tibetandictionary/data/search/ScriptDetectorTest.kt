package com.kharagedition.tibetandictionary.data.search

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.Script
import org.junit.Test

class ScriptDetectorTest {

    @Test
    fun latinQueryIsLatin() {
        assertThat(ScriptDetector.detect("bkra")).isEqualTo(Script.LATIN)
        assertThat(ScriptDetector.detect("lead astray")).isEqualTo(Script.LATIN)
        assertThat(ScriptDetector.detect("caf\u00E9")).isEqualTo(Script.LATIN)
    }

    @Test
    fun tibetanQueryIsTibetan() {
        assertThat(ScriptDetector.detect("བཀྲ")).isEqualTo(Script.TIBETAN)
        assertThat(ScriptDetector.detect("བཀྲ་ཤིས་")).isEqualTo(Script.TIBETAN)
    }

    @Test
    fun anyTibetanCharacterMakesTheQueryTibetan() {
        assertThat(ScriptDetector.detect("བཀྲ shis")).isEqualTo(Script.TIBETAN)
        assertThat(ScriptDetector.detect("bkra ཤིས")).isEqualTo(Script.TIBETAN)
        // A lone tsheg and the block edges U+0F00 / U+0FFF.
        assertThat(ScriptDetector.detect("\u0F0B")).isEqualTo(Script.TIBETAN)
        assertThat(ScriptDetector.detect("a\u0F00")).isEqualTo(Script.TIBETAN)
        assertThat(ScriptDetector.detect("a\u0FFF")).isEqualTo(Script.TIBETAN)
    }

    @Test
    fun cjkIdeographsAreHan() {
        assertThat(ScriptDetector.detect("\u4E2D\u6587")).isEqualTo(Script.HAN)
        assertThat(ScriptDetector.detect("tea \u8336")).isEqualTo(Script.HAN)
        // Unified Ideographs U+4E00..U+9FFF and Extension A U+3400..U+4DBF, edges included.
        assertThat(ScriptDetector.detect("\u4E00")).isEqualTo(Script.HAN)
        assertThat(ScriptDetector.detect("\u9FFF")).isEqualTo(Script.HAN)
        assertThat(ScriptDetector.detect("\u3400")).isEqualTo(Script.HAN)
        assertThat(ScriptDetector.detect("\u4DBF")).isEqualTo(Script.HAN)
    }

    @Test
    fun tibetanWinsOverHan() {
        assertThat(ScriptDetector.detect("\u4E2D བཀྲ")).isEqualTo(Script.TIBETAN)
    }

    @Test
    fun blankDigitsAndPunctuationAreLatin() {
        assertThat(ScriptDetector.detect("  ")).isEqualTo(Script.LATIN)
        assertThat(ScriptDetector.detect("")).isEqualTo(Script.LATIN)
        assertThat(ScriptDetector.detect("42")).isEqualTo(Script.LATIN)
        assertThat(ScriptDetector.detect("?!")).isEqualTo(Script.LATIN)
        // Just outside the CJK ranges.
        assertThat(ScriptDetector.detect("\u33FF\u4DC0\uA000")).isEqualTo(Script.LATIN)
    }
}
