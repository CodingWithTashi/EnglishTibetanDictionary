package com.kharagedition.tibetandictionary.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.billing.BillingError
import com.kharagedition.tibetandictionary.data.billing.BillingRepository
import com.kharagedition.tibetandictionary.data.billing.OffersResult
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.data.billing.PurchaseOutcome
import com.kharagedition.tibetandictionary.data.billing.RestoreOutcome
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import com.kharagedition.tibetandictionary.ui.model.SignInAction
import com.kharagedition.tibetandictionary.ui.model.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Pro sheet wherever it opens. [open] picks what it shows: the fake door while billing is off or this build can't
 * sell, sign-in for a guest, "already Pro" when the store has Pro for this user, else the offers (or, to switch,
 * yearly alone). It follows the account while open: a sign-in, sign-out or other Google user starts over. Every
 * store call first makes the store act for the signed-in Google user, so nothing is bought for another store user.
 *
 * The server decides who is Pro; after a purchase or restore it is asked to re-read RevenueCat whatever the sheet did
 * meanwhile, and the sheet closes even if that fails, since the reconciler and the webhook catch up.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val auth: AuthRepository,
    private val translation: TranslationRepository,
    private val signInAction: SignInAction,
    private val analytics: Analytics,
) : ViewModel() {

    private val sheet = MutableStateFlow<ProSheetUi>(ProSheetUi.Loading)

    val uiState: StateFlow<PaywallUiState> =
        combine(sheet, translation.availability, translation.quota) { sheet, availability, quota ->
            PaywallUiState(sheet, tableOf(availability, quota))
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PaywallUiState(sheet.value, tableOf(translation.availability.value, translation.quota.value)),
        )

    private val effectChannel = Channel<PaywallEffect>(Channel.BUFFERED)
    val effects: Flow<PaywallEffect> = effectChannel.receiveAsFlow()

    private var source = PromptSource.TRANSLATE_LIMIT
    private var mode = PaywallMode.PURCHASE
    /** The `paywall_view` modes logged since [open]: each once, so a reload doesn't count a second view. */
    private val viewsLogged = mutableSetOf<String>()

    /** Bumped whenever the sheet starts over; a call that ends after that leaves the new sheet alone. */
    private var generation = 0
    private var loadJob: Job? = null
    private var accountJob: Job? = null
    private var signingIn = false
    private var isOpen = false

    /** Opens the sheet; the same [source] and [mode] again while open (a recreated activity) changes nothing. */
    fun open(source: PromptSource, mode: PaywallMode) {
        if (isOpen && source == this.source && mode == this.mode) return
        isOpen = true
        this.source = source
        this.mode = mode
        viewsLogged.clear()
        resolve()
        accountJob?.cancel()
        accountJob = auth.account.map(::googleUid).distinctUntilChanged().drop(1)
            .onEach { resolve() }
            .launchIn(viewModelScope)
    }

    /** The sheet closed: it stops following the account, and what was loading leaves it alone. */
    fun close() {
        if (!isOpen) return
        isOpen = false
        accountJob?.cancel()
        accountJob = null
        loadJob?.cancel()
        generation++
    }

    fun onEvent(event: PaywallEvent) {
        when (event) {
            is PaywallEvent.Select -> (sheet.value as? ProSheetUi.Offers)
                ?.takeIf { !it.working && it.offerFor(event.plan) != null }
                ?.let { sheet.value = it.copy(selected = event.plan) }
            PaywallEvent.Retry -> if (sheet.value == ProSheetUi.Error) resolve()
            PaywallEvent.Restore -> restore()
        }
    }

    fun purchase(activity: Activity) {
        val uid = googleUid(auth.account.value) ?: return
        val current = sheet.value
        val (plan, switchFrom) = when {
            current is ProSheetUi.Offers && !current.working -> current.selected to null
            current is ProSheetUi.Switch && !current.working -> ProPlan.YEARLY to ProPlan.MONTHLY
            else -> return
        }
        sheet.value = current.working()
        analytics.log(AnalyticsEvent.PurchaseStart(plan.code, source))
        val started = generation
        viewModelScope.launch {
            val outcome = if (billing.identify(uid)) {
                billing.purchase(activity, plan, switchFrom)
            } else {
                PurchaseOutcome.Failed(BillingError.NETWORK)
            }
            analytics.log(purchaseResult(plan, outcome, switch = switchFrom != null))
            when {
                outcome == PurchaseOutcome.Purchased -> syncAndWelcome(uid, UiText(R.string.pro_welcome))
                started != generation -> Unit
                outcome == PurchaseOutcome.Cancelled -> sheet.value = current
                outcome == PurchaseOutcome.Pending -> sheet.value = ProSheetUi.Pending
                else -> sheet.value = ProSheetUi.Error
            }
        }
    }

    fun signIn(activity: Activity) {
        if (sheet.value != ProSheetUi.NeedsSignIn || signingIn) return
        signingIn = true
        viewModelScope.launch {
            try {
                // On success the account changes, and following it loads the offers.
                signInAction.run(activity, source)?.let { effectChannel.send(PaywallEffect.Toast(it)) }
            } finally {
                signingIn = false
            }
        }
    }

    private fun restore() {
        val uid = googleUid(auth.account.value) ?: return
        val current = sheet.value as? ProSheetUi.Offers ?: return
        if (current.working) return
        sheet.value = current.copy(working = true)
        val started = generation
        viewModelScope.launch {
            val outcome = if (billing.identify(uid)) billing.restore() else RestoreOutcome.Failed(BillingError.NETWORK)
            analytics.log(AnalyticsEvent.RestoreResult(restoreCode(outcome)))
            when {
                outcome == RestoreOutcome.Restored -> syncAndWelcome(uid, UiText(R.string.pro_restored))
                started != generation -> Unit
                outcome == RestoreOutcome.NothingToRestore -> {
                    sheet.value = current
                    effectChannel.send(PaywallEffect.Toast(UiText(R.string.pro_restore_none)))
                }
                else -> sheet.value = ProSheetUi.Error
            }
        }
    }

    /** Starts the sheet over for the current account, switch and store state. */
    private fun resolve() {
        generation++
        loadJob?.cancel()
        val account = auth.account.value
        when {
            translation.availability.value?.billingEnabled != true || !billing.available -> {
                sheet.value = ProSheetUi.FakeDoor
                analytics.log(AnalyticsEvent.ProInterest(source))
                logView(FAKE_DOOR)
            }
            account == null || account.isGuest -> {
                sheet.value = ProSheetUi.NeedsSignIn
                logView(SIGN_IN)
            }
            mode == PaywallMode.PURCHASE && alreadyPro() -> {
                sheet.value = ProSheetUi.AlreadyPro
                val started = generation
                loadJob = viewModelScope.launch { syncAndClose(started) }
            }
            else -> {
                sheet.value = ProSheetUi.Loading
                val started = generation
                loadJob = viewModelScope.launch { load(started, account.uid) }
            }
        }
    }

    /** A Pro user never sees the purchase paywall, so nobody pays twice. */
    private fun alreadyPro(): Boolean =
        billing.entitlement.value?.active == true || translation.quota.value?.quota?.plan == Plan.PRO

    private suspend fun load(started: Int, uid: String) {
        // As this user first: the store then knows whether they already have Pro (a sign-in from this sheet).
        if (!billing.identify(uid)) {
            if (started == generation) sheet.value = ProSheetUi.Error
            return
        }
        if (started != generation) return
        if (mode == PaywallMode.PURCHASE && alreadyPro()) {
            sheet.value = ProSheetUi.AlreadyPro
            return syncAndClose(started)
        }
        val result = billing.offers()
        if (started != generation) return
        sheet.value = when (result) {
            is OffersResult.Failed -> ProSheetUi.Error
            is OffersResult.Loaded -> {
                val offers = result.offers
                when {
                    mode == PaywallMode.SWITCH -> offers.yearly?.let { ProSheetUi.Switch(it) }
                    offers.yearly != null -> ProSheetUi.Offers(offers, ProPlan.YEARLY)
                    offers.monthly != null -> ProSheetUi.Offers(offers, ProPlan.MONTHLY)
                    else -> null
                } ?: ProSheetUi.Error
            }
        }
        if (sheet.value != ProSheetUi.Error) logView(mode.code)
    }

    /** Already Pro: the server re-reads RevenueCat, then the sheet closes unless it started over meanwhile. */
    private suspend fun syncAndClose(started: Int) {
        translation.syncSubscription()
        if (started == generation) effectChannel.send(PaywallEffect.Close(null))
    }

    /** Paid or restored: the server re-reads RevenueCat, and the sheet closes with [toast] while [uid] is signed in. */
    private suspend fun syncAndWelcome(uid: String, toast: UiText) {
        translation.syncSubscription()
        if (googleUid(auth.account.value) == uid) effectChannel.send(PaywallEffect.Close(toast))
    }

    private fun logView(mode: String) {
        if (viewsLogged.add(mode)) analytics.log(AnalyticsEvent.PaywallView(source, mode))
    }

    private companion object {
        const val FAKE_DOOR = "fake_door"
        const val SIGN_IN = "sign_in"

        fun googleUid(account: Account?): String? = account?.takeUnless { it.isGuest }?.uid

        fun ProSheetUi.Offers.offerFor(plan: ProPlan) = if (plan == ProPlan.YEARLY) offers.yearly else offers.monthly

        fun ProSheetUi.working(): ProSheetUi = when (this) {
            is ProSheetUi.Offers -> copy(working = true)
            is ProSheetUi.Switch -> copy(working = true)
            else -> this
        }

        fun purchaseResult(plan: ProPlan, outcome: PurchaseOutcome, switch: Boolean) = AnalyticsEvent.PurchaseResult(
            plan = plan.code,
            outcome = when (outcome) {
                PurchaseOutcome.Purchased -> "success"
                PurchaseOutcome.Cancelled -> "cancelled"
                PurchaseOutcome.Pending -> "pending"
                is PurchaseOutcome.Failed -> "error"
            },
            code = (outcome as? PurchaseOutcome.Failed)?.error?.code,
            switch = switch,
        )

        fun restoreCode(outcome: RestoreOutcome) = when (outcome) {
            RestoreOutcome.Restored -> "restored"
            RestoreOutcome.NothingToRestore -> "none"
            is RestoreOutcome.Failed -> "error"
        }

        /** Free's numbers from the quota of a guest or free user; Pro's from status; the fallbacks otherwise. */
        fun tableOf(availability: TranslationAvailability?, quota: QuotaState?): ProTable {
            val fallback = ProTable.FALLBACK
            val freeQuota = quota?.quota?.takeIf { it.plan != Plan.PRO }
            return ProTable(
                freePerDay = quota?.freeLimit ?: fallback.freePerDay,
                freeChars = freeQuota?.maxChars ?: fallback.freeChars,
                proLimit = availability?.proLimit ?: fallback.proLimit,
                proChars = availability?.proMaxChars ?: fallback.proChars,
            )
        }
    }
}
