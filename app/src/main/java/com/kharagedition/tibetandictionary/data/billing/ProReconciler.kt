package com.kharagedition.tibetandictionary.data.billing

import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Repairs a missed webhook: when the store says this Google user has Pro but the server still says free, makes sure
 * the store acts for this user (a failed sign-in can leave a purchase on another store user) and asks the server to
 * re-read RevenueCat. At most once per [RETRY_AFTER], so a store and server that disagree for longer (a refund the
 * SDK hasn't seen yet) cost one call every few minutes, not one per screen.
 */
@Singleton
class ProReconciler @Inject constructor(
    private val billing: BillingRepository,
    private val auth: AuthRepository,
    private val translation: TranslationRepository,
    private val clock: AppClock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false
    private var lastSync: Instant? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(billing.entitlement, auth.account, translation.quota) { entitlement, account, quota ->
                account?.takeIf { entitlement?.active == true && !it.isGuest && quota?.quota?.plan == Plan.FREE }?.uid
            }.collect { uid -> if (uid != null) repairUnlessRecent(uid) }
        }
    }

    private suspend fun repairUnlessRecent(uid: String) {
        val now = clock.now()
        val last = lastSync
        if (last != null && Duration.between(last, now) < RETRY_AFTER) return
        lastSync = now
        if (billing.identify(uid)) translation.syncSubscription()
    }

    private companion object {
        val RETRY_AFTER: Duration = Duration.ofMinutes(5)
    }
}
