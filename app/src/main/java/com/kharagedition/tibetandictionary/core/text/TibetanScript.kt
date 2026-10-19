package com.kharagedition.tibetandictionary.core.text

/** Character-level helpers for text in the Tibetan Unicode block (U+0F00..U+0FFF). */
object TibetanScript {
    /** The syllable separator (U+0F0B). */
    const val TSHEG = '་'

    /** The non-breaking tsheg (U+0F0C); search normalises it to [TSHEG]. */
    const val NON_BREAKING_TSHEG = '༌'

    /** True for any character in the Tibetan block: letters, vowel signs, digits, tsheg, shad and marks. */
    fun isTibetan(c: Char): Boolean = c in 'ༀ'..'࿿'

    /** True when [text] contains at least one Tibetan-block character. */
    fun hasTibetan(text: CharSequence): Boolean = text.any(::isTibetan)

    /**
     * [text] ending in a tsheg, as dictionary headwords do: one is appended only when the last character is a
     * Tibetan letter or vowel sign (U+0F40..U+0FBC). Text already ending in a tsheg, a shad, a digit, whitespace
     * or a Latin character is returned unchanged.
     */
    fun ensureTsheg(text: String): String {
        val last = text.lastOrNull() ?: return text
        return if (last in 'ཀ'..'ྼ') text + TSHEG else text
    }

    /** [text] without its trailing tshegs (breaking or non-breaking); inner tshegs are kept. */
    fun stripTrailingTsheg(text: String): String = text.trimEnd { it == TSHEG || it == NON_BREAKING_TSHEG }

    /**
     * Maximal index ranges of Tibetan text in [text], for rendering mixed runs in different fonts. Whitespace
     * between two Tibetan characters joins the run ("བཀྲ་ཤིས་ བདེ་ལེགས" is one run); leading and trailing
     * whitespace never does.
     */
    fun runs(text: CharSequence): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        var lastTibetan = -1
        for (i in text.indices) {
            val c = text[i]
            when {
                isTibetan(c) -> {
                    if (start < 0) start = i
                    lastTibetan = i
                }
                c.isWhitespace() && start >= 0 -> Unit
                start >= 0 -> {
                    runs += start..lastTibetan
                    start = -1
                }
            }
        }
        if (start >= 0) runs += start..lastTibetan
        return runs
    }
}
