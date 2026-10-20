package com.kharagedition.tibetandictionary.data.search

import com.kharagedition.tibetandictionary.core.text.TibetanScript

/**
 * Turns raw search-field text into the query every search step uses. Rules, in order:
 * 1. drop zero-width characters (U+200B, U+FEFF) and map the non-breaking tsheg U+0F0C to the tsheg U+0F0B, as the
 *    build pipeline does for dictionary text; fold the apostrophe look-alikes that smart-punctuation keyboards type
 *    (U+2019 `’`, U+2018 `‘`, U+02BC `ʼ`) to the ASCII `'` Wylie writes its a-chung with ("’jam dpal" → "'jam dpal");
 * 2. trim and collapse every whitespace run (including NBSP) to one space;
 * 3. strip trailing shads (U+0F0D `།`, U+0F0E `༎`) and the spaces around them; a tsheg before the shad stays;
 * 4. lowercase (locale-independent) when the text contains no Tibetan; text with any Tibetan keeps its case.
 *
 * Never throws; blank input gives "".
 */
object QueryNormalizer {
    private const val ZERO_WIDTH_SPACE = '\u200B'
    private const val BYTE_ORDER_MARK = '\uFEFF'
    private const val SHAD = '\u0F0D'
    private const val NYIS_SHAD = '\u0F0E'
    private const val APOSTROPHE = '\''

    /** Right and left single quotation marks and the modifier letter apostrophe, typed for `'`. */
    private const val TYPOGRAPHIC_APOSTROPHES = "\u2019\u2018\u02BC"

    fun normalize(raw: String): String {
        val cleaned = buildString(raw.length) {
            var pendingSpace = false
            for (c in raw) {
                when {
                    c == ZERO_WIDTH_SPACE || c == BYTE_ORDER_MARK -> Unit
                    c.isWhitespace() -> pendingSpace = isNotEmpty()
                    else -> {
                        if (pendingSpace) append(' ')
                        pendingSpace = false
                        append(
                            when (c) {
                                TibetanScript.NON_BREAKING_TSHEG -> TibetanScript.TSHEG
                                in TYPOGRAPHIC_APOSTROPHES -> APOSTROPHE
                                else -> c
                            },
                        )
                    }
                }
            }
        }
        val withoutShad = cleaned.trimEnd { it == SHAD || it == NYIS_SHAD || it == ' ' }
        return if (TibetanScript.hasTibetan(withoutShad)) withoutShad else withoutShad.lowercase()
    }
}
