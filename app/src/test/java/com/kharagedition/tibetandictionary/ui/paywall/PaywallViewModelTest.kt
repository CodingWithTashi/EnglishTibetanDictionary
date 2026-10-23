package com.kharagedition.tibetandictionary.ui.paywall

import android.app.Activity
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.billing.BillingError
import com.kharagedition.tibetandictionary.data.billing.OffersResult
import com.kharagedition.tibetandictionary.data.billing.ProEntitlement
import com.kharagedition.tibetandictionary.data.billing.ProOffer
import com.kharagedition.tibetandictionary.data.billing.ProOffers
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.data.billing.PurchaseOutcome
import com.kharagedition.tibetandictionary.data.billing.RestoreOutcome
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Quota
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeBillingRepository
import com.kharagedition.tibetandictionary.fakes.FakeBillingRepository.PurchaseCall
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import com.kharagedition.tibetandictionary.ui.model.SignInAction
import com.kharagedition.tibetandictionary.ui.model.UiText
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PaywallViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val google = Account("u1", isGuest = false, name = "Tashi", email = "t@x.com")
    private val guest = Account("guest-1", isGuest = true, name = null, email = null)

    private val billing = FakeBillingRepository()
    private val auth = FakeAuthRepository(google)
    private val translation = FakeTranslationRepository().apply {
        availability.value = TranslationAvailability(enabled = true, pairs = emptySet(), billingEnabled = true)
        quota.value = quota(Plan.FREE)
    }
    private val analytics = RecordingAnalytics()
    private val activity = mockk<Activity>(relaxed = true)

    private val monthly = ProOffer(ProPlan.MONTHLY, price = "₹49.00", pricePerMonth = null)
    private val yearly = ProOffer(ProPlan.YEARLY, price = "₹399.00", pricePerMonth = "₹33.25")
    private val both = ProOffers(monthly = monthly, yearly = yearly)
    private val activeMonthly = ProEntitlement(
        active = true,
        plan = ProPlan.MONTHLY,
        expiresAt = Instant.parse("2026-11-19T09:00:00Z"),
        willRenew = true,
        billingIssue = false,
    )

    private fun quota(plan: Plan) = QuotaState(
        Quota(plan, used = 0, limit = 10, resetsAt = Instant.parse("2026-10-20T00:00:00Z"), maxChars = 100),
        freeLimit = 10,
    )

    private fun viewModel() = PaywallViewModel(billing, auth, translation, SignInAction(auth, translation, analytics), analytics)

    private fun PaywallViewModel.sheet() = uiState.value.sheet

    /** Opened with both offers loaded; analytics cleared. */
    private fun offersOpen(mode: PaywallMode = PaywallMode.PURCHASE): PaywallViewModel {
        billing.offersResults.add(OffersResult.Loaded(both))
        return viewModel().also {
            it.open(PromptSource.TRANSLATE_LIMIT, mode)
            analytics.events.clear()
        }
    }

    // Mode resolution

    @Test
    fun `billing switched off - fake door, logged as interest`() {
        translation.availability.value = translation.availability.value!!.copy(billingEnabled = false)
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.FakeDoor)
        assertThat(analytics.events).containsExactly(
            AnalyticsEvent.ProInterest(PromptSource.TRANSLATE_LIMIT),
            AnalyticsEvent.PaywallView(PromptSource.TRANSLATE_LIMIT, "fake_door"),
        ).inOrder()
        assertThat(billing.offersCalls).isEqualTo(0)
    }

    @Test
    fun `no status yet - fake door`() {
        translation.availability.value = null
        val vm = viewModel()

        vm.open(PromptSource.SEARCH_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.FakeDoor)
    }

    @Test
    fun `a build without a RevenueCat key - fake door`() {
        billing.available = false
        val vm = viewModel()

        vm.open(PromptSource.PROFILE_PLAN, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.FakeDoor)
        assertThat(analytics.events).contains(AnalyticsEvent.ProInterest(PromptSource.PROFILE_PLAN))
    }

    @Test
    fun `a guest must sign in, then sees the offers`() {
        auth.account.value = guest
        billing.offersResults.add(OffersResult.Loaded(both))
        val vm = viewModel()

        vm.open(PromptSource.TODAY_SHEET, PaywallMode.PURCHASE)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.NeedsSignIn)
        assertThat(analytics.events).containsExactly(AnalyticsEvent.PaywallView(PromptSource.TODAY_SHEET, "sign_in"))

        vm.signIn(activity)

        assertThat(auth.signInCalls).isEqualTo(1)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY))
        assertThat(analytics.events).contains(AnalyticsEvent.PaywallView(PromptSource.TODAY_SHEET, "offers"))
    }

    @Test
    fun `nobody signed in must sign in`() {
        auth.account.value = null
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.NeedsSignIn)
    }

    // Store identity

    @Test
    fun `the offers load as the signed-in Google user`() {
        offersOpen()

        assertThat(billing.identifyCalls).containsExactly("u1")
    }

    @Test
    fun `a store that can't act for the user - error, no offers`() {
        billing.identifyFailures = 1
        billing.offersResults.add(OffersResult.Loaded(both))
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
        assertThat(billing.offersCalls).isEqualTo(0)
    }

    @Test
    fun `a purchase first makes the store act for the buyer, and Play never opens if it can't`() {
        val vm = offersOpen()
        billing.identifyFailures = 1
        billing.purchaseResults.add(PurchaseOutcome.Failed(BillingError.STORE))

        vm.purchase(activity)

        assertThat(billing.identifyCalls).containsExactly("u1", "u1").inOrder()
        assertThat(billing.purchaseCalls).isEmpty()
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
        assertThat(analytics.events.last())
            .isEqualTo(AnalyticsEvent.PurchaseResult("yearly", "error", code = "network", switch = false))
    }

    @Test
    fun `a restore first makes the store act for the user, and fails if it can't`() {
        val vm = offersOpen()
        billing.identifyFailures = 1
        billing.restoreResults.add(RestoreOutcome.NothingToRestore)

        vm.onEvent(PaywallEvent.Restore)

        assertThat(billing.restoreCalls).isEqualTo(0)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
    }

    @Test
    fun `signing in from the sheet as a returning Pro user - already Pro, not the plans`() = runTest {
        auth.account.value = guest
        billing.offersResults.add(OffersResult.Loaded(both))
        billing.entitlementAfterIdentify = activeMonthly
        translation.syncResults.add(Plan.PRO)
        val vm = viewModel()
        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        vm.effects.test {
            auth.account.value = google

            assertThat(vm.sheet()).isEqualTo(ProSheetUi.AlreadyPro)
            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(null))
        }
        assertThat(billing.offersCalls).isEqualTo(0)
    }

    @Test
    fun `loading until the offers arrive`() {
        val gate = CompletableDeferred<Unit>()
        billing.offersGate = gate
        billing.offersResults.add(OffersResult.Loaded(both))
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Loading)
        assertThat(analytics.events).isEmpty()
        gate.complete(Unit)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY))
    }

    @Test
    fun `offers preselect yearly and log the view once`() {
        billing.offersResults.add(OffersResult.Loaded(both))
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY))
        assertThat(analytics.events).containsExactly(AnalyticsEvent.PaywallView(PromptSource.TRANSLATE_LIMIT, "offers"))
    }

    @Test
    fun `only monthly on offer - monthly is selected`() {
        billing.offersResults.add(OffersResult.Loaded(ProOffers(monthly = monthly, yearly = null)))
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(ProOffers(monthly, null), selected = ProPlan.MONTHLY))
    }

    @Test
    fun `selecting monthly changes the selection`() {
        val vm = offersOpen()

        vm.onEvent(PaywallEvent.Select(ProPlan.MONTHLY))

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.MONTHLY))
    }

    @Test
    fun `offers failing - error, and retry reloads them`() {
        billing.offersResults.add(OffersResult.Failed(BillingError.NETWORK))
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)

        billing.offersResults.add(OffersResult.Loaded(both))
        vm.onEvent(PaywallEvent.Retry)

        assertThat(billing.offersCalls).isEqualTo(2)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY))
    }

    @Test
    fun `no plan on offer at all - error`() {
        billing.offersResults.add(OffersResult.Loaded(ProOffers(monthly = null, yearly = null)))
        val vm = viewModel()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
    }

    // Purchase

    @Test
    fun `a purchase syncs the server, closes with a welcome and logs success`() = runTest {
        billing.purchaseResults.add(PurchaseOutcome.Purchased)
        translation.syncResults.add(Plan.PRO)
        val vm = offersOpen()

        vm.effects.test {
            vm.purchase(activity)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(UiText(R.string.pro_welcome)))
        }
        assertThat(billing.purchaseCalls).containsExactly(PurchaseCall(ProPlan.YEARLY, null))
        assertThat(translation.syncCalls).isEqualTo(1)
        assertThat(analytics.events).containsExactly(
            AnalyticsEvent.PurchaseStart("yearly", PromptSource.TRANSLATE_LIMIT),
            AnalyticsEvent.PurchaseResult("yearly", "success", code = null, switch = false),
        ).inOrder()
    }

    @Test
    fun `a purchase whose sync fails still closes`() = runTest {
        billing.purchaseResults.add(PurchaseOutcome.Purchased)
        translation.syncResults.add(null)
        val vm = offersOpen()

        vm.effects.test {
            vm.purchase(activity)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(UiText(R.string.pro_welcome)))
        }
    }

    @Test
    fun `a cancelled purchase goes back to the offers`() {
        billing.purchaseResults.add(PurchaseOutcome.Cancelled)
        val vm = offersOpen()
        vm.onEvent(PaywallEvent.Select(ProPlan.MONTHLY))

        vm.purchase(activity)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.MONTHLY))
        assertThat(analytics.events.last())
            .isEqualTo(AnalyticsEvent.PurchaseResult("monthly", "cancelled", code = null, switch = false))
        assertThat(translation.syncCalls).isEqualTo(0)
    }

    @Test
    fun `a pending payment shows pending`() {
        billing.purchaseResults.add(PurchaseOutcome.Pending)
        val vm = offersOpen()

        vm.purchase(activity)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Pending)
        assertThat(analytics.events.last())
            .isEqualTo(AnalyticsEvent.PurchaseResult("yearly", "pending", code = null, switch = false))
    }

    @Test
    fun `a failed purchase shows the error with its code`() {
        billing.purchaseResults.add(PurchaseOutcome.Failed(BillingError.NETWORK))
        val vm = offersOpen()

        vm.purchase(activity)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
        assertThat(analytics.events.last())
            .isEqualTo(AnalyticsEvent.PurchaseResult("yearly", "error", code = "network", switch = false))
    }

    @Test
    fun `working while a purchase runs, and a second purchase is ignored`() {
        val gate = CompletableDeferred<Unit>()
        billing.purchaseGate = gate
        billing.purchaseResults.add(PurchaseOutcome.Cancelled)
        val vm = offersOpen()

        vm.purchase(activity)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY, working = true))
        vm.purchase(activity)
        vm.onEvent(PaywallEvent.Select(ProPlan.MONTHLY))
        gate.complete(Unit)

        assertThat(billing.purchaseCalls).hasSize(1)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY))
    }

    // Switch

    @Test
    fun `switch mode offers yearly and replaces the monthly subscription`() = runTest {
        billing.entitlement.value = activeMonthly
        translation.quota.value = quota(Plan.PRO)
        billing.purchaseResults.add(PurchaseOutcome.Purchased)
        translation.syncResults.add(Plan.PRO)
        billing.offersResults.add(OffersResult.Loaded(both))
        val vm = viewModel()

        vm.open(PromptSource.PROFILE_PLAN, PaywallMode.SWITCH)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Switch(yearly))
        assertThat(analytics.events).containsExactly(AnalyticsEvent.PaywallView(PromptSource.PROFILE_PLAN, "switch"))

        vm.effects.test {
            vm.purchase(activity)
            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(UiText(R.string.pro_welcome)))
        }
        assertThat(billing.purchaseCalls).containsExactly(PurchaseCall(ProPlan.YEARLY, switchFrom = ProPlan.MONTHLY))
        assertThat(analytics.events.last())
            .isEqualTo(AnalyticsEvent.PurchaseResult("yearly", "success", code = null, switch = true))
    }

    @Test
    fun `switch mode without a yearly offer - error`() {
        billing.entitlement.value = activeMonthly
        billing.offersResults.add(OffersResult.Loaded(ProOffers(monthly = monthly, yearly = null)))
        val vm = viewModel()

        vm.open(PromptSource.PROFILE_PLAN, PaywallMode.SWITCH)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
    }

    // Restore

    @Test
    fun `a restore that finds Pro syncs and closes`() = runTest {
        billing.restoreResults.add(RestoreOutcome.Restored)
        translation.syncResults.add(Plan.PRO)
        val vm = offersOpen()

        vm.effects.test {
            vm.onEvent(PaywallEvent.Restore)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(UiText(R.string.pro_restored)))
        }
        assertThat(translation.syncCalls).isEqualTo(1)
        assertThat(analytics.events).containsExactly(AnalyticsEvent.RestoreResult("restored"))
    }

    @Test
    fun `a restore that finds nothing says so and stays`() = runTest {
        billing.restoreResults.add(RestoreOutcome.NothingToRestore)
        val vm = offersOpen()

        vm.effects.test {
            vm.onEvent(PaywallEvent.Restore)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Toast(UiText(R.string.pro_restore_none)))
        }
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(both, selected = ProPlan.YEARLY))
        assertThat(analytics.events).containsExactly(AnalyticsEvent.RestoreResult("none"))
        assertThat(translation.syncCalls).isEqualTo(0)
    }

    @Test
    fun `a failed restore shows the error`() {
        billing.restoreResults.add(RestoreOutcome.Failed(BillingError.STORE))
        val vm = offersOpen()

        vm.onEvent(PaywallEvent.Restore)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Error)
        assertThat(analytics.events).containsExactly(AnalyticsEvent.RestoreResult("error"))
    }

    // Already Pro

    @Test
    fun `the store says Pro but the server says free - already Pro, sync, close`() = runTest {
        billing.entitlement.value = activeMonthly
        val gate = CompletableDeferred<Unit>()
        translation.syncGate = gate
        translation.syncResults.add(Plan.PRO)
        val vm = viewModel()

        vm.effects.test {
            vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
            assertThat(vm.sheet()).isEqualTo(ProSheetUi.AlreadyPro)
            gate.complete(Unit)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(null))
        }
        assertThat(translation.syncCalls).isEqualTo(1)
        assertThat(billing.offersCalls).isEqualTo(0)
    }

    @Test
    fun `the server already says Pro - the purchase paywall is never shown`() = runTest {
        translation.quota.value = quota(Plan.PRO)
        translation.syncResults.add(Plan.PRO)
        val vm = viewModel()

        vm.effects.test {
            vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(null))
        }
        assertThat(billing.offersCalls).isEqualTo(0)
    }

    // Account changes while open

    @Test
    fun `the account becomes a guest while offers show - sign in`() {
        val vm = offersOpen()

        auth.account.value = guest

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.NeedsSignIn)
    }

    @Test
    fun `another Google account while offers show - offers reload`() {
        val vm = offersOpen()
        billing.offersResults.add(OffersResult.Loaded(ProOffers(monthly = monthly, yearly = null)))

        auth.account.value = google.copy(uid = "u2")

        assertThat(billing.offersCalls).isEqualTo(2)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.Offers(ProOffers(monthly, null), selected = ProPlan.MONTHLY))
        assertThat(analytics.events).isEmpty()
    }

    @Test
    fun `a purchase that ends after the account changed does not touch the new sheet`() {
        val gate = CompletableDeferred<Unit>()
        billing.purchaseGate = gate
        billing.purchaseResults.add(PurchaseOutcome.Failed(BillingError.NETWORK))
        val vm = offersOpen()
        vm.purchase(activity)

        auth.account.value = guest
        gate.complete(Unit)

        assertThat(vm.sheet()).isEqualTo(ProSheetUi.NeedsSignIn)
        assertThat(analytics.events.last())
            .isEqualTo(AnalyticsEvent.PurchaseResult("yearly", "error", code = "network", switch = false))
    }

    // Reopening and closing

    @Test
    fun `opening again with the same source and mode keeps a purchase in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        billing.purchaseGate = gate
        billing.purchaseResults.add(PurchaseOutcome.Purchased)
        translation.syncResults.add(Plan.PRO)
        val vm = offersOpen()
        billing.offersResults.add(OffersResult.Loaded(both))

        vm.effects.test {
            vm.purchase(activity)
            // The activity was recreated while Play's purchase sheet was up.
            vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
            assertThat((vm.sheet() as ProSheetUi.Offers).working).isTrue()
            gate.complete(Unit)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(UiText(R.string.pro_welcome)))
        }
        assertThat(billing.offersCalls).isEqualTo(1)
        assertThat(translation.syncCalls).isEqualTo(1)
    }

    @Test
    fun `a purchase that ends after the sheet started over still syncs and welcomes the buyer`() = runTest {
        val gate = CompletableDeferred<Unit>()
        billing.purchaseGate = gate
        billing.purchaseResults.add(PurchaseOutcome.Purchased)
        translation.syncResults.add(Plan.PRO)
        val vm = offersOpen()
        billing.offersResults.add(OffersResult.Loaded(both))

        vm.effects.test {
            vm.purchase(activity)
            vm.close()
            vm.open(PromptSource.PROFILE_PLAN, PaywallMode.PURCHASE)
            gate.complete(Unit)

            assertThat(awaitItem()).isEqualTo(PaywallEffect.Close(UiText(R.string.pro_welcome)))
        }
        assertThat(translation.syncCalls).isEqualTo(1)
    }

    @Test
    fun `a purchase that ends after a sign-out syncs, but welcomes nobody`() = runTest {
        val gate = CompletableDeferred<Unit>()
        billing.purchaseGate = gate
        billing.purchaseResults.add(PurchaseOutcome.Purchased)
        translation.syncResults.add(null)
        val vm = offersOpen()

        vm.effects.test {
            vm.purchase(activity)
            auth.account.value = guest
            gate.complete(Unit)

            expectNoEvents()
        }
        assertThat(translation.syncCalls).isEqualTo(1)
        assertThat(vm.sheet()).isEqualTo(ProSheetUi.NeedsSignIn)
    }

    @Test
    fun `opening again while open logs nothing more`() {
        translation.availability.value = translation.availability.value!!.copy(billingEnabled = false)
        val vm = viewModel()
        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
        analytics.events.clear()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(analytics.events).isEmpty()
    }

    @Test
    fun `a closed sheet stops following the account`() {
        translation.availability.value = translation.availability.value!!.copy(billingEnabled = false)
        val vm = viewModel()
        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
        vm.close()
        analytics.events.clear()

        auth.account.value = guest
        auth.account.value = google.copy(uid = "u2")

        assertThat(analytics.events).isEmpty()
    }

    @Test
    fun `opening after a close starts over`() {
        translation.availability.value = translation.availability.value!!.copy(billingEnabled = false)
        val vm = viewModel()
        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)
        vm.close()
        analytics.events.clear()

        vm.open(PromptSource.TRANSLATE_LIMIT, PaywallMode.PURCHASE)

        assertThat(analytics.events).contains(AnalyticsEvent.ProInterest(PromptSource.TRANSLATE_LIMIT))
    }

    // Table

    @Test
    fun `the table uses the status limits`() {
        translation.availability.value =
            translation.availability.value!!.copy(proLimit = 250, proMaxChars = 600)
        val vm = offersOpen()

        assertThat(vm.uiState.value.table).isEqualTo(ProTable(freePerDay = 10, freeChars = 100, proLimit = 250, proChars = 600))
    }

    @Test
    fun `the table falls back without status limits`() {
        translation.availability.value = null
        translation.quota.value = null
        val vm = viewModel()

        assertThat(vm.uiState.value.table).isEqualTo(ProTable(freePerDay = 10, freeChars = 100, proLimit = 300, proChars = 500))
    }
}
