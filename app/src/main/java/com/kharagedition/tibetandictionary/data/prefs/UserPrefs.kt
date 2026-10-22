package com.kharagedition.tibetandictionary.data.prefs

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack

/**
 * User settings kept in the `user_prefs` DataStore.
 * [defaultDirectionId] and [lastUsedFolderId] are those of the current [dictionaryPack].
 */
data class UserPrefs(
    val onboarded: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.EN,
    val appearance: Appearance = Appearance.SYSTEM,
    val textSize: AppTextSize = AppTextSize.DEFAULT,
    /** Tibetan line-height setting, always within [LineSpacing]. */
    val lineSpacing: Float = LineSpacing.DEFAULT,
    /** The pack in use: the stored choice, else the build's default pack. */
    val dictionaryPack: DictionaryPack = DictionaryPack.TIBETAN,
    /** True once a pack has been chosen explicitly through `setDictionaryPack`. */
    val dictionaryPackChosen: Boolean = false,
    val defaultDirectionId: String = "bo-en",
    /** Folder the last word was saved to in the current pack; new saves go there. */
    val lastUsedFolderId: Long? = null,
    val wotdNotificationEnabled: Boolean = false,
    /** Local time of the word-of-the-day notification, always within [WotdNotificationMinute] (540 = 09:00). */
    val wotdNotificationMinute: Int = WotdNotificationMinute.DEFAULT,
    val legacyCleanupDone: Boolean = false,
)
