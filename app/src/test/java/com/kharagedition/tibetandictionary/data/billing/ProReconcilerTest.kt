package com.kharagedition.tibetandictionary.data.billing

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Quota
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeBillingRepository
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProReconcilerTest {

    private val billing = FakeBillingRepository()
    private val auth = FakeAuthRepository(Account("u1", isGuest = false, name = "Tashi", email = "t@x.com"))
    private val translation = FakeTranslationRepository().apply { repeat(5) { syncResults.add(Plan.FREE) } }
    private val clock = FakeClock()

    private val activePro = ProEntitlement(
        active = true,
        plan = ProPlan.MONTHLY,
        expiresAt = Instant.parse("2026-11-19T09:00:00Z"),
        willRenew = true,
        billingIssue = false,
    )

    private fun quota(plan: Plan, used: Int = 0) =
        QuotaState(Quota(plan, used, limit = 10, resetsAt = Instant.parse("2026-10-20T00:00:00Z"), maxChars = 100), 10)

    private fun TestScope.start(): Unit = ProReconciler(billing, auth, translation, clock, backgroundScope).start()

    @Test
    fun `the store says Pro but the server says free - syncs once`() = runTest(UnconfinedTestDispatcher()) {
        translation.quota.value = quota(Plan.FREE)
        start()

        billing.entitlement.value = activePro

        assertThat(translation.syncCalls).isEqualTo(1)
    }

    @Test
    fun `a mismatch first makes the store act for this Google user`() = runTest(UnconfinedTestDispatcher()) {
        translation.quota.value = quota(Plan.FREE)
        start()

        billing.entitlement.value = activePro

        assertThat(billing.identifyCalls).containsExactly("u1")
        assertThat(translation.syncCalls).isEqualTo(1)
    }

    @Test
    fun `a store that can't act for this user is not synced, and tries again 5 minutes later`() =
        runTest(UnconfinedTestDispatcher()) {
            billing.identifyFailures = 1
            billing.entitlement.value = activePro
            translation.quota.value = quota(Plan.FREE)
            start()
            assertThat(translation.syncCalls).isEqualTo(0)

            translation.quota.value = quota(Plan.FREE, used = 1)
            assertThat(billing.identifyCalls).hasSize(1)

            clock.instant = clock.instant.plus(Duration.ofMinutes(5))
            translation.quota.value = quota(Plan.FREE, used = 2)
            assertThat(billing.identifyCalls).containsExactly("u1", "u1")
            assertThat(translation.syncCalls).isEqualTo(1)
        }

    @Test
    fun `another mismatch within 5 minutes does not sync again`() = runTest(UnconfinedTestDispatcher()) {
        billing.entitlement.value = activePro
        translation.quota.value = quota(Plan.FREE)
        start()

        clock.instant = clock.instant.plus(Duration.ofMinutes(4).plusSeconds(59))
        translation.quota.value = quota(Plan.FREE, used = 1)

        assertThat(translation.syncCalls).isEqualTo(1)
    }

    @Test
    fun `a mismatch 5 minutes later syncs again`() = runTest(UnconfinedTestDispatcher()) {
        billing.entitlement.value = activePro
        translation.quota.value = quota(Plan.FREE)
        start()

        clock.instant = clock.instant.plus(Duration.ofMinutes(5))
        translation.quota.value = quota(Plan.FREE, used = 1)

        assertThat(translation.syncCalls).isEqualTo(2)
    }

    @Test
    fun `the server already says Pro - no sync`() = runTest(UnconfinedTestDispatcher()) {
        billing.entitlement.value = activePro
        translation.quota.value = quota(Plan.PRO)
        start()

        assertThat(translation.syncCalls).isEqualTo(0)
    }

    @Test
    fun `a guest is never synced`() = runTest(UnconfinedTestDispatcher()) {
        auth.account.value = Account("guest-1", isGuest = true, name = null, email = null)
        billing.entitlement.value = activePro
        translation.quota.value = quota(Plan.FREE)
        start()

        assertThat(translation.syncCalls).isEqualTo(0)
    }

    @Test
    fun `an inactive or unknown entitlement is not synced`() = runTest(UnconfinedTestDispatcher()) {
        translation.quota.value = quota(Plan.FREE)
        start()
        billing.entitlement.value = activePro.copy(active = false)

        assertThat(translation.syncCalls).isEqualTo(0)
    }

    @Test
    fun `no quota yet - waits for it`() = runTest(UnconfinedTestDispatcher()) {
        billing.entitlement.value = activePro
        start()
        assertThat(translation.syncCalls).isEqualTo(0)

        translation.quota.value = quota(Plan.FREE)
        assertThat(translation.syncCalls).isEqualTo(1)
    }

    @Test
    fun `starting twice syncs once`() = runTest(UnconfinedTestDispatcher()) {
        billing.entitlement.value = activePro
        translation.quota.value = quota(Plan.FREE)
        val reconciler = ProReconciler(billing, auth, translation, clock, backgroundScope)
        reconciler.start()
        reconciler.start()

        assertThat(translation.syncCalls).isEqualTo(1)
    }
}
