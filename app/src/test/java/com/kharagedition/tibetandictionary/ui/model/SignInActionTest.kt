package com.kharagedition.tibetandictionary.ui.model

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.auth.SignInResult
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Quota
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SignInActionTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val auth = FakeAuthRepository(Account("guest-1", isGuest = true, name = null, email = null))
    private val translation = FakeTranslationRepository()
    private val analytics = RecordingAnalytics()
    private val action = SignInAction(auth, translation, analytics)

    @Test
    fun `success toasts the remaining after refresh`() = runTest {
        translation.quota.value = QuotaState(Quota(Plan.GUEST, 3, 3, Instant.EPOCH, 100), freeLimit = 10)
        translation.quotaAfterRefresh = QuotaState(Quota(Plan.FREE, 3, 10, Instant.EPOCH, 100), freeLimit = 10)

        val toast = action.run(activity, PromptSource.TRANSLATE_LIMIT)

        assertThat(toast).isEqualTo(UiText(R.string.signed_in_left, counts = listOf(7)))
        assertThat(translation.refreshCalls).containsExactly(true)
    }

    @Test
    fun `success with no quota yet just says signed in`() = runTest {
        assertThat(action.run(activity, PromptSource.PROFILE_ACCOUNT)).isEqualTo(UiText(R.string.signed_in))
    }

    @Test
    fun `success with the guest quota still showing just says signed in`() = runTest {
        translation.quota.value = QuotaState(Quota(Plan.GUEST, 3, 3, Instant.EPOCH, 100), freeLimit = 10)
        assertThat(action.run(activity, PromptSource.TRANSLATE_LIMIT)).isEqualTo(UiText(R.string.signed_in))
    }

    @Test
    fun `cancel returns null and logs only the prompt`() = runTest {
        auth.signInResult = SignInResult.Cancelled
        assertThat(action.run(activity, PromptSource.TODAY_SHEET)).isNull()
        assertThat(analytics.events).containsExactly(AnalyticsEvent.SignInPrompt(PromptSource.TODAY_SHEET))
        assertThat(translation.refreshCalls).isEmpty()
    }

    @Test
    fun `failure toasts sign_in_failed`() = runTest {
        auth.signInResult = SignInResult.Failed(IllegalStateException("broken"))
        assertThat(action.run(activity, PromptSource.TRANSLATE_LIMIT)).isEqualTo(UiText(R.string.sign_in_failed))
        assertThat(translation.refreshCalls).isEmpty()
    }

    @Test
    fun `logs sign_in_success with linked`() = runTest {
        auth.signInResult = SignInResult.Success(linked = true)
        action.run(activity, PromptSource.SEARCH_LIMIT)
        assertThat(analytics.events).containsExactly(
            AnalyticsEvent.SignInPrompt(PromptSource.SEARCH_LIMIT),
            AnalyticsEvent.SignInSuccess(linked = true),
        ).inOrder()
    }
}
