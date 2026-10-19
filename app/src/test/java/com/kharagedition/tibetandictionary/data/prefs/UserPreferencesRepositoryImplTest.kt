package com.kharagedition.tibetandictionary.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = storeScope) { File(tmp.root, "user_prefs.preferences_pb") }
    }
    private val repo by lazy { UserPreferencesRepositoryImpl(store) }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun defaults() = runTest {
        val p = repo.snapshot()

        assertThat(p).isEqualTo(UserPrefs())
        assertThat(p.onboarded).isFalse()
        assertThat(p.appLanguage).isEqualTo(AppLanguage.EN)
        assertThat(p.appearance).isEqualTo(Appearance.SYSTEM)
        assertThat(p.textSize).isEqualTo(AppTextSize.DEFAULT)
        assertThat(p.lineSpacing).isEqualTo(1.9f)
        assertThat(p.defaultDirectionId).isEqualTo("bo-en")
        assertThat(p.lastUsedFolderId).isNull()
        assertThat(p.wotdNotificationEnabled).isFalse()
        assertThat(p.wotdNotificationMinute).isEqualTo(540)
        assertThat(p.legacyCleanupDone).isFalse()
    }

    @Test
    fun roundTripsEveryField() = runTest {
        repo.setOnboarded(true)
        repo.setAppLanguage(AppLanguage.BO)
        repo.setAppearance(Appearance.DARK)
        repo.setTextSize(AppTextSize.LARGEST)
        repo.setLineSpacing(2.1f)
        repo.setDefaultDirectionId("en-bo")
        repo.setLastUsedFolderId(7L)
        repo.setWotdNotification(enabled = true, minuteOfDay = 1200)
        repo.setLegacyCleanupDone(true)

        assertThat(repo.snapshot()).isEqualTo(
            UserPrefs(
                onboarded = true,
                appLanguage = AppLanguage.BO,
                appearance = Appearance.DARK,
                textSize = AppTextSize.LARGEST,
                lineSpacing = 2.1f,
                defaultDirectionId = "en-bo",
                lastUsedFolderId = 7L,
                wotdNotificationEnabled = true,
                wotdNotificationMinute = 1200,
                legacyCleanupDone = true,
            ),
        )
    }

    @Test
    fun clearingLastUsedFolderStoresNull() = runTest {
        repo.setLastUsedFolderId(7L)
        repo.setLastUsedFolderId(null)

        assertThat(repo.snapshot().lastUsedFolderId).isNull()
    }

    @Test
    fun prefsFlowEmitsChanges() = runTest {
        repo.prefs.test {
            assertThat(awaitItem().onboarded).isFalse()
            repo.setOnboarded(true)
            assertThat(awaitItem().onboarded).isTrue()
        }
    }

    @Test
    fun lineSpacingIsClampedOnWrite() = runTest {
        repo.setLineSpacing(3f)
        assertThat(repo.snapshot().lineSpacing).isEqualTo(2.3f)

        repo.setLineSpacing(1f)
        assertThat(repo.snapshot().lineSpacing).isEqualTo(1.5f)
    }

    @Test
    fun lineSpacingSnapsToTenthsOnWrite() = runTest {
        repo.setLineSpacing(1.94f)
        assertThat(repo.snapshot().lineSpacing).isEqualTo(1.9f)

        repo.setLineSpacing(1.5f + 0.1f + 0.1f + 0.1f)
        assertThat(repo.snapshot().lineSpacing).isEqualTo(1.8f)
    }

    @Test
    fun lineSpacingIsClampedOnRead() = runTest {
        store.edit { it[floatPreferencesKey("line_spacing")] = 9f }
        assertThat(repo.snapshot().lineSpacing).isEqualTo(2.3f)

        store.edit { it[floatPreferencesKey("line_spacing")] = 0.2f }
        assertThat(repo.snapshot().lineSpacing).isEqualTo(1.5f)

        store.edit { it[floatPreferencesKey("line_spacing")] = Float.NaN }
        assertThat(repo.snapshot().lineSpacing).isEqualTo(1.9f)
    }

    @Test
    fun wotdNotificationMinuteStaysWithinTheDay() = runTest {
        repo.setWotdNotification(enabled = true, minuteOfDay = 5000)
        assertThat(repo.snapshot().wotdNotificationMinute).isEqualTo(1439)

        store.edit { it[intPreferencesKey("wotd_notification_minute")] = -5 }
        assertThat(repo.snapshot().wotdNotificationMinute).isEqualTo(0)
    }

    @Test
    fun readFailureWithIOExceptionGivesDefaults() = runTest {
        val failing = UserPreferencesRepositoryImpl(FailingDataStore(IOException("disk unavailable")))

        assertThat(failing.snapshot()).isEqualTo(UserPrefs())
    }

    @Test
    fun readFailureWithOtherExceptionsIsRethrown() = runTest {
        val failing = UserPreferencesRepositoryImpl(FailingDataStore(IllegalStateException("bug")))

        val error = runCatching { failing.snapshot() }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() = runTest {
        store.edit {
            it[stringPreferencesKey("app_language")] = "fr"
            it[stringPreferencesKey("appearance")] = "SEPIA"
            it[stringPreferencesKey("text_size")] = "HUGE"
        }

        val p = repo.snapshot()
        assertThat(p.appLanguage).isEqualTo(AppLanguage.EN)
        assertThat(p.appearance).isEqualTo(Appearance.SYSTEM)
        assertThat(p.textSize).isEqualTo(AppTextSize.DEFAULT)
    }
}

/** A [DataStore] whose reads and writes always fail with [error]. */
private class FailingDataStore(private val error: Throwable) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw error }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw error
}
