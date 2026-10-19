package com.kharagedition.tibetandictionary.data.prefs

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance

/** User settings kept in the `user_prefs` DataStore. */
data class UserPrefs(
    val onboarded: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.EN,
    val appearance: Appearance = Appearance.SYSTEM,
    val textSize: AppTextSize = AppTextSize.DEFAULT,
    /** Tibetan line-height setting, always within [LineSpacing]. */
    val lineSpacing: Float = LineSpacing.DEFAULT,
    val defaultDirectionId: String = "bo-en",
    /** Folder the last word was saved to; new saves go there. */
    val lastUsedFolderId: Long? = null,
    val wotdNotificationEnabled: Boolean = false,
    /** Local time of the word-of-the-day notification, in minutes after midnight (540 = 09:00). */
    val wotdNotificationMinute: Int = 540,
    val legacyCleanupDone: Boolean = false,
)
