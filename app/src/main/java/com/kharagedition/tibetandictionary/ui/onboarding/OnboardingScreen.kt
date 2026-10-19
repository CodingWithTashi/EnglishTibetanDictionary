package com.kharagedition.tibetandictionary.ui.onboarding

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/** First-run setup (2b/2c). Stub until L4 (T4.2) replaces the body: [onFinished] moves on to the main scaffold. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    RouteStub("Onboarding", "Finish" to onFinished)
}
