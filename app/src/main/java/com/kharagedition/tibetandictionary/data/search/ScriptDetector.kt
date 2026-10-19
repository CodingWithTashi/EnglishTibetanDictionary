package com.kharagedition.tibetandictionary.data.search

import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.model.Script

/**
 * The script a query is written in: [Script.TIBETAN] when any character is in the Tibetan block (U+0F00..U+0FFF), so
 * mixed input such as "བཀྲ shis" is Tibetan; otherwise [Script.HAN] when any character is a CJK ideograph (Unified
 * Ideographs U+4E00..U+9FFF or Extension A U+3400..U+4DBF); otherwise [Script.LATIN], including blank input, digits,
 * punctuation and scripts the dictionary has no direction for.
 */
object ScriptDetector {
    private val CJK_UNIFIED = '\u4E00'..'\u9FFF'
    private val CJK_EXTENSION_A = '\u3400'..'\u4DBF'

    fun detect(q: String): Script = when {
        TibetanScript.hasTibetan(q) -> Script.TIBETAN
        q.any { it in CJK_UNIFIED || it in CJK_EXTENSION_A } -> Script.HAN
        else -> Script.LATIN
    }
}
