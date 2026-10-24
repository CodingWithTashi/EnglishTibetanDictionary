package com.kharagedition.tibetandictionary

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kharagedition.tibetandictionary.core.appcheck.appCheckProviderFactory
import com.kharagedition.tibetandictionary.core.init.AppInitializer
import com.kharagedition.tibetandictionary.core.init.StoredAppearance
import com.kharagedition.tibetandictionary.data.billing.BillingSetup
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DictionaryApplication : Application() {

    @Inject lateinit var appInitializer: AppInitializer

    @Inject lateinit var storedAppearance: StoredAppearance

    // Lazy: built only where Firebase is set up.
    @Inject lateinit var billingSetup: Lazy<BillingSetup>

    override fun onCreate() {
        super.onCreate()
        configureFirebase()
        // Before any activity exists, so a cold start never recreates the activity to change the theme.
        storedAppearance.applyBlocking()
        appInitializer.start()
    }

    /**
     * Crash and analytics collection run in release builds only. App Check attests every translation call. RevenueCat
     * is configured here, before any screen can open the paywall.
     */
    private fun configureFirebase() {
        // FirebaseInitProvider creates the default app; it is missing where google-services is not set up
        // (Robolectric tests), and Firebase must never stop the app from starting.
        if (FirebaseApp.getApps(this).isEmpty()) return
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
        }
        // Apart from the block above, so a Firebase failure there doesn't leave Pro unsellable.
        runCatching { billingSetup.get().configure(this) }
    }
}
