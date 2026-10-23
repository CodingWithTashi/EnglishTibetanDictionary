package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.data.translation.ApiResult
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.StatusReply
import com.kharagedition.tibetandictionary.data.translation.TranslateReply
import com.kharagedition.tibetandictionary.data.translation.TranslationApi
import kotlinx.coroutines.CompletableDeferred

/**
 * [TranslationApi] that answers from scripted queues, one per method, and records every call. A call with
 * nothing queued fails the test. Set [translateGate] or [statusGate] to hold that call until the test completes it.
 */
class FakeTranslationApi : TranslationApi {
    data class TranslateCall(val text: String, val pair: LangPair, val timeZone: String)

    val translateResults = ArrayDeque<ApiResult<TranslateReply>>()
    val statusResults = ArrayDeque<ApiResult<StatusReply>>()
    val deleteResults = ArrayDeque<ApiResult<Unit>>()

    val translateCalls = mutableListOf<TranslateCall>()
    val statusCalls = mutableListOf<String>()
    var deleteCalls = 0

    var translateGate: CompletableDeferred<Unit>? = null
    var statusGate: CompletableDeferred<Unit>? = null
    var deleteGate: CompletableDeferred<Unit>? = null

    override suspend fun translate(text: String, pair: LangPair, timeZone: String): ApiResult<TranslateReply> {
        translateCalls += TranslateCall(text, pair, timeZone)
        translateGate?.await()
        return translateResults.removeFirstOrNull() ?: error("no translate result queued")
    }

    override suspend fun status(timeZone: String): ApiResult<StatusReply> {
        statusCalls += timeZone
        statusGate?.await()
        return statusResults.removeFirstOrNull() ?: error("no status result queued")
    }

    override suspend fun deleteAccount(): ApiResult<Unit> {
        deleteCalls++
        deleteGate?.await()
        return deleteResults.removeFirstOrNull() ?: error("no deleteAccount result queued")
    }
}
