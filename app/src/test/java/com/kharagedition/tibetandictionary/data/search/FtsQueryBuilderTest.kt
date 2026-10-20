package com.kharagedition.tibetandictionary.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryBuilderTest {

    private fun tokens(q: String) = FtsQueryBuilder.tokens(q)

    @Test
    fun tibetanSplitsOnTshegAndShad() {
        assertThat(tokens("བཀྲ་ཤི")).containsExactly("བཀྲ", "ཤི").inOrder()
        assertThat(tokens("བཀྲ\u0F0Cཤིས\u0F0Dབདེ\u0F0Eལེགས")).containsExactly("བཀྲ", "ཤིས", "བདེ", "ལེགས").inOrder()
        // A trailing tsheg yields no empty token.
        assertThat(tokens("བཀྲ་ཤིས་")).containsExactly("བཀྲ", "ཤིས").inOrder()
    }

    @Test
    fun syllableMarksStayInsideTheToken() {
        // Subjoined letters, vowel signs, anusvara and visarga are marks, not separators.
        assertThat(tokens("སྒྲུབ་པ")).containsExactly("སྒྲུབ", "པ").inOrder()
        assertThat(tokens("ཨོཾ་ཀཿ")).containsExactly("ཨོཾ", "ཀཿ").inOrder()
        // A Latin combining accent (decomposed e + U+0301) stays too.
        assertThat(tokens("cafe\u0301")).containsExactly("cafe\u0301")
    }

    @Test
    fun tibetanDigitsAreTokens() {
        assertThat(tokens("༡")).containsExactly("༡")
        assertThat(FtsQueryBuilder.phrase(tokens("༡"))).isEqualTo("\"༡*\"")
        assertThat(tokens("༡༢་༣")).containsExactly("༡༢", "༣").inOrder()
    }

    @Test
    fun latinSplitsOnWhitespace() {
        assertThat(tokens("lead  astray")).containsExactly("lead", "astray").inOrder()
        assertThat(tokens(" lead\tastray ")).containsExactly("lead", "astray").inOrder()
    }

    @Test
    fun allTermsQuotesEachTokenAndPrefixesTheLast() {
        assertThat(FtsQueryBuilder.allTerms(tokens("བཀྲ་ཤི"))).isEqualTo("\"བཀྲ\" \"ཤི*\"")
        assertThat(FtsQueryBuilder.allTerms(tokens("lead  astray"))).isEqualTo("\"lead\" \"astray*\"")
        assertThat(FtsQueryBuilder.allTerms(listOf("lead"))).isEqualTo("\"lead*\"")
    }

    @Test
    fun phraseQuotesAllTokensTogether() {
        assertThat(FtsQueryBuilder.phrase(tokens("བཀྲ་ཤི"))).isEqualTo("\"བཀྲ ཤི*\"")
        assertThat(FtsQueryBuilder.phrase(tokens("lead astray"))).isEqualTo("\"lead astray*\"")
        assertThat(FtsQueryBuilder.phrase(listOf("ཤིས"))).isEqualTo("\"ཤིས*\"")
    }

    @Test
    fun anyTermJoinsWithOr() {
        assertThat(FtsQueryBuilder.anyTerm(tokens("lead astray"))).isEqualTo("\"lead\" OR \"astray*\"")
        assertThat(FtsQueryBuilder.anyTerm(tokens("བཀྲ་ཤིས་བདེ"))).isEqualTo("\"བཀྲ\" OR \"ཤིས\" OR \"བདེ*\"")
        assertThat(FtsQueryBuilder.anyTerm(listOf("lead"))).isEqualTo("\"lead*\"")
    }

    @Test
    fun operatorsAndQuotesAreStripped() {
        assertThat(tokens("\"lead\" -astray (go*) col:^x"))
            .containsExactly("lead", "astray", "go", "col", "x").inOrder()
        assertThat(FtsQueryBuilder.allTerms(tokens("\"lead* -astray"))).isEqualTo("\"lead\" \"astray*\"")
        // The FTS tokenizer splits "lead-in" and "o'clock" in the index, so the query splits there too.
        assertThat(tokens("lead-in")).containsExactly("lead", "in").inOrder()
        assertThat(tokens("o'clock")).containsExactly("o", "clock").inOrder()
    }

    @Test
    fun keywordsAreNeutralisedByQuoting() {
        assertThat(FtsQueryBuilder.allTerms(tokens("lead OR astray AND NOT")))
            .isEqualTo("\"lead\" \"OR\" \"astray\" \"AND\" \"NOT*\"")
        assertThat(FtsQueryBuilder.anyTerm(tokens("NEAR/2"))).isEqualTo("\"NEAR\" OR \"2*\"")
    }

    @Test
    fun punctuationOnlyTokensAreDropped() {
        // An empty quoted phrase would make an AND query match nothing.
        assertThat(tokens("lead . , !")).containsExactly("lead")
        // Head marks U+0F04 U+0F05 and an emoji (a surrogate pair) separate like punctuation.
        assertThat(tokens("\u0F04\u0F05ཀ")).containsExactly("ཀ")
        assertThat(tokens("lead \uD83D\uDE00")).containsExactly("lead")
    }

    @Test
    fun onlyTheFirstTokensAreKept() {
        val words = (1..40).map { "w$it" }
        val kept = words.take(FtsQueryBuilder.MAX_TOKENS)
        val pasted = tokens("a ".repeat(5000))

        assertThat(tokens(words.joinToString(" "))).containsExactlyElementsIn(kept).inOrder()
        assertThat(tokens(kept.joinToString(" "))).containsExactlyElementsIn(kept).inOrder()
        assertThat(pasted).hasSize(FtsQueryBuilder.MAX_TOKENS)
        assertThat(FtsQueryBuilder.allTerms(pasted)).endsWith(" \"a*\"")
    }

    @Test
    fun aFinishedLastTokenIsMatchedExactly() {
        val kawaDone = FtsQueryBuilder.parse("ཀ་བ་")
        val kawa = FtsQueryBuilder.parse("ཀ་བ")
        val goDone = FtsQueryBuilder.parse("go ")
        val go = FtsQueryBuilder.parse("go")

        // A tsheg after the last syllable: the user has finished it, so it is matched exactly.
        assertThat(kawaDone).isEqualTo(FtsQueryBuilder.Parsed(listOf("ཀ", "བ"), lastFinished = true))
        assertThat(FtsQueryBuilder.phrase(kawaDone.tokens, kawaDone.lastFinished)).isEqualTo("\"ཀ བ\"")
        assertThat(FtsQueryBuilder.allTerms(kawaDone.tokens, kawaDone.lastFinished)).isEqualTo("\"ཀ\" \"བ\"")
        assertThat(FtsQueryBuilder.anyTerm(kawaDone.tokens, kawaDone.lastFinished)).isEqualTo("\"ཀ\" OR \"བ\"")
        // Still typing: the last token stays a prefix.
        assertThat(kawa).isEqualTo(FtsQueryBuilder.Parsed(listOf("ཀ", "བ"), lastFinished = false))
        assertThat(FtsQueryBuilder.phrase(kawa.tokens, kawa.lastFinished)).isEqualTo("\"ཀ བ*\"")
        // Latin: a space finishes the word.
        assertThat(goDone).isEqualTo(FtsQueryBuilder.Parsed(listOf("go"), lastFinished = true))
        assertThat(FtsQueryBuilder.phrase(goDone.tokens, goDone.lastFinished)).isEqualTo("\"go\"")
        assertThat(go.lastFinished).isFalse()
        assertThat(FtsQueryBuilder.allTerms(go.tokens, go.lastFinished)).isEqualTo("\"go*\"")
    }

    @Test
    fun finishingIsJudgedOnTheTextAsTyped() {
        // Normalisation trims "go " to "go" and strips a trailing shad; the typed text still shows the end.
        assertThat(FtsQueryBuilder.parse("go", typed = "go ").lastFinished).isTrue()
        assertThat(FtsQueryBuilder.parse("ཀ་བ", typed = "ཀ་བ།").lastFinished).isTrue()
        assertThat(FtsQueryBuilder.parse("ཀ་བ", typed = "ཀ་བ༌").lastFinished).isTrue()
        assertThat(FtsQueryBuilder.parse("lead", typed = "lead ").lastFinished).isTrue()
        // Other punctuation does not finish a word, and without tokens nothing is finished.
        assertThat(FtsQueryBuilder.parse("lead-").lastFinished).isFalse()
        assertThat(FtsQueryBuilder.parse("lead\"").lastFinished).isFalse()
        assertThat(FtsQueryBuilder.parse("go - ").lastFinished).isTrue()
        assertThat(FtsQueryBuilder.parse("་")).isEqualTo(FtsQueryBuilder.Parsed(emptyList(), lastFinished = false))
        assertThat(FtsQueryBuilder.parse(" ").lastFinished).isFalse()
    }

    @Test
    fun noTokensGiveNull() {
        assertThat(tokens("")).isEmpty()
        assertThat(tokens("  \u0F0B \u0F0D \"*():^-")).isEmpty()
        assertThat(FtsQueryBuilder.phrase(emptyList())).isNull()
        assertThat(FtsQueryBuilder.allTerms(emptyList())).isNull()
        assertThat(FtsQueryBuilder.anyTerm(emptyList())).isNull()
    }
}
