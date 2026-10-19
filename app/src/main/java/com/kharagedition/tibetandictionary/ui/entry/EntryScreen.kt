package com.kharagedition.tibetandictionary.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * One entry (1h/1l). Stub until L3 (T3.1) replaces the body: [onOpenEntry] pushes a see-also entry,
 * [onOpenSearch] searches for a headword that has no entry id, [onBack] pops the route.
 */
@Composable
fun EntryScreen(entryId: Long, onBack: () -> Unit, onOpenEntry: (Long) -> Unit, onOpenSearch: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Entry", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
