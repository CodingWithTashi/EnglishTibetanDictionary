package com.kharagedition.tibetandictionary.data.search

import com.kharagedition.tibetandictionary.core.text.TibetanScript

/**
 * Char ranges (into the displayed string) to highlight in a search hit. Queries are [QueryNormalizer] outputs; every
 * function returns null when nothing should be highlighted, never an empty range, and a range never ends inside a
 * stack: when the next character is a combining mark (a Tibetan vowel sign, subjoined letter or other mark, or a
 * Latin combining accent) the range is extended over it, so "བཀ" against "བཀྲ་ཤིས་" gives `0 until 3`.
 *
 * "Find" below means: the first occurrence, ignoring case, that starts a token (at index 0 or after a character that
 * is not a letter, digit or mark: a tsheg, shad, space or punctuation), else the first occurrence anywhere. A token
 * start is preferred because that is where the FTS match was: ས in "བཀྲ་ཤིས་ས་" is the last syllable, not the final
 * letter of ཤིས.
 */
object Highlighter {
    private val WORD = Regex("\\S+")
    private val MARKS =
        setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK)

    /**
     * Headword match ([com.kharagedition.tibetandictionary.data.model.MatchField.HEADWORD]): with the query's trailing
     * tsheg stripped, `0 until query.length` when the headword starts with it; else the whole query found inside;
     * else the query's first FTS token ([FtsQueryBuilder.tokens]) found inside.
     */
    fun headwordRange(headword: String, query: String): IntRange? {
        val q = TibetanScript.stripTrailingTsheg(query.trim())
        if (q.isEmpty()) return null
        if (headword.startsWith(q, ignoreCase = true)) return (0 until q.length).overMarks(headword)
        return (find(headword, q) ?: firstToken(q)?.let { find(headword, it) })?.overMarks(headword)
    }

    /** Wylie match: `0 until query.length` when [wylie] starts with [query] (ignoring case), else null. */
    fun wylieRange(wylie: String, query: String): IntRange? =
        if (query.isNotEmpty() && wylie.startsWith(query, ignoreCase = true)) 0 until query.length else null

    /**
     * The Tibetan headword part a Wylie query covers: its first k syllables, where k is the number of complete
     * space-separated Wylie syllables in [wylieQuery] plus one when it ends mid-syllable (no trailing space), capped at
     * the headword's syllable count. "bkra" and "bkra " cover བཀྲ; "bkra sh" covers བཀྲ་ཤིས. The range runs from 0 to
     * the end of the k-th syllable, without its tsheg. Syllables are runs of letters, digits and marks.
     */
    fun headwordRangeForWylie(headword: String, wylieQuery: String): IntRange? {
        val k = WORD.findAll(wylieQuery).count()
        if (k == 0) return null
        var syllables = 0
        var end = -1
        var inSyllable = false
        var i = 0
        while (i < headword.length) {
            val codePoint = headword.codePointAt(i)
            val next = i + Character.charCount(codePoint)
            if (FtsQueryBuilder.isTokenChar(codePoint)) {
                if (!inSyllable) syllables++
                inSyllable = true
                end = next
            } else {
                if (inSyllable && syllables == k) break
                inSyllable = false
            }
            i = next
        }
        return if (end > 0) 0 until end else null
    }

    /** Gloss match: the query's first FTS token found in [glossPreview], else null. */
    fun glossRange(glossPreview: String, query: String): IntRange? =
        firstToken(query)?.let { find(glossPreview, it) }?.overMarks(glossPreview)

    private fun firstToken(query: String): String? = FtsQueryBuilder.tokens(query).firstOrNull()

    /** This range extended over the combining marks that directly follow it in [text]. */
    private fun IntRange.overMarks(text: String): IntRange {
        var end = last + 1
        while (end < text.length && text[end].category in MARKS) end++
        return first until end
    }

    private fun find(text: String, needle: String): IntRange? {
        var first = -1
        var from = 0
        while (true) {
            val index = text.indexOf(needle, from, ignoreCase = true)
            if (index < 0) break
            if (first < 0) first = index
            val atTokenStart = index == 0 || !FtsQueryBuilder.isTokenChar(text.codePointBefore(index))
            if (atTokenStart) return index until index + needle.length
            from = index + 1
        }
        return if (first < 0) null else first until first + needle.length
    }
}
