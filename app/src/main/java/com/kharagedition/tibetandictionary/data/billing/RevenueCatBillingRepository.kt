package com.kharagedition.tibetandictionary.data.billing

import android.app.Activity
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreReplacementMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [BillingRepository] over RevenueCat, the only class besides [BillingSetup] that touches its SDK. Available once
 * [BillingSetup] says this build can sell; the SDK itself starts when [identify] first names a Google user. Expected
 * store errors become [BillingError]s; configuration and unknown errors are also recorded. Nothing recorded carries a
 * price or an id.
 */
@Singleton
class RevenueCatBillingRepository @Inject constructor(
    private val errors: ErrorReporter,
    private val setup: BillingSetup,
) : BillingRepository {

    private val _entitlement = MutableStateFlow<ProEntitlement?>(null)
    override val entitlement: StateFlow<ProEntitlement?> = _entitlement.asStateFlow()

    override val available: Boolean get() = setup.canSell

    /** The packages of the last offerings read, to buy from. */
    @Volatile private var packages: Map<ProPlan, Package> = emptyMap()

    @Volatile private var listening = false

    override suspend fun offers(): OffersResult {
        val purchases = purchases() ?: return OffersResult.Failed(BillingError.UNAVAILABLE)
        return try {
            val loaded = loadPackages(purchases)
            if (loaded.isEmpty()) {
                errors.record(IllegalStateException("no Pro packages in the current or default offering"))
                OffersResult.Failed(BillingError.UNAVAILABLE)
            } else {
                OffersResult.Loaded(
                    ProOffers(
                        monthly = loaded[ProPlan.MONTHLY]?.let { offerOf(ProPlan.MONTHLY, it) },
                        yearly = loaded[ProPlan.YEARLY]?.let { offerOf(ProPlan.YEARLY, it) },
                    ),
                )
            }
        } catch (e: PurchasesException) {
            OffersResult.Failed(errorOf(e))
        }
    }

    override suspend fun purchase(activity: Activity, plan: ProPlan, switchFrom: ProPlan?): PurchaseOutcome {
        val purchases = purchases() ?: return PurchaseOutcome.Failed(BillingError.UNAVAILABLE)
        return try {
            val pkg = packages[plan] ?: loadPackages(purchases)[plan]
                ?: return PurchaseOutcome.Failed(BillingError.UNAVAILABLE)
            val params = PurchaseParams.Builder(activity, pkg).apply {
                if (switchFrom != null) {
                    // Google Play's subscription id, without the base plan; the unused time is credited.
                    oldProductId(switchFrom.productId)
                    replacementMode(StoreReplacementMode.WITH_TIME_PRORATION)
                }
            }.build()
            publish(purchases.awaitPurchase(params).customerInfo)
            PurchaseOutcome.Purchased
        } catch (e: PurchasesTransactionException) {
            if (e.code == PurchasesErrorCode.ProductAlreadyPurchasedError) return purchasedByRestore()
            failedPurchaseOutcome(e.code.name, e.userCancelled).also {
                if (it is PurchaseOutcome.Failed) errorOf(e)
            }
        } catch (e: PurchasesException) {
            PurchaseOutcome.Failed(errorOf(e))
        }
    }

    override suspend fun restore(): RestoreOutcome {
        val purchases = purchases() ?: return RestoreOutcome.Failed(BillingError.UNAVAILABLE)
        return try {
            val info = purchases.awaitRestore()
            publish(info)
            if (info.entitlements[ENTITLEMENT]?.isActive == true) {
                RestoreOutcome.Restored
            } else {
                RestoreOutcome.NothingToRestore
            }
        } catch (e: PurchasesException) {
            RestoreOutcome.Failed(errorOf(e))
        }
    }

    override suspend fun identify(uid: String?): Boolean {
        if (uid != null && !setup.configureAs(uid)) return false
        // Not started yet and nobody to act for: nothing to log out of.
        val purchases = purchases() ?: return uid == null
        return try {
            when {
                uid != null && purchases.appUserID != uid -> publish(purchases.awaitLogIn(uid).customerInfo)
                // Logging out an anonymous user is an error in RevenueCat; there is nothing to log out of.
                uid == null && !purchases.isAnonymous -> publish(purchases.awaitLogOut())
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: PurchasesException) {
            errorOf(e)
            false
        } catch (e: Exception) {
            errors.record(e)
            false
        }
    }

    /** A purchase Play says this account already owns: restore it, so it lands on this app account. */
    private suspend fun purchasedByRestore(): PurchaseOutcome = when (val restored = restore()) {
        RestoreOutcome.Restored -> PurchaseOutcome.Purchased
        RestoreOutcome.NothingToRestore -> PurchaseOutcome.Failed(BillingError.UNKNOWN)
        is RestoreOutcome.Failed -> PurchaseOutcome.Failed(restored.error)
    }

    /** The configured SDK, listening for customer updates from the first use on; null when unavailable. */
    private fun purchases(): Purchases? {
        if (!Purchases.isConfigured) return null
        val purchases = Purchases.sharedInstance
        if (!listening) {
            synchronized(this) {
                if (!listening) {
                    purchases.updatedCustomerInfoListener = UpdatedCustomerInfoListener(::publish)
                    listening = true
                }
            }
        }
        return purchases
    }

    private suspend fun loadPackages(purchases: Purchases): Map<ProPlan, Package> {
        val offerings = purchases.awaitOfferings()
        val offering = offerings.current ?: offerings[OFFERING]
        val loaded = buildMap {
            offering?.monthly?.let { put(ProPlan.MONTHLY, it) }
            offering?.annual?.let { put(ProPlan.YEARLY, it) }
        }
        packages = loaded
        return loaded
    }

    private fun offerOf(plan: ProPlan, pkg: Package): ProOffer = ProOffer(
        plan = plan,
        price = pkg.product.price.formatted,
        pricePerMonth = if (plan == ProPlan.YEARLY) pkg.product.pricePerMonth()?.formatted else null,
    )

    private fun publish(info: CustomerInfo) {
        val pro = info.entitlements[ENTITLEMENT]
        _entitlement.value = if (pro == null) {
            NO_ENTITLEMENT
        } else {
            entitlementOf(pro.isActive, pro.productIdentifier, pro.expirationDate, pro.willRenew, pro.billingIssueDetectedAt)
        }
    }

    /** The error's [BillingError]; a misconfiguration or an unknown error is recorded too. */
    private fun errorOf(e: PurchasesException): BillingError = billingErrorOf(e.code.name).also {
        if (it == BillingError.UNAVAILABLE || it == BillingError.UNKNOWN) errors.record(e)
    }

    private companion object {
        const val ENTITLEMENT = "pro"
        const val OFFERING = "default"
        val NO_ENTITLEMENT = ProEntitlement(active = false, plan = null, expiresAt = null, willRenew = false, billingIssue = false)
    }
}
