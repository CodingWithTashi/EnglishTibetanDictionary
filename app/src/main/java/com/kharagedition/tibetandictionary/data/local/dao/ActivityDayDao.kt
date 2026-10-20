package com.kharagedition.tibetandictionary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kharagedition.tibetandictionary.data.local.entity.ActivityDayEntity
import kotlinx.coroutines.flow.Flow

/**
 * Per-day usage counters (user.db), keyed by local date `yyyy-MM-dd`. Increments insert the day first, then update,
 * because `INSERT … ON CONFLICT DO UPDATE` needs SQLite 3.24 (API 30) and minSdk is 24.
 */
@Dao
abstract class ActivityDayDao {

    @Query("SELECT * FROM activity_days WHERE date = :date")
    abstract suspend fun get(date: String): ActivityDayEntity?

    /** Days with any activity, newest first. */
    @Query("SELECT date FROM activity_days WHERE searches > 0 OR entries_opened > 0 ORDER BY date DESC LIMIT :limit")
    abstract fun observeRecentDates(limit: Int): Flow<List<String>>

    @Transaction
    open suspend fun incrementSearches(date: String) {
        insertDay(ActivityDayEntity(date = date, searches = 0, entriesOpened = 0))
        addSearch(date)
    }

    @Transaction
    open suspend fun incrementEntriesOpened(date: String) {
        insertDay(ActivityDayEntity(date = date, searches = 0, entriesOpened = 0))
        addEntryOpened(date)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertDay(day: ActivityDayEntity)

    @Query("UPDATE activity_days SET searches = searches + 1 WHERE date = :date")
    protected abstract suspend fun addSearch(date: String)

    @Query("UPDATE activity_days SET entries_opened = entries_opened + 1 WHERE date = :date")
    protected abstract suspend fun addEntryOpened(date: String)
}
