package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** The Profile tab (2f). Stub until L4 (T4.1) replaces the body: [onOpenAbout] opens About. */
@Composable
fun ProfileScreen(onOpenAbout: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Profile", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
