package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.translation.Lang

/**
 * The words of a phrase to look up for "In the dictionary": Tibetan-script text as runs of up to [MAX_SYLLABLES]
 * syllables, English as whitespace words without a few [STOP_WORDS]. Input is normalised text
 * ([TranslationText.normalize]).
 */
object PhraseSegmenter {
    private const val MAX_SYLLABLES = 4
    private const val TSHEG = "\u0F0B"
    private const val SYLLABLE_MARKS = "\u0F0B\u0F0C"
    private const val CLAUSE_MARKS = "\u0F0D\u0F0E\u0F14"
    private const val ENGLISH_PUNCTUATION = ".,?!;:\"“”‘’()[]"

    /** Function words that are never worth a dictionary row. */
    private val STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "if", "so", "as", "of", "to", "in", "on", "at", "by", "for", "with",
        "from", "into", "is", "are", "am", "was", "were", "be", "been", "being", "do", "does", "did", "has", "have",
        "had", "may", "might", "can", "could", "will", "would", "shall", "should", "must", "all", "not", "no",
        "it", "its", "this", "that", "these", "those",
    )

    /** Every 1–4-syllable run (bo/dz) or every non-stop-word (en), deduplicated, in order. */
    fun candidates(normalized: String, source: Lang): List<String> =
        if (source.usesTibetanScript) {
            clauses(normalized).flatMap { syllables ->
                syllables.indices.flatMap { start ->
                    (1..minOf(MAX_SYLLABLES, syllables.size - start)).map { length -> run(syllables, start, length) }
                }
            }.distinct()
        } else {
            englishWords(normalized).distinct()
        }

    /** Greedy longest match left to right over [known]; at most [max] results, in order, deduplicated. */
    fun segment(normalized: String, source: Lang, known: Set<String>, max: Int = 6): List<String> {
        val found = LinkedHashSet<String>()
        if (source.usesTibetanScript) {
            for (syllables in clauses(normalized)) {
                var start = 0
                while (start < syllables.size && found.size < max) {
                    val length = (minOf(MAX_SYLLABLES, syllables.size - start) downTo 1)
                        .firstOrNull { run(syllables, start, it) in known }
                    if (length != null) found += run(syllables, start, length)
                    start += length ?: 1
                }
            }
        } else {
            englishWords(normalized).filter { it in known }.forEach { if (found.size < max) found += it }
        }
        return found.toList()
    }

    /** The syllables of each clause (split on shad and spaces), without their tsheg. */
    private fun clauses(text: String): List<List<String>> =
        text.splitOn { it.isWhitespace() || it in CLAUSE_MARKS }
            .map { clause -> clause.splitOn { it in SYLLABLE_MARKS }.filter(String::isNotEmpty) }
            .filter(List<String>::isNotEmpty)

    private fun run(syllables: List<String>, start: Int, length: Int): String =
        syllables.subList(start, start + length).joinToString(TSHEG)

    private fun englishWords(text: String): List<String> =
        text.splitOn { it.isWhitespace() }
            .map { it.trim { c -> c in ENGLISH_PUNCTUATION }.lowercase() }
            .filter { it.isNotEmpty() && it !in STOP_WORDS }

    /** The parts between separator characters, empty ones included. */
    private inline fun String.splitOn(isSeparator: (Char) -> Boolean): List<String> {
        val text = this
        return buildList {
            var start = 0
            for (i in text.indices) {
                if (isSeparator(text[i])) {
                    add(text.substring(start, i))
                    start = i + 1
                }
            }
            add(text.substring(start))
        }
    }
}
