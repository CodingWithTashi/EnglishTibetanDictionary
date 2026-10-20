package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.core.text.TibetanScript

/**
 * Root-letter clusters (prefix, superscript, aspirated or digraph root) that open a Tibetan syllable in Wylie. A
 * lower-cased token starting with one of them marks the query as Wylie. Several are shared with English (st, sp,
 * sk, sm, sn, bl, br, dr, gn, gy, ny), so words like "street" or "blue" detect as Wylie too; the hit counts in
 * [ResolveDirection] then decide the direction.
 */
internal val TibetanOnsets: Set<String> = setOf(
    "bk", "bg", "bd", "bs", "bz", "br", "bl",
    "dk", "dg", "db", "dm", "dp", "dr",
    "gc", "gn", "gt", "gd", "gz", "gy", "gs",
    "rk", "rg", "rng", "rj", "rny", "rt", "rd", "rn", "rb", "rm", "rts", "rdz",
    "sk", "sg", "sng", "sny", "st", "sd", "sn", "sp", "sb", "sm", "sts",
    "lk", "lg", "lng", "lc", "lj", "lt", "ld", "lp", "lb", "lh",
    "mkh", "mg", "mng", "mch", "mj", "mny", "mth", "md", "mn", "mtsh", "mdz",
    "'kh", "'g", "'ch", "'j", "'d", "'ph", "'b", "'tsh", "'dz",
    "tsh", "dz", "zh", "ng", "ny",
)

/**
 * Common Wylie particles and short words. One alone ("pa", "la") could be English or a typo; two or more in a
 * query mark it as Wylie.
 */
internal val WylieLexicon: Set<String> = setOf(
    "pa", "ba", "ma", "po", "bo", "mo", "pa'i", "ba'i",
    "kyi", "gyi", "gi", "las", "la", "na", "dang", "yin", "red", "'dug", "yod",
)

/** Decides which [InputScript] a search query is written in. Pure; the query need not be normalised. */
object DetectScript {

    /** Characters a Wylie token may contain once lower-cased. */
    private val WylieToken = Regex("^[a-z'+.~-]+$")

    private val Whitespace = Regex("\\s+")

    /**
     * Apostrophe look-alikes that keyboards with smart punctuation type for the Wylie `'` (a-chung): right and
     * left single quotation marks (U+2019, U+2018) and the modifier letter apostrophe (U+02BC).
     */
    private val TypographicApostrophes = charArrayOf('’', '‘', 'ʼ')

    /** Lexicon tokens needed when no onset cluster or apostrophe already marks the query as Wylie. */
    private const val MIN_LEXICON_TOKENS = 2

    /**
     * The script of [q], by the first rule that applies:
     * 1. **Tibetan**: any character in the Tibetan block (U+0F00–U+0FFF), even mixed with Latin ("བཀྲ shis").
     * 2. **Han**: any Han ideograph (CJK unified or compatibility, any plane).
     * 3. **Wylie**: every one of the [tokens] matches `^[a-z'+.~-]+$`, and either a token starts with one of
     *    [TibetanOnsets], or a token contains `'`, or at least two tokens are in [WylieLexicon].
     * 4. **Latin**: everything else, including a blank query.
     */
    fun detect(q: String): InputScript = when {
        TibetanScript.hasTibetan(q) -> InputScript.TIBETAN
        hasHan(q) -> InputScript.HAN
        isWylie(q) -> InputScript.WYLIE
        else -> InputScript.LATIN
    }

    /** [q] lower-cased, with typographic apostrophes folded to `'`, split on whitespace; no empty tokens. */
    internal fun tokens(q: String): List<String> {
        var folded = q.lowercase()
        for (c in TypographicApostrophes) folded = folded.replace(c, '\'')
        return folded.split(Whitespace).filter { it.isNotEmpty() }
    }

    private fun hasHan(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) return true
            i += Character.charCount(codePoint)
        }
        return false
    }

    private fun isWylie(text: String): Boolean {
        val tokens = tokens(text)
        if (tokens.isEmpty() || !tokens.all(WylieToken::matches)) return false
        return tokens.any { token -> TibetanOnsets.any(token::startsWith) } ||
            tokens.any { '\'' in it } ||
            tokens.count { it in WylieLexicon } >= MIN_LEXICON_TOKENS
    }
}
