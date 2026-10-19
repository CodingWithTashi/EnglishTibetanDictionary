package com.kharagedition.tibetandictionary.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrefixBoundsTest {

    /**
     * SQLite's text order: unsigned UTF-8 bytes, which is code-point order (NOCASE only folds ASCII on top).
     * Kotlin's String.compareTo uses UTF-16 units and would put U+E000..U+FFFF above any surrogate pair.
     */
    private fun sqliteCompare(a: String, b: String): Int {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(x.size, y.size)) {
            val diff = (x[i].toInt() and 0xFF) - (y[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return x.size - y.size
    }

    private fun inRange(value: String, prefix: String): Boolean =
        sqliteCompare(value, prefix) >= 0 && sqliteCompare(value, PrefixBounds.upper(prefix)) < 0

    @Test
    fun upperIsThePrefixFollowedByTheLastCodePoint() {
        val upper = PrefixBounds.upper("བཀྲ")

        assertThat(upper).startsWith("བཀྲ")
        assertThat(upper.codePoints().toArray().last()).isEqualTo(0x10FFFF)
        assertThat(upper.codePointCount(0, upper.length)).isEqualTo(4)
    }

    @Test
    fun tibetanUpperSortsAboveEveryWordWithThePrefix() {
        val prefix = "བཀྲ"
        val withPrefix = listOf(
            "བཀྲ",
            "བཀྲ་",
            "བཀྲ་ཤིས་",
            "བཀྲག་",
            "བཀྲ་ཤིས་བདེ་ལེགས་",
            prefix + "\uE000",
            prefix + "\uFFFD",
            prefix + "\uD83D\uDE00",
            prefix + "\uDBFF\uDFFE",
        )

        assertThat(sqliteCompare(PrefixBounds.upper(prefix), "བཀྲ་ཤིས་")).isGreaterThan(0)
        for (word in withPrefix) assertThat(inRange(word, prefix)).isTrue()
        // Neighbours without the prefix: a different subjoined letter, the next root letter, a shorter stem.
        for (word in listOf("བཀྱེ་", "བཁ་", "བཀ", "ཀ་")) assertThat(inRange(word, prefix)).isFalse()
    }

    @Test
    fun asciiUpperExcludesTheNextWord() {
        for (word in listOf("lead", "leader", "lead astray")) assertThat(inRange(word, "lead")).isTrue()
        for (word in listOf("leaf", "leae", "lea", "le")) assertThat(inRange(word, "lead")).isFalse()
    }

    @Test
    fun emptyPrefixCoversEveryString() {
        for (word in listOf("", "a", "བཀྲ", "\uFFFD", "\uD83D\uDE00")) assertThat(inRange(word, "")).isTrue()
    }
}
