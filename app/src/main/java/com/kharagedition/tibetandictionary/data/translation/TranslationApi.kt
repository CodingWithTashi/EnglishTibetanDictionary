package com.kharagedition.tibetandictionary.data.translation

/** The translation backend's callables (backend/README.md). Never throws, except for cancellation. */
interface TranslationApi {
    suspend fun translate(text: String, pair: LangPair, timeZone: String): ApiResult<TranslateReply>
    suspend fun status(timeZone: String): ApiResult<StatusReply>
    suspend fun deleteAccount(): ApiResult<Unit>
}
