package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.translation.Lang
import java.text.Normalizer

enum class ScriptCheck { OK, EMPTY, NOT_TIBETAN_SCRIPT, WRONG_SCRIPT }

/** One token of a translation, as the result card shows it. */
data class OutputWord(
    val text: String,
    /** What a tap searches; null = not tappable. */
    val query: String?,
)

/**
 * The client's copy of the server's text rules (backend/functions/src/domain/normalize.ts), so the counter shows
 * what the quota counts and romanised input is caught before any call.
 */
object TranslationText {
    private const val SHAD = '\u0F0D'
    private const val PHRASE_MIN_TOKENS = 3
    private const val MIN_QUERY_LETTERS = 4
    private const val ZERO_WIDTH = "\u200B\u200C\u200D\uFEFF"
    private const val JS_EXTRA_SPACE = "\t\n\u000B\u000C\r\u2028\u2029\uFEFF"
    private const val SYLLABLE_BREAKS = "\u0F0B\u0F0D\u0F0E"
    private const val TRAILING_MARKS = "\u0F0B\u0F0C\u0F0D\u0F0E"
    private const val ENGLISH_PUNCTUATION = ".,?!;:\"“”‘’()"

    /**
     * The text that is counted and sent: well-formed, NFC, no zero-width characters, LF line breaks, single spaces,
     * trimmed lines, at most one blank line in a row, trimmed.
     */
    fun normalize(raw: String): String {
        val text = Normalizer.normalize(wellFormed(raw), Normalizer.Form.NFC)
            .filterNot { it in ZERO_WIDTH }
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val kept = mutableListOf<String>()
        for (line in text.split('\n')) {
            val tidy = collapseSpaces(line).trim(::isSpace)
            if (tidy.isEmpty() && kept.lastOrNull() == "") continue
            kept += tidy
        }
        return kept.joinToString("\n").trim(::isSpace)
    }

    /** Unicode code points, as the quota counts them. */
    fun length(text: String): Int = text.codePointCount(0, text.length)

    fun check(normalized: String, source: Lang): ScriptCheck {
        if (normalized.isEmpty()) return ScriptCheck.EMPTY
        var tibetan = false
        var other = false
        var i = 0
        while (i < normalized.length) {
            val codePoint = normalized.codePointAt(i)
            if (isTibetanLetter(codePoint)) tibetan = true else if (Character.isLetter(codePoint)) other = true
            i += Character.charCount(codePoint)
        }
        return when {
            source.usesTibetanScript -> if (tibetan) ScriptCheck.OK else ScriptCheck.NOT_TIBETAN_SCRIPT
            tibetan && !other -> ScriptCheck.WRONG_SCRIPT
            else -> ScriptCheck.OK
        }
    }

    /** bo/dz: ≥ 3 syllables or ends in ། ; en: ≥ 3 words. */
    fun isPhrase(normalized: String, source: Lang): Boolean =
        if (source.usesTibetanScript) {
            normalized.endsWith(SHAD) ||
                tokens(normalized) { isSpace(it) || it in SYLLABLE_BREAKS }.size >= PHRASE_MIN_TOKENS
        } else {
            tokens(normalized, ::isSpace).size >= PHRASE_MIN_TOKENS
        }

    /**
     * The output split on whitespace. A Tibetan token searches without its trailing tsheg or shad, and only if it
     * has a Tibetan letter; an English token searches without surrounding punctuation, and only with 4+ letters.
     */
    fun words(output: String, target: Lang): List<OutputWord> =
        tokens(output, ::isSpace).map { word ->
            val query = if (target.usesTibetanScript) {
                word.trimEnd { it in TRAILING_MARKS }.takeIf { q -> q.any { isTibetanLetter(it.code) } }
            } else {
                word.trim { it in ENGLISH_PUNCTUATION }.takeIf { q -> q.count(Char::isLetter) >= MIN_QUERY_LETTERS }
            }
            OutputWord(word, query)
        }

    /** Consonants, vowel signs and subjoined letters (U+0F40–U+0FBC); not punctuation or digits. */
    private fun isTibetanLetter(codePoint: Int): Boolean = codePoint in 0x0F40..0x0FBC

    /** JavaScript's `\s`, which the server's regexes and `trim()` use; Kotlin's `isWhitespace` differs at the edges. */
    private fun isSpace(c: Char): Boolean =
        c in JS_EXTRA_SPACE || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

    /** String.toWellFormed(): a lone surrogate becomes U+FFFD. */
    private fun wellFormed(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                append(c).append(text[i + 1])
                i += 2
                continue
            }
            append(if (c.isSurrogate()) '\uFFFD' else c)
            i++
        }
    }

    private fun collapseSpaces(line: String): String = buildString(line.length) {
        var inSpace = false
        for (c in line) {
            if (!isSpace(c)) append(c) else if (!inSpace) append(' ')
            inSpace = isSpace(c)
        }
    }

    private inline fun tokens(text: String, isSeparator: (Char) -> Boolean): List<String> = buildList {
        var start = -1
        for (i in text.indices) {
            if (isSeparator(text[i])) {
                if (start >= 0) add(text.substring(start, i))
                start = -1
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) add(text.substring(start))
    }
}
