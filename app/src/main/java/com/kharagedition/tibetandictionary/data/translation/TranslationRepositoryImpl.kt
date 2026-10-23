package com.kharagedition.tibetandictionary.data.translation

import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.core.di.ApplicationScope
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.domain.TranslationText
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the quota from every reply and refusal, so the UI's counts need no extra calls, and reads status only when
 * it is stale. Analytics events carry the outcome, never the text.
 */
@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val api: TranslationApi,
    private val auth: AuthRepository,
    private val analytics: Analytics,
    private val clock: AppClock,
    private val errors: ErrorReporter,
    @ApplicationScope scope: CoroutineScope,
) : TranslationRepository {

    private val _quota = MutableStateFlow<QuotaState?>(null)
    override val quota: StateFlow<QuotaState?> = _quota.asStateFlow()

    private val _availability = MutableStateFlow<TranslationAvailability?>(null)
    override val availability: StateFlow<TranslationAvailability?> = _availability.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile private var lastStatusAt: Instant? = null

    init {
        // A switch to another uid (or a sign-out) makes everything we know about the old one wrong. The first user
        // appearing is left to whoever created it: prepare() and translate() fill the state themselves.
        scope.launch {
            var previous = auth.account.value?.uid
            auth.account.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                val switched = previous != null && uid != previous
                previous = uid
                if (!switched) return@collectLatest
                _quota.value = null
                _availability.value = null
                lastStatusAt = null
                if (uid != null) refresh(force = true)
            }
        }
        // Someone still on a screen at midnight sees the new day's allowance without a call.
        scope.launch {
            _quota.map { it?.quota?.resetsAt }.distinctUntilChanged().collectLatest { resetsAt ->
                if (resetsAt == null) return@collectLatest
                while (clock.now() < resetsAt) {
                    delay(Duration.between(clock.now(), resetsAt).toMillis().coerceAtLeast(1))
                }
                _quota.update { state ->
                    if (state?.quota?.resetsAt == resetsAt) state.copy(quota = state.quota.copy(used = 0)) else state
                }
            }
        }
    }

    override suspend fun prepare() {
        if (ensureUser() != null) return
        refresh(force = false)
    }

    override suspend fun refresh(force: Boolean) {
        if (auth.account.value == null) return
        val result = refreshMutex.withLock {
            val now = clock.now()
            val current = _quota.value
            val pastReset = current != null && now >= current.quota.resetsAt
            // A new day: show the full allowance at once, before status confirms it.
            if (pastReset) _quota.value = current.copy(quota = current.quota.copy(used = 0))
            val fresh = lastStatusAt?.let { Duration.between(it, now) < STALE_AFTER } ?: false
            if (!force && fresh && !pastReset) return
            api.status(clock.zone().id).also { result ->
                if (result is ApiResult.Ok) {
                    val status = result.value
                    _quota.value = QuotaState(status.quota, status.freeLimit)
                    _availability.value = TranslationAvailability(
                        enabled = status.enabled,
                        pairs = status.pairs,
                        billingEnabled = status.billingEnabled,
                        proLimit = status.proLimit,
                        proMaxChars = status.proMaxChars,
                    )
                    lastStatusAt = now
                }
            }
        }
        // Outside the lock: signing out switches the uid, and that refreshes.
        if (result is ApiResult.Refused && result.refusal == TranslationRefusal.AccountGone) signOutGoneAccount()
    }

    override suspend fun translate(text: String, pair: LangPair, surface: TranslateSurface): TranslateResult {
        ensureUser()?.let { return finish(ApiResult.Refused(it), pair, surface) }
        return finish(api.translate(TranslationText.normalize(text), pair, clock.zone().id), pair, surface)
    }

    override suspend fun syncSubscription(): Plan? = when (val result = api.syncSubscription()) {
        is ApiResult.Ok -> {
            refresh(force = true)
            result.value
        }
        is ApiResult.Refused -> {
            if (result.refusal == TranslationRefusal.AccountGone) signOutGoneAccount()
            null
        }
    }

    /** Null once there is a user; otherwise why there can't be one. */
    private suspend fun ensureUser(): TranslationRefusal? = try {
        auth.ensureUser()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        TranslationRefusal.Offline
    } catch (e: Exception) {
        errors.record(e)
        TranslationRefusal.Unexpected
    }

    private suspend fun finish(
        result: ApiResult<TranslateReply>,
        pair: LangPair,
        surface: TranslateSurface,
    ): TranslateResult {
        val outcome = when (result) {
            is ApiResult.Ok -> {
                result.value.quota?.let { quota -> _quota.update { QuotaState(quota, it?.freeLimit) } }
                if (result.value.text.cached) "cached" else "translated"
            }
            is ApiResult.Refused -> {
                onRefusal(result.refusal)
                "refused:${result.refusal.analyticsName}"
            }
        }
        analytics.log(
            AnalyticsEvent.Translate(
                outcome = outcome,
                cached = result is ApiResult.Ok && result.value.text.cached,
                pair = pair.key,
                plan = _quota.value?.quota?.plan?.code,
                surface = surface,
            ),
        )
        return when (result) {
            is ApiResult.Ok -> TranslateResult.Translated(result.value.text)
            is ApiResult.Refused -> {
                val refusal = result.refusal
                if (refusal is TranslationRefusal.DailyLimit) {
                    analytics.log(AnalyticsEvent.QuotaLimitHit(refusal.plan.code, surface))
                }
                TranslateResult.Refused(refusal)
            }
        }
    }

    private suspend fun onRefusal(refusal: TranslationRefusal) {
        when (refusal) {
            is TranslationRefusal.DailyLimit -> _quota.update { old ->
                QuotaState(
                    Quota(
                        plan = refusal.plan,
                        used = refusal.limit,
                        limit = refusal.limit,
                        resetsAt = refusal.resetsAt,
                        maxChars = old?.quota?.maxChars ?: DEFAULT_MAX_CHARS,
                    ),
                    freeLimit = refusal.freeLimit ?: old?.freeLimit,
                )
            }
            // With no quota yet there is no maxChars to change, so read it: the counter must show the limit.
            is TranslationRefusal.TooLong ->
                if (_quota.value == null) {
                    refresh(force = true)
                } else {
                    _quota.update { it?.copy(quota = it.quota.copy(maxChars = refusal.maxChars)) }
                }
            TranslationRefusal.Disabled ->
                _availability.update {
                    it?.copy(enabled = false) ?: TranslationAvailability(enabled = false, pairs = emptySet())
                }
            TranslationRefusal.UnsupportedPair -> refresh(force = true)
            // Signing out on Unauthenticated instead would make a Google user a guest whenever App Check fails.
            TranslationRefusal.AccountGone -> signOutGoneAccount()
            else -> Unit
        }
    }

    /** The user is gone: start over as a new guest on the next call. */
    private suspend fun signOutGoneAccount() {
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.record(e)
        }
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(5)

        /** The free plan's character limit, until a status or a refusal says otherwise. */
        const val DEFAULT_MAX_CHARS = 100
    }
}

/** Spelled out rather than derived from class names, which R8 renames. */
private val TranslationRefusal.analyticsName: String
    get() = when (this) {
        is TranslationRefusal.DailyLimit -> "daily_limit"
        is TranslationRefusal.TooLong -> "too_long"
        is TranslationRefusal.RateLimited -> "rate_limited"
        TranslationRefusal.Offline -> "offline"
        TranslationRefusal.Busy -> "busy"
        TranslationRefusal.Disabled -> "disabled"
        TranslationRefusal.UnsupportedPair -> "unsupported_pair"
        TranslationRefusal.NotTibetanScript -> "not_tibetan_script"
        TranslationRefusal.WrongScript -> "wrong_script"
        TranslationRefusal.EmptyText -> "empty_text"
        TranslationRefusal.Unauthenticated -> "unauthenticated"
        TranslationRefusal.AccountGone -> "account_gone"
        TranslationRefusal.GuestAccount -> "guest_account"
        TranslationRefusal.BillingUnavailable -> "billing_unavailable"
        TranslationRefusal.Unexpected -> "unexpected"
    }
