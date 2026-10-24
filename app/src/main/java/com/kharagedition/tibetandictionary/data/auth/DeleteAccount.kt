package com.kharagedition.tibetandictionary.data.auth

import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.data.translation.ApiResult
import com.kharagedition.tibetandictionary.data.translation.TranslationApi
import com.kharagedition.tibetandictionary.data.translation.TranslationRefusal
import javax.inject.Inject

/**
 * Deletes the signed-in Google account (Play's account-deletion rule): the server deletes its usage history and the
 * Firebase user, then the app signs out locally, which also forgets the saved Google credential. Saved words and
 * translations live only on this device and stay. Guests have nothing to delete; the server refuses them. An
 * account already deleted elsewhere (another device, or the Firebase console) counts as deleted.
 */
class DeleteAccount @Inject constructor(
    private val api: TranslationApi,
    private val auth: AuthRepository,
    private val analytics: Analytics,
) {
    /** Server delete, then local sign-out; true on success. On failure the user stays signed in. */
    suspend operator fun invoke(): Boolean {
        when (val result = api.deleteAccount()) {
            is ApiResult.Ok -> analytics.log(AnalyticsEvent.AccountDeleted)
            is ApiResult.Refused -> if (result.refusal != TranslationRefusal.AccountGone) return false
        }
        auth.signOut()
        return true
    }
}
