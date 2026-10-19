package com.kharagedition.tibetandictionary.core.init

import android.app.NotificationManager
import android.content.Context
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Fresh-start wipe of what the 2.x app left behind (its database, preferences and notifications). Runs once. */
@Singleton
class LegacyCleanup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: UserPreferencesRepository,
) {
    suspend fun runOnce() {
        if (prefs.snapshot().legacyCleanupDone) return
        context.deleteDatabase(LEGACY_DB) // also removes -journal / -shm / -wal when SQLite created them
        for (suffix in listOf("-shm", "-wal")) {
            context.getDatabasePath(LEGACY_DB + suffix).delete()
        }
        context.deleteSharedPreferences(LEGACY_PREFS)
        context.deleteSharedPreferences("${context.packageName}_preferences")
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
        prefs.setLegacyCleanupDone(true)
    }

    private companion object {
        const val LEGACY_DB = "dictionarydb.db"
        const val LEGACY_PREFS = "com.kharagedition.dictionary"
    }
}
