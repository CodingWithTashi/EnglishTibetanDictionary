package com.kharagedition.tibetandictionary.domain

/**
 * What a search query is written in, as [DetectScript] sees it. Unlike the repository's
 * [com.kharagedition.tibetandictionary.data.model.Script], Latin letters are split into [WYLIE] (a Tibetan
 * transliteration) and [LATIN] (anything else, usually English), so the search can pick a direction.
 */
enum class InputScript { TIBETAN, LATIN, WYLIE, HAN }
