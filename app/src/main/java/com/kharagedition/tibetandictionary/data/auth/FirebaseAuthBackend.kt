package com.kharagedition.tibetandictionary.data.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.kharagedition.tibetandictionary.core.firebase.FirebaseAvailability
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** [AuthBackend] over FirebaseAuth. Without a FirebaseApp (Robolectric), there is no user and nothing works. */
@Singleton
class FirebaseAuthBackend @Inject constructor(
    availability: FirebaseAvailability,
) : AuthBackend {
    private val auth: FirebaseAuth? = if (availability.isAvailable) FirebaseAuth.getInstance() else null
    private val state = MutableStateFlow(auth?.currentUser?.toAuthUser())

    override val user: StateFlow<AuthUser?> = state.asStateFlow()

    init {
        auth?.addAuthStateListener { publish() }
    }

    override suspend fun signInAnonymously(): AuthUser = signIn { it.signInAnonymously() }

    override suspend fun linkGoogle(idToken: String): AuthUser = signIn {
        val current = it.currentUser ?: throw IllegalStateException("no user to link")
        current.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null))
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser =
        signIn { it.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)) }

    override suspend fun refreshIdToken() {
        firebase { it.currentUser?.getIdToken(true)?.await() }
    }

    override suspend fun signOut() {
        firebase { it.signOut() }
        publish()
    }

    private suspend fun signIn(task: (FirebaseAuth) -> Task<AuthResult>): AuthUser {
        val user = firebase { task(it).await().user } ?: throw IllegalStateException("no user after sign-in")
        // A link keeps the same user, so the auth state listener doesn't fire for it.
        publish()
        return user.toAuthUser()
    }

    private inline fun <T> firebase(block: (FirebaseAuth) -> T): T {
        val auth = auth ?: throw IOException("Firebase unavailable")
        return try {
            block(auth)
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AccountCollisionException(e)
        } catch (e: FirebaseNetworkException) {
            throw IOException(e)
        }
    }

    private fun publish() {
        state.value = auth?.currentUser?.toAuthUser()
    }
}

/** A linked user's own name and email can be empty; the Google profile then has them. */
private fun FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    isAnonymous = isAnonymous,
    name = displayName.orIfBlank { providerData.firstNotNullOfOrNull { it.displayName.orIfBlank { null } } },
    email = email.orIfBlank { providerData.firstNotNullOfOrNull { it.email.orIfBlank { null } } },
)

private inline fun String?.orIfBlank(fallback: () -> String?): String? = if (isNullOrBlank()) fallback() else this
