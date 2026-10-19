package com.kharagedition.tibetandictionary.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** The Saved tab (1i/3c). Stub until L3 (T3.2) replaces the body: [onOpenEntry] opens a saved entry. */
@Composable
fun SavedScreen(onOpenEntry: (Long) -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Saved", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
