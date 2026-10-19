package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** First-run setup (2b/2c). Stub until L4 (T4.2) replaces the body: [onFinished] moves on to the main scaffold. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DictTheme.colors.background), contentAlignment = Alignment.Center) {
        Text("Onboarding", style = DictTheme.type.screenTitle, color = DictTheme.colors.ink)
    }
}
