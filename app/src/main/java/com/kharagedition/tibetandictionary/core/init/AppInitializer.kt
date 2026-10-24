package com.kharagedition.tibetandictionary.core.init

import android.util.Log
import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.AppLocale
import com.kharagedition.tibetandictionary.data.billing.BillingIdentitySync
import com.kharagedition.tibetandictionary.data.billing.ProReconciler
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Start-up work, run once per process off the main thread: legacy cleanup, the pack choice of an onboarded user, then the dictionary install, then
 * the store identity sync and the Pro reconciler, then [ready]. A failing step is reported and skipped so the app always gets past the splash. Install failures show on
 * the installing screen through the installer's state; an installer that throws before reporting anything is
 * still at `Checking` once [ready] is set, which the root view model shows as a failure with retry.
 */
@Singleton
class AppInitializer @Inject constructor(
    private val legacyCleanup: LegacyCleanup,
    private val installer: DictionaryInstaller,
    private val prefs: UserPreferencesRepository,
    private val billingIdentity: BillingIdentitySync,
    private val proReconciler: ProReconciler,
    private val errors: ErrorReporter,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch(io) {
            step("legacyCleanup") { legacyCleanup.runOnce() }
            step("keepTibetanPack") { keepTibetanPackForOnboardedUser() }
            step("install") { installer.ensureInstalled(prefs.snapshot().dictionaryPack) }
            // Both only launch collectors; the paywall can't open before ready.
            step("billingIdentity") { billingIdentity.start() }
            step("proReconciler") { proReconciler.start() }
            _ready.value = true
            // After ready: only keeps the stored language in step with the UI, so it must not delay start-up.
            step("syncLanguage") { syncLanguage() }
        }
    }

    /**
     * A user who finished onboarding before packs existed has no stored pack and was always on Tibetan. Store it, so
     * the SIM-based default for a fresh install can never move that user to another dictionary.
     */
    private suspend fun keepTibetanPackForOnboardedUser() {
        val current = prefs.snapshot()
        if (current.onboarded && !current.dictionaryPackChosen) prefs.setDictionaryPack(DictionaryPack.TIBETAN)
    }

    /** Copies a per-app locale chosen in Android settings into prefs, so prefs match the language on screen. */
    private suspend fun syncLanguage() {
        AppLanguage.syncTarget(AppLocale.currentTag(), prefs.snapshot().appLanguage)?.let { prefs.setAppLanguage(it) }
    }

    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Init step failed: $name", t)
            errors.record(t)
        }
    }

    private companion object {
        const val TAG = "AppInitializer"
    }
}
