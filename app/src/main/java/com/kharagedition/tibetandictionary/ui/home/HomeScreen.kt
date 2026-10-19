package com.kharagedition.tibetandictionary.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * Home page of the Search tab (1a/2a). Stub until L2 (T2.3) replaces the body: [onOpenSearch] opens the Search
 * route with an optional query, or voice input when `voice` is set; [onOpenEntry] opens an entry.
 */
@Composable
fun HomeScreen(onOpenSearch: (query: String?, voice: Boolean) -> Unit, onOpenEntry: (Long) -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Home", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
