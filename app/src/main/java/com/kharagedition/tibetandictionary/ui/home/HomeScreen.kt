package com.kharagedition.tibetandictionary.ui.home

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/**
 * Home page of the Search tab (1a/2a). Stub until L2 (T2.3) replaces the body: [onOpenSearch] opens the Search
 * route with an optional query, or voice input when `voice` is set; [onOpenEntry] opens an entry.
 */
@Composable
fun HomeScreen(onOpenSearch: (query: String?, voice: Boolean) -> Unit, onOpenEntry: (Long) -> Unit) {
    RouteStub(
        "Home",
        "Open search" to { onOpenSearch(null, false) },
        "Voice search" to { onOpenSearch(null, true) },
        "Open entry" to { onOpenEntry(1) },
    )
}
