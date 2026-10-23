package com.kharagedition.tibetandictionary.data.auth

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.data.translation.ApiResult
import com.kharagedition.tibetandictionary.data.translation.TranslationRefusal
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationApi
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeleteAccountTest {

    private val account = Account("u1", isGuest = false, name = "Tenzin Dolma", email = "tenzin@example.com")
    private val api = FakeTranslationApi()
    private val fakeAuth = FakeAuthRepository(account)
    private val analytics = RecordingAnalytics()

    /** The server deletes the user had been asked to by the time the app signed out; null until it signs out. */
    private var deletesAtSignOut: Int? = null

    private val auth = object : AuthRepository by fakeAuth {
        override suspend fun signOut() {
            deletesAtSignOut = api.deleteCalls
            fakeAuth.signOut()
        }
    }

    private val deleteAccount = DeleteAccount(api, auth, analytics)

    @Test
    fun `success deletes on the server then signs out and logs`() = runTest {
        api.deleteResults += ApiResult.Ok(Unit)

        assertThat(deleteAccount()).isTrue()

        assertThat(deletesAtSignOut).isEqualTo(1)
        assertThat(fakeAuth.account.value).isNull()
        assertThat(analytics.events).containsExactly(AnalyticsEvent.AccountDeleted)
    }

    @Test
    fun `a server failure keeps the user signed in`() = runTest {
        api.deleteResults += ApiResult.Refused(TranslationRefusal.Offline)

        assertThat(deleteAccount()).isFalse()

        assertThat(api.deleteCalls).isEqualTo(1)
        assertThat(fakeAuth.signOutCalls).isEqualTo(0)
        assertThat(fakeAuth.account.value).isEqualTo(account)
        assertThat(analytics.events).isEmpty()
    }

    @Test
    fun `an account already deleted elsewhere counts as deleted`() = runTest {
        api.deleteResults += ApiResult.Refused(TranslationRefusal.AccountGone)

        assertThat(deleteAccount()).isTrue()

        assertThat(fakeAuth.account.value).isNull()
        assertThat(analytics.events).isEmpty()
    }
}
