package com.kharagedition.tibetandictionary.core.error

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/** Sink for non-fatal errors that were handled (swallowed) so the app keeps running. */
interface ErrorReporter {
    /** Records [t]; must never throw. */
    fun record(t: Throwable)
}

/** [ErrorReporter] that forwards to Firebase Crashlytics. */
@Singleton
class CrashlyticsErrorReporter @Inject constructor() : ErrorReporter {
    override fun record(t: Throwable) {
        runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
    }
}
