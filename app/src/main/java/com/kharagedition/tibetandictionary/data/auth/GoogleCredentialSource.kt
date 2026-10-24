package com.kharagedition.tibetandictionary.data.auth

import android.app.Activity

interface GoogleCredentialSource {
    /** The Google ID token from the "Sign in with Google" sheet; null when the user cancelled. */
    suspend fun requestIdToken(activity: Activity): String?

    /** Forgets the chosen account, so the next sign-in asks again. */
    suspend fun clearCredentialState()
}
