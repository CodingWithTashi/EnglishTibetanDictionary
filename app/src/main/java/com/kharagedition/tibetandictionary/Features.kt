package com.kharagedition.tibetandictionary

/**
 * Whole features that are not part of release 1. Data-dependent UI never checks these; it follows
 * `DictionaryCapabilities` and nullable fields instead.
 */
object Features {
    /** Learn tab, Home stats card and the flashcards copy. */
    const val LEARN = false

    /** The Profile account card, its sign-in and account sheet, and the plan card (Google sign-in for translation). */
    const val ACCOUNTS = true

    /** The onboarding sign-in step (2d): sign-in is asked for after the first translations, never during setup. */
    const val ONBOARDING_SIGN_IN = false

    /** Share an entry as an image card (otherwise plain-text share). */
    const val SHARE_IMAGE = false

    /** Home-screen widgets (manifest components are not declared in release 1). */
    const val WIDGETS = false

    /** "Look up" from other apps' text selection (manifest component not declared in release 1). */
    const val PROCESS_TEXT = false

    /** Two-pane layout on tablets. */
    const val TABLET_TWO_PANE = false

    /** Word-of-the-day notification row in Profile and its scheduler. */
    const val WOTD_NOTIFICATION = false

    /** Mic in the search field; also requires a speech recognizer on the device. */
    const val VOICE_SEARCH = true
}
