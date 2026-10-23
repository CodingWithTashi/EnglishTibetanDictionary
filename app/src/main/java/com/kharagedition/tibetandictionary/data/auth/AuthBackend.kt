package com.kharagedition.tibetandictionary.data.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val isAnonymous: Boolean, val name: String?, val email: String?)

/** The Google account already belongs to another Firebase user, so it can't be linked to this one. */
class AccountCollisionException(cause: Throwable? = null) : Exception(cause)

/** The thin seam over FirebaseAuth. Throws IOException when offline or when Firebase isn't set up. */
interface AuthBackend {
    val user: StateFlow<AuthUser?>

    suspend fun signInAnonymously(): AuthUser

    /** Links Google to the current anonymous user; throws AccountCollisionException if the account has a uid. */
    suspend fun linkGoogle(idToken: String): AuthUser

    suspend fun signInWithGoogle(idToken: String): AuthUser

    /** A fresh ID token, so the next call carries the Google identity. */
    suspend fun refreshIdToken()

    suspend fun signOut()
}
