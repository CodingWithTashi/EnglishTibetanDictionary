package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.data.auth.AuthBackend
import com.kharagedition.tibetandictionary.data.auth.AuthUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [AuthBackend] in memory. Anonymous sign-in suspends briefly, so concurrent callers overlap. Set a `*Failure` to
 * make that operation throw.
 */
class FakeAuthBackend(initial: AuthUser? = null) : AuthBackend {
    override val user = MutableStateFlow(initial)

    /** Who Google sign-in returns when it switches accounts. */
    var googleUser = AuthUser("u2", isAnonymous = false, name = "Tashi", email = "t@x.com")

    var anonymousFailure: Exception? = null
    var linkFailure: Exception? = null

    var anonymousSignIns = 0
    val links = mutableListOf<String>()
    val googleSignIns = mutableListOf<String>()
    var refreshes = 0
    var signOuts = 0

    val calls: Int get() = anonymousSignIns + links.size + googleSignIns.size + refreshes + signOuts

    override suspend fun signInAnonymously(): AuthUser {
        anonymousSignIns++
        delay(10)
        anonymousFailure?.let { throw it }
        return AuthUser("guest-$anonymousSignIns", isAnonymous = true, name = null, email = null).also { user.value = it }
    }

    override suspend fun linkGoogle(idToken: String): AuthUser {
        links += idToken
        linkFailure?.let { throw it }
        val current = checkNotNull(user.value) { "no user to link" }
        return current.copy(isAnonymous = false, name = googleUser.name, email = googleUser.email)
            .also { user.value = it }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser {
        googleSignIns += idToken
        return googleUser.also { user.value = it }
    }

    override suspend fun refreshIdToken() {
        refreshes++
    }

    override suspend fun signOut() {
        signOuts++
        user.value = null
    }
}
