package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.data.repository.TranslationHistoryRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationRecord
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [TranslationHistoryRepository] in memory, newest first, upserting by (pair, source) and keeping 20. */
class FakeTranslationHistoryRepository(
    initial: List<TranslationRecord> = emptyList(),
    private val now: () -> Instant = { Instant.parse("2026-10-19T09:00:00Z") },
) : TranslationHistoryRepository {
    val records = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override fun observe(limit: Int): Flow<List<TranslationRecord>> = records.map { it.take(limit) }

    override suspend fun record(pair: LangPair, source: String, text: TranslatedText) {
        val existing = records.value.firstOrNull { it.pair == pair && it.source == source }
        val row = TranslationRecord(existing?.id ?: nextId++, pair, source, text.translation, text.provider, now())
        records.value = (listOf(row) + records.value.filterNot { it.id == row.id }).take(MAX)
    }

    override suspend fun clear() {
        records.value = emptyList()
    }

    private companion object {
        const val MAX = 20
    }
}
