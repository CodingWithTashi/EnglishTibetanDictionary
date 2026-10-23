package com.kharagedition.tibetandictionary.data.billing

import java.time.Instant

/** A Pro subscription in Google Play, by its subscription id; [code] names it in analytics. */
enum class ProPlan(val productId: String, val code: String) {
    MONTHLY("dictionary_pro", "monthly"),
    YEARLY("dictionary_pro_annually", "yearly"),
    ;

    companion object {
        /** The plan for a Play id such as `dictionary_pro` or `dictionary_pro:monthly`, matched exactly before `:`. */
        fun fromProductId(id: String?): ProPlan? {
            val subscriptionId = id?.substringBefore(':') ?: return null
            return entries.firstOrNull { it.productId == subscriptionId }
        }
    }
}

/** A plan as Google Play sells it to this user: [price] and [pricePerMonth] are Play's formatted strings. */
data class ProOffer(val plan: ProPlan, val price: String, val pricePerMonth: String?)

/** The plans on offer; either can be missing when Play doesn't return it. */
data class ProOffers(val monthly: ProOffer?, val yearly: ProOffer?)

/** The `pro` entitlement as the store SDK last reported it. [plan] is null for a product this app doesn't know. */
data class ProEntitlement(
    val active: Boolean,
    val plan: ProPlan?,
    val expiresAt: Instant?,
    val willRenew: Boolean,
    val billingIssue: Boolean,
)

/** Why a billing call failed; [code] is its analytics name. */
enum class BillingError(val code: String) {
    NETWORK("network"),
    STORE("store"),
    NOT_ALLOWED("not_allowed"),
    UNAVAILABLE("unavailable"),
    UNKNOWN("unknown"),
}

sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object Cancelled : PurchaseOutcome

    /** Play is waiting for the payment (cash, some cards); Pro starts when it confirms. */
    data object Pending : PurchaseOutcome
    data class Failed(val error: BillingError) : PurchaseOutcome
}

sealed interface OffersResult {
    data class Loaded(val offers: ProOffers) : OffersResult
    data class Failed(val error: BillingError) : OffersResult
}

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    data object NothingToRestore : RestoreOutcome
    data class Failed(val error: BillingError) : RestoreOutcome
}

private const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"
private const val PACKAGE = "com.kharagedition.tibetandictionary"

/** Google Play's page for [plan]'s subscription, or this app's subscriptions when the plan isn't known. */
fun manageSubscriptionUrl(plan: ProPlan?): String = when (plan) {
    null -> "$PLAY_SUBSCRIPTIONS?package=$PACKAGE"
    else -> "$PLAY_SUBSCRIPTIONS?sku=${plan.productId}&package=$PACKAGE"
}
