package com.kharagedition.tibetandictionary.ui.about

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/** About: data source, fonts and licences. Stub until L4 (T4.1) replaces the body: [onBack] pops the route. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    RouteStub("About", "Back" to onBack)
}
