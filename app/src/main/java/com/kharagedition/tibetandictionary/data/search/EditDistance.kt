package com.kharagedition.tibetandictionary.data.search

/**
 * Edit distance for did-you-mean ranking.
 *
 * [damerauLevenshtein] is the optimal string alignment variant: insertions, deletions, substitutions and swaps of two
 * adjacent characters each cost 1, but no substring is edited more than once ("ca" → "abc" is 3, where unrestricted
 * Damerau-Levenshtein gives 2). It compares UTF-16 chars exactly: Tibetan is in the BMP, so a char is a code point,
 * and each subjoined letter or vowel sign of a stack counts separately. Case matters; compare normalised text.
 */
object EditDistance {

    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Three rolling rows of the (a.length + 1) x (b.length + 1) table: i - 2, i - 1 and i.
        var beforePrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = minOf(best, beforePrevious[j - 2] + 1)
                }
                current[j] = best
            }
            val recycled = beforePrevious
            beforePrevious = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }
}
