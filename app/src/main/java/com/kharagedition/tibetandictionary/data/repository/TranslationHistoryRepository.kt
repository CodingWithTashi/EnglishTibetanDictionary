package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** A translation kept on the device: a recent one, or a saved one. */
data class TranslationRecord(
    val id: Long,
    val pair: LangPair,
    val source: String,
    val translation: String,
    val provider: Provider,
    val at: Instant,
)

/** The Translate tab's Recent list (user.db), per pack. Restoring one makes no call. */
interface TranslationHistoryRepository {
    /** Active pack's recents, newest first (at most 20 are kept per pack). */
    fun observe(limit: Int = 20): Flow<List<TranslationRecord>>

    /**
     * Upserts by (pack, pair, source); moves it to the top. The pack is the pair's, not the active one: a reply
     * that arrives after a pack switch belongs to the pack it was asked in.
     */
    suspend fun record(pair: LangPair, source: String, text: TranslatedText)

    suspend fun clear()
}
