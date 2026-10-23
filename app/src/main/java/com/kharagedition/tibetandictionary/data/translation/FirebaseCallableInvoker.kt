package com.kharagedition.tibetandictionary.data.translation

import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.kharagedition.tibetandictionary.core.firebase.FirebaseAvailability
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** [CallableInvoker] over the Firebase Functions SDK, in the backend's region. */
@Singleton
class FirebaseCallableInvoker @Inject constructor(
    private val availability: FirebaseAvailability,
) : CallableInvoker {
    private val functions by lazy { Firebase.functions(REGION) }

    override suspend fun call(name: String, data: Map<String, Any?>?): Any? {
        // Where Firebase isn't set up (Robolectric), behave as offline: the UI already handles that.
        if (!availability.isAvailable) throw IOException("Firebase unavailable")
        return try {
            functions.getHttpsCallable(name).call(data).await().data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw callableFailure(e)
        }
    }

    private companion object {
        const val REGION = "asia-south1"
    }
}

/**
 * What a failed call becomes: a refusal keeps its code and details; a call that never reached the server is an
 * [IOException]. The SDK reports no network as INTERNAL and a timeout as DEADLINE_EXCEEDED, each with the I/O
 * error as the cause, and a token fetch with no network as [FirebaseNetworkException]. A token refresh for a user
 * deleted or disabled elsewhere fails with [FirebaseAuthInvalidUserException], alone or as the cause.
 */
internal fun callableFailure(e: Exception): Exception = when {
    e is FirebaseAuthInvalidUserException || e.cause is FirebaseAuthInvalidUserException -> AccountGoneException(e)
    e is FirebaseFunctionsException && (e.cause is IOException || e.cause is FirebaseNetworkException) -> IOException(e)
    e is FirebaseFunctionsException ->
        CallableException(e.code.name, (e.details as? Map<*, *>)?.mapKeys { it.key.toString() }, e)
    e is FirebaseNetworkException -> IOException(e)
    else -> e
}
