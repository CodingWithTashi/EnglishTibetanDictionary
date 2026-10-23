package com.kharagedition.tibetandictionary.data.auth

import android.app.Activity
import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Guest first, Google later. Signing in with Google links it to the guest, keeping the uid and today's usage,
 * unless that Google account already has a uid (another install), which then replaces the guest's.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val backend: AuthBackend,
    private val credentials: GoogleCredentialSource,
    private val errors: ErrorReporter,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    override val account: StateFlow<Account?> = backend.user
        .map { it?.toAccount() }
        .stateIn(scope, SharingStarted.Eagerly, backend.user.value?.toAccount())

    /** Concurrent first calls share one anonymous sign-in. */
    private val mutex = Mutex()

    override suspend fun ensureUser(): Account = mutex.withLock {
        (backend.user.value ?: backend.signInAnonymously()).toAccount()
    }

    override suspend fun signInWithGoogle(activity: Activity): SignInResult {
        return try {
            val idToken = credentials.requestIdToken(activity) ?: return SignInResult.Cancelled
            val linked = if (backend.user.value?.isAnonymous == true) {
                try {
                    backend.linkGoogle(idToken)
                    true
                } catch (_: AccountCollisionException) {
                    backend.signInWithGoogle(idToken)
                    false
                }
            } else {
                backend.signInWithGoogle(idToken)
                false
            }
            backend.refreshIdToken()
            SignInResult.Success(linked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline is expected; anything else is worth a report.
            if (e !is IOException) errors.record(e)
            SignInResult.Failed(e)
        }
    }

    override suspend fun signOut() {
        backend.signOut()
        try {
            credentials.clearCredentialState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.record(e)
        }
    }
}

private fun AuthUser.toAccount() = Account(uid = uid, isGuest = isAnonymous, name = name, email = email)
