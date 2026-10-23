package com.kharagedition.tibetandictionary.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** The Firebase user, for translation quota: a silent guest first, Google once they sign in. */
data class Account(val uid: String, val isGuest: Boolean, val name: String?, val email: String?)

sealed interface SignInResult {
    /** [linked]: the guest's uid was kept, so today's usage carries over. */
    data class Success(val linked: Boolean) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val cause: Throwable) : SignInResult
}

interface AuthRepository {
    /** Null until a Firebase user exists (no anonymous user is created just by reading this). */
    val account: StateFlow<Account?>

    /** The current account, signing in anonymously first if there is none. Throws IOException when offline. */
    suspend fun ensureUser(): Account

    suspend fun signInWithGoogle(activity: Activity): SignInResult

    suspend fun signOut()
}
