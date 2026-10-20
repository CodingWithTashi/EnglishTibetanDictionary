package com.kharagedition.tibetandictionary.navigation

import android.content.Intent
import android.net.Uri

/**
 * The app's `app://` links: the URI patterns declared on the [Entry] and [Search] destinations, and the parser
 * that turns a link delivered to the running activity into a [DeepLink] for [NavigationEventBus].
 */
object DeepLinks {
    private const val SCHEME = "app"
    private const val HOST_ENTRY = "entry"
    private const val HOST_SEARCH = "search"
    private const val QUERY = "query"

    const val ENTRY_PATTERN = "$SCHEME://$HOST_ENTRY/{entryId}"
    const val SEARCH_PATTERN = "$SCHEME://$HOST_SEARCH?$QUERY={$QUERY}"

    fun parse(intent: Intent?): DeepLink? = parse(intent?.data)

    /** `app://entry/{id}` → [DeepLink.OpenEntry]; `app://search[?query=…]` → [DeepLink.OpenSearch]; else null. */
    fun parse(uri: Uri?): DeepLink? {
        if (uri == null || !uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        return when (uri.host?.lowercase()) {
            HOST_ENTRY -> uri.pathSegments.singleOrNull()?.toLongOrNull()?.let { DeepLink.OpenEntry(it) }
            HOST_SEARCH -> DeepLink.OpenSearch(uri.getQueryParameter(QUERY).orEmpty())
            else -> null
        }
    }
}
