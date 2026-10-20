package com.kharagedition.tibetandictionary.core.text

/**
 * Joins short display values: an entry's parts of speech and label ("adj. · n."), a row's Wylie and gloss, the
 * senses of a shared entry.
 */
const val DOT_SEPARATOR = " · "

/** True for text written only in Tibetan script (spaces and punctuation aside), such as a Tibetan headword. */
fun isTibetanText(text: String): Boolean =
    TibetanScript.hasTibetan(text) && text.none { it.isLetterOrDigit() && !TibetanScript.isTibetan(it) }
