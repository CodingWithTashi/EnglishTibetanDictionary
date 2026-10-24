package com.kharagedition.tibetandictionary.data.billing

import java.util.Date

// RevenueCat's values mapped to this app's models. Plain values in, so tests need no SDK objects; error codes are
// PurchasesErrorCode names.

internal fun entitlementOf(
    isActive: Boolean,
    productIdentifier: String,
    expiration: Date?,
    willRenew: Boolean,
    billingIssueAt: Date?,
): ProEntitlement = ProEntitlement(
    active = isActive,
    plan = ProPlan.fromProductId(productIdentifier),
    expiresAt = expiration?.toInstant(),
    willRenew = willRenew,
    billingIssue = billingIssueAt != null,
)

/** A purchase that failed with [code]: the user backing out, a pending payment, or an error. */
internal fun failedPurchaseOutcome(code: String, userCancelled: Boolean): PurchaseOutcome = when {
    userCancelled || code == "PurchaseCancelledError" -> PurchaseOutcome.Cancelled
    code == "PaymentPendingError" -> PurchaseOutcome.Pending
    else -> PurchaseOutcome.Failed(billingErrorOf(code))
}

internal fun billingErrorOf(code: String): BillingError = when (code) {
    "NetworkError" -> BillingError.NETWORK
    "StoreProblemError" -> BillingError.STORE
    "PurchaseNotAllowedError", "PurchaseInvalidError", "InsufficientPermissionsError", "IneligibleError" ->
        BillingError.NOT_ALLOWED
    "ConfigurationError", "ProductNotAvailableForPurchaseError", "InvalidCredentialsError", "UnsupportedError" ->
        BillingError.UNAVAILABLE
    else -> BillingError.UNKNOWN
}
