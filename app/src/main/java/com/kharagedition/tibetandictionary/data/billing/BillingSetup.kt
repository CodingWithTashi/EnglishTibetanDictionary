package com.kharagedition.tibetandictionary.data.billing

import android.content.Context
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import javax.inject.Inject
import javax.inject.Singleton

/** RevenueCat's static SDK, apart so [BillingSetup] can be tested without it. */
internal interface StoreSdk {
    val isConfigured: Boolean

    fun configure(context: Context, key: String, appUserId: String)
}

/**
 * Sets RevenueCat up for selling, once per process. It never runs anonymous: an anonymous SDK posts the Play
 * purchases it finds on the device, and RevenueCat's Transfer would move a subscription off its Google account (a
 * fresh install opened as a guest). So [configure], called from `DictionaryApplication` where Firebase is set up,
 * only records that this build can sell; RevenueCat starts with the first Google user ([configureAs]). A build
 * without `REVENUECAT_ANDROID_KEY` can't sell, which keeps the paywall a fake door. Never throws.
 */
@Singleton
class BillingSetup internal constructor(
    private val errors: ErrorReporter,
    private val key: String,
    private val sdk: StoreSdk,
) {
    @Inject constructor(errors: ErrorReporter) : this(errors, BuildConfig.REVENUECAT_ANDROID_KEY, RevenueCatSdk)

    @Volatile private var context: Context? = null

    /** This build has a key and [configure] has run, so a Google user can buy. */
    val canSell: Boolean get() = context != null

    fun configure(context: Context) {
        if (key.isNotBlank()) this.context = context.applicationContext ?: context
    }

    /** Starts RevenueCat as [uid] unless it already runs; false when it can't. */
    @Synchronized
    fun configureAs(uid: String): Boolean {
        if (sdk.isConfigured) return true
        val context = context ?: return false
        return try {
            sdk.configure(context, key, uid)
            true
        } catch (e: Exception) {
            errors.record(e)
            false
        }
    }
}

private object RevenueCatSdk : StoreSdk {
    override val isConfigured: Boolean get() = Purchases.isConfigured

    override fun configure(context: Context, key: String, appUserId: String) {
        Purchases.configure(
            PurchasesConfiguration.Builder(context, key)
                .appUserID(appUserId)
                // No advertising or device ids: RevenueCat only needs the app account and the purchases.
                .automaticDeviceIdentifierCollectionEnabled(false)
                .build(),
        )
    }
}
