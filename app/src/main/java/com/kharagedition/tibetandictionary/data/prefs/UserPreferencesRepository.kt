package com.kharagedition.tibetandictionary.data.prefs

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val prefs: Flow<UserPrefs>

    suspend fun snapshot(): UserPrefs

    suspend fun setOnboarded(value: Boolean)

    suspend fun setAppLanguage(language: AppLanguage)

    suspend fun setAppearance(appearance: Appearance)

    suspend fun setTextSize(size: AppTextSize)

    /** The repository owns the invariant: values are clamped to [LineSpacing] on write and on read. */
    suspend fun setLineSpacing(spacing: Float)

    suspend fun setDefaultDirectionId(id: String)

    /** Null clears it. */
    suspend fun setLastUsedFolderId(id: Long?)

    /** [minuteOfDay] is minutes after local midnight, clamped to [WotdNotificationMinute] on write and on read. */
    suspend fun setWotdNotification(enabled: Boolean, minuteOfDay: Int)

    suspend fun setLegacyCleanupDone(done: Boolean)
}
