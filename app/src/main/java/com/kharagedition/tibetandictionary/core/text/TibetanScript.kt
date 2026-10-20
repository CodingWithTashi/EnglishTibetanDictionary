package com.kharagedition.tibetandictionary.core.text

/** Character-level helpers for text in the Tibetan Unicode block (U+0F00..U+0FFF). */
object TibetanScript {
    /** The syllable separator (U+0F0B). */
    const val TSHEG = '\u0F0B'

    /** The non-breaking tsheg (U+0F0C); search normalises it to [TSHEG]. */
    const val NON_BREAKING_TSHEG = '\u0F0C'

    private const val VISARGA = '\u0F7F'
    private const val PALUTA = '\u0F85'

    /** True for any character in the Tibetan block: letters, vowel signs, digits, tsheg, shad and marks. */
    fun isTibetan(c: Char): Boolean = c in '\u0F00'..'\u0FFF'

    /** True when [text] contains at least one Tibetan-block character. */
    fun hasTibetan(text: CharSequence): Boolean = text.any(::isTibetan)

    /**
     * [text] ending in a tsheg, as dictionary headwords do: one is appended only when the last character is a
     * Tibetan letter, vowel sign or subjoined letter (U+0F40..U+0FBC), except visarga (U+0F7F, "ཀཿ") and paluta
     * (U+0F85), which end a syllable themselves. Text already ending in a tsheg, a shad, a digit, whitespace or a
     * Latin character is returned unchanged.
     */
    fun ensureTsheg(text: String): String {
        val last = text.lastOrNull() ?: return text
        val needsTsheg = last in '\u0F40'..'\u0FBC' && last != VISARGA && last != PALUTA
        return if (needsTsheg) text + TSHEG else text
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
