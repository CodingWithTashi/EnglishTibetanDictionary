package com.kharagedition.tibetandictionary.data.billing

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.Date
import org.junit.Test

private val EXPIRES: Date = Date.from(Instant.parse("2026-11-23T09:00:00Z"))

class RevenueCatMappingTest {

    @Test
    fun `an active monthly entitlement`() {
        assertThat(entitlementOf(true, "dictionary_pro", EXPIRES, willRenew = true, billingIssueAt = null)).isEqualTo(
            ProEntitlement(
                active = true,
                plan = ProPlan.MONTHLY,
                expiresAt = Instant.parse("2026-11-23T09:00:00Z"),
                willRenew = true,
                billingIssue = false,
            ),
        )
    }

    @Test
    fun `a yearly entitlement keyed with its base plan`() {
        val entitlement = entitlementOf(true, "dictionary_pro_annually:yearly", EXPIRES, true, null)
        assertThat(entitlement.plan).isEqualTo(ProPlan.YEARLY)
    }

    @Test
    fun `a billing issue date is a billing issue`() {
        val entitlement = entitlementOf(true, "dictionary_pro", EXPIRES, willRenew = true, billingIssueAt = EXPIRES)
        assertThat(entitlement.billingIssue).isTrue()
    }

    @Test
    fun `an unknown product has no plan and no expiry keeps null`() {
        val entitlement = entitlementOf(false, "lifetime_x", null, willRenew = false, billingIssueAt = null)
        assertThat(entitlement).isEqualTo(ProEntitlement(false, null, null, willRenew = false, billingIssue = false))
    }

    @Test
    fun `purchase failures map to outcomes`() {
        assertThat(failedPurchaseOutcome("PurchaseCancelledError", userCancelled = false))
            .isEqualTo(PurchaseOutcome.Cancelled)
        assertThat(failedPurchaseOutcome("PaymentPendingError", userCancelled = false))
            .isEqualTo(PurchaseOutcome.Pending)
        assertThat(failedPurchaseOutcome("NetworkError", userCancelled = false))
            .isEqualTo(PurchaseOutcome.Failed(BillingError.NETWORK))
        assertThat(failedPurchaseOutcome("StoreProblemError", userCancelled = true))
            .isEqualTo(PurchaseOutcome.Cancelled)
    }

    @Test
    fun `error codes map to billing errors`() {
        mapOf(
            "NetworkError" to BillingError.NETWORK,
            "StoreProblemError" to BillingError.STORE,
            "PurchaseNotAllowedError" to BillingError.NOT_ALLOWED,
            "PurchaseInvalidError" to BillingError.NOT_ALLOWED,
            "InsufficientPermissionsError" to BillingError.NOT_ALLOWED,
            "ConfigurationError" to BillingError.UNAVAILABLE,
            "ProductNotAvailableForPurchaseError" to BillingError.UNAVAILABLE,
            "InvalidCredentialsError" to BillingError.UNAVAILABLE,
            "UnsupportedError" to BillingError.UNAVAILABLE,
            "UnknownError" to BillingError.UNKNOWN,
            "SomethingNewError" to BillingError.UNKNOWN,
        ).forEach { (code, error) -> assertThat(billingErrorOf(code)).isEqualTo(error) }
    }
}
