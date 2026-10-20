package com.kharagedition.tibetandictionary.data.search

/**
 * Builds FTS4 MATCH expressions from a normalised query ([QueryNormalizer]). Callers MUST pass only these outputs,
 * never raw user text, to `EntryDao.ftsHeadword`/`ftsGloss`: an unbalanced quote, a bracket or a bare `OR`/`AND`/
 * `NOT`/`NEAR` in raw text makes SQLite throw "malformed MATCH expression".
 *
 * [tokens] approximates how the `unicode61` tokenizer (with `TibetanTokenChars`) splits the indexed text, so one token
 * is one Tibetan syllable or one word:
 * - token characters are letters, decimal digits (Tibetan digits too) and combining marks (Tibetan vowel signs,
 *   subjoined letters, anusvara, visarga, Latin diacritics);
 * - everything else separates tokens: whitespace, tsheg U+0F0B, non-breaking tsheg U+0F0C, shad U+0F0D/U+0F0E, and all
 *   punctuation and symbols, which strips the FTS operators `" * ( ) : ^ -` ("lead-in" gives "lead", "in", as in the
 *   index);
 * - empty tokens are dropped, so no expression contains an empty phrase (which would make an AND match nothing);
 * - only the first [MAX_TOKENS] tokens are kept: FTS4 rejects an AND/OR expression of 4096 or more terms ("FTS
 *   expression tree is too large"), and a long paste would only make the query slow.
 *
 * Known differences from `unicode61`, neither of which can produce bad syntax (such a query may miss, never throw):
 * SQLite keeps other numbers and private-use characters (No/Nl/Co, e.g. the Tibetan half digits U+0F2A..U+0F33 or `²`)
 * inside a token where the builder splits, and splits at combining marks that are neither Tibetan token chars nor Latin
 * diacritics (e.g. Devanagari vowel signs) where the builder keeps them.
 *
 * Every token is double-quoted, which turns the keywords `OR`, `AND`, `NOT` and `NEAR` into plain terms. The last token
 * gets `*` (prefix match: the user may still be typing) unless it is finished ([Parsed.lastFinished]: the text goes on
 * after it with a tsheg, a shad or whitespace), in which case it is matched exactly. The builders return null when
 * there are no tokens.
 */
object FtsQueryBuilder {
    /** Tokens kept from a query; later ones are ignored. */
    const val MAX_TOKENS = 32

    /**
     * A query's FTS [tokens], and whether the user has finished the last one: [lastFinished] when the text goes on
     * after it with a tsheg (U+0F0B, U+0F0C), a shad (U+0F0D, U+0F0E) or whitespace, as in "ཀ་བ་" or "go ".
     */
    data class Parsed(val tokens: List<String>, val lastFinished: Boolean)

    /**
     * [tokens] of [q], and whether its last token is finished, judged on [typed]: the text as typed when [q] is its
     * normalised form, because normalisation trims the space of "go " and strips a trailing shad.
     */
    fun parse(q: String, typed: String = q): Parsed {
        val tokens = tokens(q)
        return Parsed(tokens, lastFinished = tokens.isNotEmpty() && endsAfterAFinishedToken(typed))
    }

    private val MARK_TYPES = setOf(
        Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.ENCLOSING_MARK,
    )

    /** The query's first [MAX_TOKENS] FTS tokens in order; see the class rules. */
    fun tokens(q: String): List<String> {
        val tokens = mutableListOf<String>()
        var start = -1
        var i = 0
        while (i < q.length && tokens.size < MAX_TOKENS) {
            val codePoint = q.codePointAt(i)
            if (isTokenChar(codePoint)) {
                if (start < 0) start = i
            } else if (start >= 0) {
                tokens += q.substring(start, i)
                start = -1
            }
            i += Character.charCount(codePoint)
        }
        if (start >= 0 && tokens.size < MAX_TOKENS) tokens += q.substring(start)
        return tokens
    }

    /** All tokens as one phrase, in order and adjacent: `"tok1 tok2*"` (`"tok1 tok2"` when [lastFinished]). */
    fun phrase(tokens: List<String>, lastFinished: Boolean = false): String? =
        if (tokens.isEmpty()) null else tokens.joinToString(" ", "\"", prefixMark(lastFinished) + "\"")

    /** Every token, anywhere (implicit AND): `"tok1" "tok2*"` (no `*` when [lastFinished]). */
    fun allTerms(tokens: List<String>, lastFinished: Boolean = false): String? = terms(tokens, " ", lastFinished)

    /** Any token: `"tok1" OR "tok2*"` (no `*` when [lastFinished]). */
    fun anyTerm(tokens: List<String>, lastFinished: Boolean = false): String? = terms(tokens, " OR ", lastFinished)

    /** True for a letter, digit or combining mark: a character the FTS tokenizer keeps inside a token. */
    internal fun isTokenChar(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || Character.getType(codePoint).toByte() in MARK_TYPES

    private fun terms(tokens: List<String>, separator: String, lastFinished: Boolean): String? {
        if (tokens.isEmpty()) return null
        return tokens.withIndex().joinToString(separator) { (index, token) ->
            if (index == tokens.lastIndex) "\"$token${prefixMark(lastFinished)}\"" else "\"$token\""
        }
    }

    private fun prefixMark(lastFinished: Boolean) = if (lastFinished) "" else "*"

    /** Whether, after its last token character, [text] has a tsheg, a shad or whitespace among the characters left. */
    private fun endsAfterAFinishedToken(text: String): Boolean {
        var end = text.length
        while (end > 0) {
            val codePoint = text.codePointBefore(end)
            if (isTokenChar(codePoint)) return false
            if (codePoint in FINISHING_MARKS || Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                return true
            }
            end -= Character.charCount(codePoint)
        }
        return false
    }

    /** Tsheg, non-breaking tsheg, shad and nyis shad. */
    private val FINISHING_MARKS = setOf(0x0F0B, 0x0F0C, 0x0F0D, 0x0F0E)
}
