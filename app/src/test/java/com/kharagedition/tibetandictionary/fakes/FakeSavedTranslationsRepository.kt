package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.data.repository.SavedTranslationsRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationRecord
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [SavedTranslationsRepository] in memory, newest first, one row per (pair, source). */
class FakeSavedTranslationsRepository(
    initial: List<TranslationRecord> = emptyList(),
    private val now: () -> Instant = { Instant.parse("2026-10-19T09:00:00Z") },
) : SavedTranslationsRepository {
    val records = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override fun observe(): Flow<List<TranslationRecord>> = records.map { list -> list.sortedByDescending { it.at } }

    override fun observeIsSaved(pair: LangPair, source: String): Flow<Boolean> =
        records.map { list -> list.any { it.pair == pair && it.source == source } }.distinctUntilChanged()

    override suspend fun save(pair: LangPair, source: String, text: TranslatedText): Long {
        val existing = records.value.firstOrNull { it.pair == pair && it.source == source }
        val row = TranslationRecord(existing?.id ?: nextId++, pair, source, text.translation, text.provider, now())
        records.value = listOf(row) + records.value.filterNot { it.id == row.id }
        return row.id
    }

    override suspend fun remove(id: Long): TranslationRecord? =
        records.value.firstOrNull { it.id == id }?.also { removed -> records.value = records.value - removed }

    override suspend fun restore(record: TranslationRecord) {
        records.value = records.value.filterNot { it.id == record.id } + record
    }
}
