package com.kharagedition.tibetandictionary.fakes

import android.app.Activity
import com.kharagedition.tibetandictionary.data.billing.BillingRepository
import com.kharagedition.tibetandictionary.data.billing.OffersResult
import com.kharagedition.tibetandictionary.data.billing.ProEntitlement
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.data.billing.PurchaseOutcome
import com.kharagedition.tibetandictionary.data.billing.RestoreOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [BillingRepository] that answers from scripted queues (a call with nothing queued fails the test) and records
 * every call. [entitlement] is settable; set [entitlementAfterPurchase] or [entitlementAfterRestore] to have that
 * call change it, as RevenueCat's listener does. [offersGate] and [purchaseGate] hold that call until the test
 * completes them. [identify] fails while [identifyFailures] is above 0; on success [storeUser] becomes the uid and
 * [entitlementAfterIdentify], if set, is published.
 */
class FakeBillingRepository(override var available: Boolean = true) : BillingRepository {
    data class PurchaseCall(val plan: ProPlan, val switchFrom: ProPlan?)

    override val entitlement = MutableStateFlow<ProEntitlement?>(null)

    val offersResults = ArrayDeque<OffersResult>()
    val purchaseResults = ArrayDeque<PurchaseOutcome>()
    val restoreResults = ArrayDeque<RestoreOutcome>()
    var entitlementAfterPurchase: ProEntitlement? = null
    var entitlementAfterRestore: ProEntitlement? = null
    var offersGate: CompletableDeferred<Unit>? = null
    var purchaseGate: CompletableDeferred<Unit>? = null

    var offersCalls = 0
    val purchaseCalls = mutableListOf<PurchaseCall>()
    var restoreCalls = 0
    val identifyCalls = mutableListOf<String?>()
    var identifyFailures = 0
    var entitlementAfterIdentify: ProEntitlement? = null
    var storeUser: String? = null

    override suspend fun offers(): OffersResult {
        offersCalls++
        offersGate?.await()
        return offersResults.removeFirstOrNull() ?: error("no offers result queued")
    }

    override suspend fun purchase(activity: Activity, plan: ProPlan, switchFrom: ProPlan?): PurchaseOutcome {
        purchaseCalls += PurchaseCall(plan, switchFrom)
        purchaseGate?.await()
        val outcome = purchaseResults.removeFirstOrNull() ?: error("no purchase result queued")
        if (outcome == PurchaseOutcome.Purchased) entitlementAfterPurchase?.let { entitlement.value = it }
        return outcome
    }

    override suspend fun restore(): RestoreOutcome {
        restoreCalls++
        val outcome = restoreResults.removeFirstOrNull() ?: error("no restore result queued")
        if (outcome == RestoreOutcome.Restored) entitlementAfterRestore?.let { entitlement.value = it }
        return outcome
    }

    override suspend fun identify(uid: String?): Boolean {
        identifyCalls += uid
        if (identifyFailures > 0) {
            identifyFailures--
            return false
        }
        storeUser = uid
        entitlementAfterIdentify?.let { entitlement.value = it }
        return true
    }
}
