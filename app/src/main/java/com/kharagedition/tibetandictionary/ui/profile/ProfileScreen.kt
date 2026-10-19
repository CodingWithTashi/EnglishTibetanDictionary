package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.runtime.Composable
import com.kharagedition.tibetandictionary.ui.app.RouteStub

/** The Profile tab (2f). Stub until L4 (T4.1) replaces the body: [onOpenAbout] opens About. */
@Composable
fun ProfileScreen(onOpenAbout: () -> Unit) {
    RouteStub("Profile", "Open about" to onOpenAbout)
}
