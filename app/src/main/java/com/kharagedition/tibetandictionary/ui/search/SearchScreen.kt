package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * Focused search (1g/3b). Stub until L2 (T2.2) replaces the body: starts with [initialQuery], and with voice
 * input when [voice] is set; [onBack] pops the route, [onOpenEntry] opens a result.
 */
@Composable
fun SearchScreen(initialQuery: String, voice: Boolean, onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Search", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
