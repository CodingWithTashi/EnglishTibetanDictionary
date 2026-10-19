package com.kharagedition.tibetandictionary.data.local.entity

/**
 * Extra token characters for the FTS `unicode61` tokenizer. Tibetan vowel signs, subjoined letters and other marks
 * are Unicode marks, which unicode61 would treat as separators and so split inside a syllable. Listing them keeps
 * one token = one syllable; tsheg (U+0F0B/U+0F0C) and shad stay separators.
 *
 * The value lands verbatim in the exported schema's FTS `createSql`, which the Python pipeline executes. The marks are
 * written as escapes: raw, a baseless run of combining marks is reordered by Unicode normalisation (NFC), which an
 * editor or tool could apply silently and so change the schema.
 */
object TibetanTokenChars {
    /**
     * `tokenchars=` + 77 code points: U+0F18..19, 0F35, 0F37, 0F39, 0F3E..3F, 0F71..84, 0F86..87, 0F8D..97, 0F99..BC,
     * 0FC6.
     */
    const val ARG = "tokenchars=" +
        // Astrological signs, nyi zla, tsa phru, yar tshes, mar tshes
        "\u0F18\u0F19\u0F35\u0F37\u0F39\u0F3E\u0F3F" +
        // Vowel signs, anusvara, visarga, halanta (U+0F71..U+0F84)
        "\u0F71\u0F72\u0F73\u0F74\u0F75\u0F76\u0F77\u0F78\u0F79\u0F7A" +
        "\u0F7B\u0F7C\u0F7D\u0F7E\u0F7F\u0F80\u0F81\u0F82\u0F83\u0F84" +
        // Lci rtags, yang rtags (U+0F86 U+0F87)
        "\u0F86\u0F87" +
        // Subjoined signs and letters (U+0F8D..U+0F97, U+0F99..U+0FBC)
        "\u0F8D\u0F8E\u0F8F\u0F90\u0F91\u0F92\u0F93\u0F94\u0F95\u0F96\u0F97" +
        "\u0F99\u0F9A\u0F9B\u0F9C\u0F9D\u0F9E\u0F9F\u0FA0\u0FA1\u0FA2" +
        "\u0FA3\u0FA4\u0FA5\u0FA6\u0FA7\u0FA8\u0FA9\u0FAA\u0FAB\u0FAC" +
        "\u0FAD\u0FAE\u0FAF\u0FB0\u0FB1\u0FB2\u0FB3\u0FB4\u0FB5\u0FB6" +
        "\u0FB7\u0FB8\u0FB9\u0FBA\u0FBB\u0FBC" +
        // Symbol padma gdan (U+0FC6)
        "\u0FC6"
}
