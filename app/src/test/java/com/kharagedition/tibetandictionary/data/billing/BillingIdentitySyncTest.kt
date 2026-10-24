package com.kharagedition.tibetandictionary.data.billing

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeBillingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingIdentitySyncTest {

    private val billing = FakeBillingRepository()

    private fun google(uid: String) = Account(uid, isGuest = false, name = "Tashi", email = "t@x.com")
    private val guest = Account("guest-1", isGuest = true, name = null, email = null)

    @Test
    fun `follows the Google account, and a guest or nobody is anonymous`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(google("u1"))
        BillingIdentitySync(billing, auth, backgroundScope).start()
        assertThat(billing.identifyCalls).containsExactly("u1")

        auth.account.value = guest
        assertThat(billing.identifyCalls).containsExactly("u1", null).inOrder()

        auth.account.value = google("u2")
        assertThat(billing.identifyCalls).containsExactly("u1", null, "u2").inOrder()
    }

    @Test
    fun `nobody signed in is anonymous`() = runTest(UnconfinedTestDispatcher()) {
        BillingIdentitySync(billing, FakeAuthRepository(null), backgroundScope).start()

        assertThat(billing.identifyCalls).containsExactly(null)
    }

    @Test
    fun `the same user again, or a guest after nobody, is not identified twice`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(google("u1"))
        BillingIdentitySync(billing, auth, backgroundScope).start()

        auth.account.value = google("u1").copy(name = "Renamed")
        auth.account.value = null
        auth.account.value = guest

        assertThat(billing.identifyCalls).containsExactly("u1", null).inOrder()
    }

    @Test
    fun `unavailable billing is never identified`() = runTest(UnconfinedTestDispatcher()) {
        val unavailable = FakeBillingRepository(available = false)
        val auth = FakeAuthRepository(google("u1"))
        BillingIdentitySync(unavailable, auth, backgroundScope).start()
        auth.account.value = null

        assertThat(unavailable.identifyCalls).isEmpty()
    }

    @Test
    fun `starting twice collects once`() = runTest(UnconfinedTestDispatcher()) {
        val sync = BillingIdentitySync(billing, FakeAuthRepository(google("u1")), backgroundScope)
        sync.start()
        sync.start()

        assertThat(billing.identifyCalls).containsExactly("u1")
    }
}
