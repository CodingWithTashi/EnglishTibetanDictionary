package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [RecentSearchesRepository] with user.db semantics: one row per (query, direction), observed newest
 * first by [RecentSearch.searchedAt], keeping the 50 newest. Every [record] call is kept as passed (no query
 * filtering), so tests can assert exactly what a ViewModel recorded.
 */
class FakeRecentSearchesRepository(
    initial: List<RecentSearch> = emptyList(),
    private val clock: AppClock = FakeClock(),
) : RecentSearchesRepository {
    val recents = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observe(limit: Int): Flow<List<RecentSearch>> =
        recents.map { list -> list.sortedByDescending { it.searchedAt }.take(limit) }

    override suspend fun record(query: String, directionId: String, opened: EntrySummary?) {
        val list = recents.value
        val existing = list.firstOrNull { it.query == query && it.directionId == directionId }
        val row = RecentSearch(
            id = existing?.id ?: nextId++,
            query = query,
            directionId = directionId,
            entryId = opened?.id ?: existing?.entryId,
            headword = opened?.headword ?: existing?.headword,
            searchedAt = clock.now().toEpochMilli(),
        )
        recents.value = (listOf(row) + list.filterNot { it.id == row.id })
            .sortedByDescending { it.searchedAt }
            .take(MAX_ROWS)
    }

    override suspend fun remove(id: Long) {
        recents.value = recents.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        recents.value = emptyList()
    }

    private companion object {
        const val MAX_ROWS = 50
    }
}
