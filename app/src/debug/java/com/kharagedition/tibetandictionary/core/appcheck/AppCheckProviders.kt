package com.kharagedition.tibetandictionary.core.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds attest with a debug token. It is printed to Logcat on first use (tag
 * `com.google.firebase.appcheck.debug`); register it in Firebase console → App Check → Apps → Manage debug tokens.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
