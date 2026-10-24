package com.kharagedition.tibetandictionary.data.translation

/**
 * Calls a Firebase callable by name and returns its decoded reply. Throws [CallableException] for a refusal,
 * [java.io.IOException] when the call never reached the server and [AccountGoneException] when the user's token
 * can't be refreshed because the user was deleted or disabled.
 */
fun interface CallableInvoker {
    suspend fun call(name: String, data: Map<String, Any?>?): Any?
}

/** A callable failure: [code] is the FirebaseFunctionsException.Code name (e.g. "RESOURCE_EXHAUSTED"). */
class CallableException(
    val code: String,
    val details: Map<String, Any?>?,
    cause: Throwable? = null,
) : Exception(code, cause)

/** The signed-in user was deleted or disabled elsewhere (another device, the console), so the call never left. */
class AccountGoneException(cause: Throwable) : Exception(cause)
