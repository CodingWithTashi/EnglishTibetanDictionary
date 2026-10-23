package com.kharagedition.tibetandictionary.ui.model

import android.app.Activity
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.auth.SignInResult
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import javax.inject.Inject

/** Google sign-in from any button that offers it (Translate, Search, Profile), with the toast to show after. */
class SignInAction @Inject constructor(
    private val auth: AuthRepository,
    private val translation: TranslationRepository,
    private val analytics: Analytics,
) {
    /**
     * Runs Google sign-in; returns the toast to show, or null when cancelled. Logs sign_in_prompt and
     * sign_in_success.
     */
    suspend fun run(activity: Activity, source: PromptSource): UiText? {
        analytics.log(AnalyticsEvent.SignInPrompt(source))
        return when (val result = auth.signInWithGoogle(activity)) {
            is SignInResult.Success -> {
                analytics.log(AnalyticsEvent.SignInSuccess(result.linked))
                translation.refresh(force = true)
                // A guest quota here means the refresh failed, so its count is not the signed-in one.
                val quota = translation.quota.value?.takeIf { it.quota.plan != Plan.GUEST }
                if (quota == null) {
                    UiText(R.string.signed_in)
                } else {
                    UiText(R.string.signed_in_left, counts = listOf(quota.remaining))
                }
            }
            SignInResult.Cancelled -> null
            is SignInResult.Failed -> UiText(R.string.sign_in_failed)
        }
    }
}
