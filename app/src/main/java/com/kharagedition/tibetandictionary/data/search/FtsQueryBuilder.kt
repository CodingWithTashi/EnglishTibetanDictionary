package com.kharagedition.tibetandictionary.data.search

/**
 * Builds FTS4 MATCH expressions from a normalised query ([QueryNormalizer]). Callers MUST pass only these outputs,
 * never raw user text, to `EntryDao.ftsHeadword`/`ftsGloss`: an unbalanced quote, a bracket or a bare `OR`/`AND`/
 * `NOT`/`NEAR` in raw text makes SQLite throw "malformed MATCH expression".
 *
 * [tokens] mirrors how the `unicode61` tokenizer (with `TibetanTokenChars`) splits the indexed text, so one token is
 * one Tibetan syllable or one word:
 * - token characters are letters, digits and combining marks (Tibetan vowel signs, subjoined letters, anusvara,
 *   visarga, Latin diacritics);
 * - everything else separates tokens: whitespace, tsheg U+0F0B, non-breaking tsheg U+0F0C, shad U+0F0D/U+0F0E, and all
 *   punctuation and symbols, which strips the FTS operators `" * ( ) : ^ -` ("lead-in" gives "lead", "in", as in the
 *   index);
 * - empty tokens are dropped, so no expression contains an empty phrase (which would make an AND match nothing).
 *
 * Every token is double-quoted, which turns the keywords `OR`, `AND`, `NOT` and `NEAR` into plain terms, and the last
 * token gets `*` (prefix match: the user may still be typing). The builders return null when there are no tokens.
 */
object FtsQueryBuilder {
    private val MARK_TYPES = setOf(
        Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.ENCLOSING_MARK,
    )

    /** The query's FTS tokens in order; see the class rules. */
    fun tokens(q: String): List<String> {
        val tokens = mutableListOf<String>()
        var start = -1
        var i = 0
        while (i < q.length) {
            val codePoint = q.codePointAt(i)
            if (isTokenChar(codePoint)) {
                if (start < 0) start = i
            } else if (start >= 0) {
                tokens += q.substring(start, i)
                start = -1
            }
            i += Character.charCount(codePoint)
        }
        if (start >= 0) tokens += q.substring(start)
        return tokens
    }

    /** All tokens as one phrase, in order and adjacent: `"tok1 tok2*"`. */
    fun phrase(tokens: List<String>): String? =
        if (tokens.isEmpty()) null else tokens.joinToString(" ", prefix = "\"", postfix = "*\"")

    /** Every token, anywhere (implicit AND): `"tok1" "tok2*"`. */
    fun allTerms(tokens: List<String>): String? = terms(tokens, separator = " ")

    /** Any token: `"tok1" OR "tok2*"`. */
    fun anyTerm(tokens: List<String>): String? = terms(tokens, separator = " OR ")

    /** True for a letter, digit or combining mark: a character the FTS tokenizer keeps inside a token. */
    internal fun isTokenChar(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || Character.getType(codePoint).toByte() in MARK_TYPES

    private fun terms(tokens: List<String>, separator: String): String? {
        if (tokens.isEmpty()) return null
        return tokens.withIndex().joinToString(separator) { (index, token) ->
            if (index == tokens.lastIndex) "\"$token*\"" else "\"$token\""
        }
    }
}
