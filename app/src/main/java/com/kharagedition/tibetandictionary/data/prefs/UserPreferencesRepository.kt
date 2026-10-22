package com.kharagedition.tibetandictionary.data.prefs

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
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

    /** Switches the pack in use; the default direction and last-used folder are kept per pack. */
    suspend fun setDictionaryPack(pack: DictionaryPack)

    /** Stored for the current pack. */
    suspend fun setDefaultDirectionId(id: String)

    /** Stored for the current pack. Null clears it. */
    suspend fun setLastUsedFolderId(id: Long?)

    /** [minuteOfDay] is minutes after local midnight, clamped to [WotdNotificationMinute] on write and on read. */
    suspend fun setWotdNotification(enabled: Boolean, minuteOfDay: Int)

    suspend fun setLegacyCleanupDone(done: Boolean)
}
