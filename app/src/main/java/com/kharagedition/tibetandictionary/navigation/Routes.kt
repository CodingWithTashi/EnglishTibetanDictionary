package com.kharagedition.tibetandictionary.navigation

import kotlinx.serialization.Serializable

/** First-run setup; replaced by [Main] once finished. */
@Serializable data object Onboarding

/** The bottom-navigation scaffold hosting the [MainTab]s. */
@Serializable data object Main

/** Focused search, optionally pre-filled with [query] or starting voice input when [voice] is set. */
@Serializable data class Search(val query: String = "", val voice: Boolean = false)

/** One dictionary entry; see-also links push another [Entry] on top. */
@Serializable data class Entry(val entryId: Long)

@Serializable data object About
