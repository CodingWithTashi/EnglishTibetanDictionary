package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import kotlinx.coroutines.flow.Flow

/** Saved › Translations (user.db), per pack, newest first. */
interface SavedTranslationsRepository {
    fun observe(): Flow<List<TranslationRecord>>

    fun observeIsSaved(pair: LangPair, source: String): Flow<Boolean>

    /** Saves (or updates) the translation of [source] in [pair]; returns its id. */
    suspend fun save(pair: LangPair, source: String, text: TranslatedText): Long

    suspend fun remove(id: Long): TranslationRecord?

    /** Undo of remove: re-inserts with the same id and time. */
    suspend fun restore(record: TranslationRecord)
}
