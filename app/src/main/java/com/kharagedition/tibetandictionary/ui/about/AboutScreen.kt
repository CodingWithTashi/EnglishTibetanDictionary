package com.kharagedition.tibetandictionary.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** About: data source, fonts and licences. Stub until L4 (T4.1) replaces the body: [onBack] pops the route. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("About", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
