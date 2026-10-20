package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [ActivityRepository] over the user.db activity DAO; a day is the clock's local date, stored as `yyyy-MM-dd`. */
@Singleton
class ActivityRepositoryImpl @Inject constructor(
    db: UserDatabase,
    private val clock: AppClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ActivityRepository {
    private val dao = db.activityDayDao()

    override suspend fun markSearch() {
        withContext(io) { dao.incrementSearches(todayKey()) }
    }

    override suspend fun markEntryOpened() {
        withContext(io) { dao.incrementEntriesOpened(todayKey()) }
    }

    override fun observeActiveDays(limit: Int): Flow<List<LocalDate>> =
        dao.observeRecentDates(limit).map { dates -> dates.map { LocalDate.parse(it, KEY_FORMAT) } }.flowOn(io)

    private fun todayKey(): String = KEY_FORMAT.format(clock.today())

    private companion object {
        val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
