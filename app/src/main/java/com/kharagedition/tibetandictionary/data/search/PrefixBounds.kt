package com.kharagedition.tibetandictionary.data.search

/**
 * Bounds for prefix search as a B-tree range scan: `col >= :prefix AND col < :upper` uses the column's index (a
 * `LIKE 'prefix%'` would not under NOCASE with non-ASCII text), see `EntryDao.prefixByHeadword`/`prefixByWylie`.
 *
 * SQLite orders text by its UTF-8 bytes, i.e. by code point (NOCASE additionally folds ASCII letters). U+10FFFF is the
 * last code point, so `prefix + U+10FFFF` sorts above every string that starts with `prefix` and below every string
 * that does not and sorts after it ("lead" → "leaf" is excluded). The only string with the prefix it misses is one
 * continuing with U+10FFFF itself, a noncharacter that never occurs in dictionary text.
 *
 * The bound is meant for SQLite: Kotlin's [String.compareTo] compares UTF-16 units, which puts U+E000..U+FFFF above a
 * surrogate pair, so do not use it to filter in memory.
 */
object PrefixBounds {
    /** U+10FFFF as a UTF-16 surrogate pair. */
    private const val MAX_CODE_POINT = "\uDBFF\uDFFF"

    /** Exclusive upper bound of the range of strings starting with [prefix]. */
    fun upper(prefix: String): String = prefix + MAX_CODE_POINT
}
