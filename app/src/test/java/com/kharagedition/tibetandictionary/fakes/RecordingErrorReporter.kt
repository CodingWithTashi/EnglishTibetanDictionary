package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.error.ErrorReporter

/** [ErrorReporter] that keeps every recorded error in [recorded]. */
class RecordingErrorReporter : ErrorReporter {
    val recorded = mutableListOf<Throwable>()

    override fun record(t: Throwable) {
        recorded += t
    }
}
