package com.kharagedition.tibetandictionary.ui.entry

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/**
 * One entry (1h/1l). Stub until L3 (T3.1) replaces the body: [onOpenEntry] pushes a see-also entry,
 * [onOpenSearch] searches for a headword that has no entry id, [onBack] pops the route.
 */
@Composable
fun EntryScreen(entryId: Long, onBack: () -> Unit, onOpenEntry: (Long) -> Unit, onOpenSearch: (String) -> Unit) {
    RouteStub(
        "Entry $entryId",
        "Back" to onBack,
        "See also" to { onOpenEntry(entryId + 1) },
        "Search headword" to { onOpenSearch("bkra") },
    )
}
