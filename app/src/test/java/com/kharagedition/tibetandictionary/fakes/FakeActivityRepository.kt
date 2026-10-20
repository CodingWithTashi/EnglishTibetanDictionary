package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [ActivityRepository]; [days] holds per-day counts keyed by [clock]'s local date. */
class FakeActivityRepository(private val clock: AppClock = FakeClock()) : ActivityRepository {
    data class Day(val searches: Int = 0, val entriesOpened: Int = 0)

    val days = MutableStateFlow<Map<LocalDate, Day>>(emptyMap())

    override suspend fun markSearch() = bumpToday { it.copy(searches = it.searches + 1) }

    override suspend fun markEntryOpened() = bumpToday { it.copy(entriesOpened = it.entriesOpened + 1) }

    override fun observeActiveDays(limit: Int): Flow<List<LocalDate>> =
        days.map { it.keys.sortedDescending().take(limit) }

    private fun bumpToday(change: (Day) -> Day) {
        val today = clock.today()
        days.update { it + (today to change(it[today] ?: Day())) }
    }
}
