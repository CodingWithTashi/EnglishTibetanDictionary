package com.kharagedition.tibetandictionary.data.billing

import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the store's user the app's Google user, so a purchase lands on the account the server checks. A guest or
 * nobody is anonymous: a guest can't buy, and the anonymous id is never sent to the server.
 */
@Singleton
class BillingIdentitySync @Inject constructor(
    private val billing: BillingRepository,
    private val auth: AuthRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    @Synchronized
    fun start() {
        if (started || !billing.available) return
        started = true
        scope.launch {
            auth.account
                .map { account -> account?.takeUnless { it.isGuest }?.uid }
                .distinctUntilChanged()
                .collect { billing.identify(it) }
        }
    }
}
