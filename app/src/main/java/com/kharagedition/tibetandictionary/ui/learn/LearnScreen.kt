package com.kharagedition.tibetandictionary.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * The Learn tab (flashcards). Placeholder: the tab is only shown when
 * [com.kharagedition.tibetandictionary.Features.LEARN] is on.
 */
@Composable
fun LearnScreen() {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Learn", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
