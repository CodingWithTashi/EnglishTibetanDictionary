package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.prefs.WotdNotificationMinute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [UserPreferencesRepository] with the same clamping and per-pack direction and folder as the real one. */
class FakeUserPreferencesRepository(initial: UserPrefs = UserPrefs()) : UserPreferencesRepository {
    override val prefs = MutableStateFlow(initial)
    private val directionByPack = mutableMapOf(initial.dictionaryPack to initial.defaultDirectionId)
    private val folderByPack = mutableMapOf(initial.dictionaryPack to initial.lastUsedFolderId)

    override suspend fun snapshot(): UserPrefs = prefs.value

    override suspend fun setOnboarded(value: Boolean) = prefs.update { it.copy(onboarded = value) }
    override suspend fun setAppLanguage(language: AppLanguage) = prefs.update { it.copy(appLanguage = language) }
    override suspend fun setAppearance(appearance: Appearance) = prefs.update { it.copy(appearance = appearance) }
    override suspend fun setTextSize(size: AppTextSize) = prefs.update { it.copy(textSize = size) }
    override suspend fun setLineSpacing(spacing: Float) = prefs.update { it.copy(lineSpacing = LineSpacing.clamp(spacing)) }
    override suspend fun setDictionaryPack(pack: DictionaryPack) = prefs.update {
        it.copy(
            dictionaryPack = pack,
            dictionaryPackChosen = true,
            defaultDirectionId = directionByPack[pack] ?: pack.defaultDirectionId,
            lastUsedFolderId = folderByPack[pack],
        )
    }
    override suspend fun setDefaultDirectionId(id: String) = prefs.update {
        directionByPack[it.dictionaryPack] = id
        it.copy(defaultDirectionId = id)
    }
    override suspend fun setLastUsedFolderId(id: Long?) = prefs.update {
        folderByPack[it.dictionaryPack] = id
        it.copy(lastUsedFolderId = id)
    }
    override suspend fun setWotdNotification(enabled: Boolean, minuteOfDay: Int) = prefs.update {
        it.copy(wotdNotificationEnabled = enabled, wotdNotificationMinute = WotdNotificationMinute.clamp(minuteOfDay))
    }
    override suspend fun setLegacyCleanupDone(done: Boolean) = prefs.update { it.copy(legacyCleanupDone = done) }
}
