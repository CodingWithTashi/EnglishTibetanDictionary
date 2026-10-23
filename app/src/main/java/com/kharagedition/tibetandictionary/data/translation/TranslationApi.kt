package com.kharagedition.tibetandictionary.data.translation

/** The translation backend's callables (backend/README.md). Never throws, except for cancellation. */
interface TranslationApi {
    suspend fun translate(text: String, pair: LangPair, timeZone: String): ApiResult<TranslateReply>
    suspend fun status(timeZone: String): ApiResult<StatusReply>
    suspend fun deleteAccount(): ApiResult<Unit>

    /** Re-reads the caller's Pro subscription from RevenueCat on the server; the plan it now gives. */
    suspend fun syncSubscription(): ApiResult<Plan>
}
