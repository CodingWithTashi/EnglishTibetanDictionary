package com.kharagedition.tibetandictionary.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play subscriptions for Pro. The server, not this, decides who is Pro; this sells, restores, and says what
 * the store knows. No call throws, except for cancellation.
 */
interface BillingRepository {
    /** False in a build without a RevenueCat key (or before the SDK is set up): the paywall stays a fake door. */
    val available: Boolean

    /** The `pro` entitlement as last seen; null until the store has answered once, or when unavailable. */
    val entitlement: StateFlow<ProEntitlement?>

    suspend fun offers(): OffersResult

    /** Buys [plan]; with [switchFrom], replaces that subscription at once, crediting its unused time. */
    suspend fun purchase(activity: Activity, plan: ProPlan, switchFrom: ProPlan? = null): PurchaseOutcome

    suspend fun restore(): RestoreOutcome

    /**
     * Ties purchases to the Google user [uid] (null: a guest or nobody); true once the store acts for [uid]. Never
     * throws.
     */
    suspend fun identify(uid: String?): Boolean
}
