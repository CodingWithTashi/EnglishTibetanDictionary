package com.kharagedition.tibetandictionary.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val prefs: Flow<UserPrefs> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toUserPrefs() }

    override suspend fun snapshot(): UserPrefs = prefs.first()

    override suspend fun setOnboarded(value: Boolean) = put(ONBOARDED, value)
    override suspend fun setAppLanguage(language: AppLanguage) = put(APP_LANGUAGE, language.tag)
    override suspend fun setAppearance(appearance: Appearance) = put(APPEARANCE, appearance.name)
    override suspend fun setTextSize(size: AppTextSize) = put(TEXT_SIZE, size.name)
    override suspend fun setLineSpacing(spacing: Float) = put(LINE_SPACING, LineSpacing.clamp(spacing))
    override suspend fun setDefaultDirectionId(id: String) = put(DEFAULT_DIRECTION_ID, id)
    override suspend fun setLastUsedFolderId(id: Long?) {
        store.edit { if (id == null) it.remove(LAST_USED_FOLDER_ID) else it[LAST_USED_FOLDER_ID] = id }
    }
    override suspend fun setWotdNotification(enabled: Boolean, minuteOfDay: Int) {
        store.edit {
            it[WOTD_NOTIFICATION_ENABLED] = enabled
            it[WOTD_NOTIFICATION_MINUTE] = WotdNotificationMinute.clamp(minuteOfDay)
        }
    }
    override suspend fun setLegacyCleanupDone(done: Boolean) = put(LEGACY_CLEANUP_DONE, done)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private fun Preferences.toUserPrefs(): UserPrefs {
        val defaults = UserPrefs()
        return UserPrefs(
            onboarded = this[ONBOARDED] ?: defaults.onboarded,
            appLanguage = AppLanguage.fromTag(this[APP_LANGUAGE]),
            appearance = Appearance.fromName(this[APPEARANCE]),
            textSize = AppTextSize.fromName(this[TEXT_SIZE]),
            lineSpacing = LineSpacing.clamp(this[LINE_SPACING] ?: defaults.lineSpacing),
            defaultDirectionId = this[DEFAULT_DIRECTION_ID] ?: defaults.defaultDirectionId,
            lastUsedFolderId = this[LAST_USED_FOLDER_ID],
            wotdNotificationEnabled = this[WOTD_NOTIFICATION_ENABLED] ?: defaults.wotdNotificationEnabled,
            wotdNotificationMinute = WotdNotificationMinute.clamp(
                this[WOTD_NOTIFICATION_MINUTE] ?: defaults.wotdNotificationMinute,
            ),
            legacyCleanupDone = this[LEGACY_CLEANUP_DONE] ?: defaults.legacyCleanupDone,
        )
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val APPEARANCE = stringPreferencesKey("appearance")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val DEFAULT_DIRECTION_ID = stringPreferencesKey("default_direction_id")
        val LAST_USED_FOLDER_ID = longPreferencesKey("last_used_folder_id")
        val WOTD_NOTIFICATION_ENABLED = booleanPreferencesKey("wotd_notification_enabled")
        val WOTD_NOTIFICATION_MINUTE = intPreferencesKey("wotd_notification_minute")
        val LEGACY_CLEANUP_DONE = booleanPreferencesKey("legacy_cleanup_done")
    }
}
