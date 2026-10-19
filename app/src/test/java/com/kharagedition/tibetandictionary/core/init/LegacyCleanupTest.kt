package com.kharagedition.tibetandictionary.core.init

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class LegacyCleanupTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = FakeUserPreferencesRepository()
    private val cleanup = LegacyCleanup(context, prefs)

    private val legacyPrefNames = listOf("com.kharagedition.dictionary", "${context.packageName}_preferences")
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun createLegacyDatabase() {
        context.openOrCreateDatabase(LEGACY_DB, Context.MODE_PRIVATE, null).use { it.execSQL("CREATE TABLE IF NOT EXISTS words(id INTEGER)") }
        for (suffix in listOf("-shm", "-wal")) context.getDatabasePath(LEGACY_DB + suffix).writeText("x")
    }

    private fun createLegacyPrefs() {
        legacyPrefNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { putString("theme", "dark") }
        }
    }

    private fun postLegacyNotification() {
        notifications.createNotificationChannel(NotificationChannel("legacy", "Legacy", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(context, "legacy").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        notifications.notify(1, notification)
    }

    private fun prefsFile(name: String) = File(context.dataDir, "shared_prefs/$name.xml")

    @Test
    fun `deletes the old database, preferences and notifications`() = runTest {
        createLegacyDatabase()
        createLegacyPrefs()
        postLegacyNotification()
        assertThat(context.getDatabasePath(LEGACY_DB).exists()).isTrue()
        legacyPrefNames.forEach { assertThat(prefsFile(it).exists()).isTrue() }
        assertThat(shadowOf(notifications).allNotifications).hasSize(1)

        cleanup.runOnce()

        for (suffix in listOf("", "-shm", "-wal")) {
            assertThat(context.getDatabasePath(LEGACY_DB + suffix).exists()).isFalse()
        }
        assertThat(context.databaseList()).asList().doesNotContain(LEGACY_DB)
        legacyPrefNames.forEach { name ->
            assertThat(prefsFile(name).exists()).isFalse()
            assertThat(context.getSharedPreferences(name, Context.MODE_PRIVATE).all).isEmpty()
        }
        assertThat(shadowOf(notifications).allNotifications).isEmpty()
        assertThat(prefs.snapshot().legacyCleanupDone).isTrue()
    }

    @Test
    fun `runs only once`() = runTest {
        cleanup.runOnce()
        createLegacyDatabase()
        createLegacyPrefs()

        cleanup.runOnce()

        assertThat(context.getDatabasePath(LEGACY_DB).exists()).isTrue()
        legacyPrefNames.forEach { assertThat(prefsFile(it).exists()).isTrue() }
    }

    @Test
    fun `a fresh install with nothing to clean up is marked done`() = runTest {
        cleanup.runOnce()

        assertThat(prefs.snapshot().legacyCleanupDone).isTrue()
    }

    private companion object {
        const val LEGACY_DB = "dictionarydb.db"
    }
}
