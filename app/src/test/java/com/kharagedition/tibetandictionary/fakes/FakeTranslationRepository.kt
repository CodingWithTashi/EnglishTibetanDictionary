package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslateResult
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [TranslationRepository] with settable [quota] and [availability]. [translate] answers from [results] (a call
 * with nothing queued fails the test) and can be held by [translateGate]; every call is recorded. Set
 * [quotaAfterTranslate], [quotaAfterRefresh] or [quotaAfterSync] to have that call update [quota], as the real one
 * does. [syncSubscription] answers from [syncResults] and can be held by [syncGate].
 */
class FakeTranslationRepository : TranslationRepository {
    data class Call(val text: String, val pair: LangPair, val surface: TranslateSurface)

    override val quota = MutableStateFlow<QuotaState?>(null)
    override val availability = MutableStateFlow<TranslationAvailability?>(null)

    val results = ArrayDeque<TranslateResult>()
    var translateGate: CompletableDeferred<Unit>? = null
    var quotaAfterTranslate: QuotaState? = null
    var quotaAfterRefresh: QuotaState? = null
    val syncResults = ArrayDeque<Plan?>()
    var syncGate: CompletableDeferred<Unit>? = null
    var quotaAfterSync: QuotaState? = null

    val calls = mutableListOf<Call>()
    var prepareCalls = 0
    val refreshCalls = mutableListOf<Boolean>()
    var syncCalls = 0

    override suspend fun prepare() {
        prepareCalls++
    }

    override suspend fun refresh(force: Boolean) {
        refreshCalls += force
        quotaAfterRefresh?.let { quota.value = it }
    }

    override suspend fun translate(text: String, pair: LangPair, surface: TranslateSurface): TranslateResult {
        calls += Call(text, pair, surface)
        translateGate?.await()
        quotaAfterTranslate?.let { quota.value = it }
        return results.removeFirstOrNull() ?: error("no translate result queued")
    }

    override suspend fun syncSubscription(): Plan? {
        syncCalls++
        syncGate?.await()
        check(syncResults.isNotEmpty()) { "no syncSubscription result queued" }
        return syncResults.removeFirst().also { if (it != null) quotaAfterSync?.let { q -> quota.value = q } }
    }
}
