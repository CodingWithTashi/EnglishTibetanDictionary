package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/**
 * Focused search (1g/3b). Stub until L2 (T2.2) replaces the body: starts with [initialQuery], and with voice
 * input when [voice] is set; [onBack] pops the route, [onOpenEntry] opens a result.
 */
@Composable
fun SearchScreen(initialQuery: String, voice: Boolean, onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    RouteStub(
        listOfNotNull("Search", initialQuery.ifEmpty { null }, "(voice)".takeIf { voice }).joinToString(" "),
        "Back" to onBack,
        "Open entry" to { onOpenEntry(1) },
    )
}
