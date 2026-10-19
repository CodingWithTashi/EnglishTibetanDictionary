package com.kharagedition.tibetandictionary.data.local.entity

/**
 * Extra token characters for the FTS `unicode61` tokenizer. Tibetan vowel signs, subjoined letters and other marks
 * are Unicode marks, which unicode61 would treat as separators and so split inside a syllable. Listing them keeps
 * one token = one syllable; tsheg (U+0F0B/U+0F0C) and shad stay separators.
 *
 * The value lands verbatim in the exported schema's FTS `createSql`, which the Python pipeline executes.
 */
object TibetanTokenChars {
    /** `tokenchars=` + 77 code points: U+0F18–19, 0F35, 0F37, 0F39, 0F3E–3F, 0F71–84, 0F86–87, 0F8D–97, 0F99–BC, 0FC6. */
    const val ARG = "tokenchars=" +
        // Astrological signs, nyi zla, tsa phru, yar tshes, mar tshes (U+0F18 U+0F19 U+0F35 U+0F37 U+0F39 U+0F3E U+0F3F)
        "༹༘༙༵༷༾༿" +
        // Vowel signs, anusvara, visarga, halanta (U+0F71–U+0F84)
        "ཱཱཱིིུུྲྀཷླྀཹེཻོཽཾཿཱྀྀ" +
        "྄ྂྃ" +
        // Lci rtags, yang rtags (U+0F86 U+0F87)
        "྆྇" +
        // Subjoined signs and letters (U+0F8D–U+0F97, U+0F99–U+0FBC)
        "ྍྎྏྐྑྒྒྷྔྕྖྗ" +
        "ྙྚྛྜྜྷྞྟྠྡྡྷྣྤྥྦྦྷྨྩ" +
        "ྪྫྫྷྭྮྯྰྱྲླྴྵྶྷྸྐྵྺ" +
        "ྻྼ" +
        // Symbol padma gdan (U+0FC6)
        "࿆"
}
