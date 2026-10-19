package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stub so the Hilt graph compiles; L1 (T1.6) replaces the bodies. */
@Singleton
class DictionaryRepositoryImpl @Inject constructor() : DictionaryRepository {
    override val capabilities: Flow<DictionaryCapabilities> get() = TODO("L1")

    override suspend fun direction(id: String): Direction? = TODO("L1")

    override suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult = TODO("L1")

    override suspend fun suggestionsForEmptyQuery(directionId: String, date: LocalDate, count: Int): List<EntrySummary> =
        TODO("L1")

    override fun observeEntry(id: Long): Flow<Entry?> = TODO("L1")

    override suspend fun entry(id: Long): Entry? = TODO("L1")

    override suspend fun summaries(ids: List<Long>): List<EntrySummary> = TODO("L1")

    override suspend fun closest(text: String, directionId: String?, limit: Int): List<Suggestion> = TODO("L1")
}
