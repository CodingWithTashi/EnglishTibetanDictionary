package com.kharagedition.tibetandictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext

/**
 * Lets the repository's scheduler run and Room's own query threads deliver, so a following `expectNoEvents()` checks
 * something: Room flows emit from a real executor, which `runCurrent()` alone does not wait for.
 */
internal suspend fun TestScope.settle() {
    runCurrent()
    withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }
    runCurrent()
}

private const val SETTLE_MILLIS = 300L
