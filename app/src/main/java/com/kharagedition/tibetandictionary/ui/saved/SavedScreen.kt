package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/** The Saved tab (1i/3c). Stub until L3 (T3.2) replaces the body: [onOpenEntry] opens a saved entry. */
@Composable
fun SavedScreen(onOpenEntry: (Long) -> Unit) {
    RouteStub("Saved", "Open entry" to { onOpenEntry(1) })
}
