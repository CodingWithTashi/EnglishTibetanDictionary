package com.kharagedition.tibetandictionary.data.translation

import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import kotlinx.coroutines.flow.StateFlow

/** Today's quota as last seen, with the free plan's limit for the guest's "Sign in to get N more" line. */
data class QuotaState(val quota: Quota, val freeLimit: Int?) {
    val remaining: Int get() = quota.remaining

    /** "Sign in to get N more today": freeLimit − used, for guests only. */
    val moreWhenSignedIn: Int?
        get() = if (quota.plan == Plan.GUEST) freeLimit?.minus(quota.used)?.coerceAtLeast(0) else null
}

/**
 * Whether translation is on, which directions the backend offers, and the paywall switch with Pro's limits (null
 * until a status that has them).
 */
data class TranslationAvailability(
    val enabled: Boolean,
    val pairs: Set<LangPair>,
    val billingEnabled: Boolean = false,
    val proLimit: Int? = null,
    val proMaxChars: Int? = null,
)

sealed interface TranslateResult {
    data class Translated(val text: TranslatedText) : TranslateResult
    data class Refused(val refusal: TranslationRefusal) : TranslateResult
}

interface TranslationRepository {
    val quota: StateFlow<QuotaState?>
    val availability: StateFlow<TranslationAvailability?>

    /** Creates the guest if needed, then refresh(force = false). Called when the Translate tab is shown. */
    suspend fun prepare()

    /** Re-reads status if forced, stale (> 5 min) or past resetsAt; does nothing when there is no Firebase user. */
    suspend fun refresh(force: Boolean = false)

    /** [text] raw; normalised here. */
    suspend fun translate(text: String, pair: LangPair, surface: TranslateSurface): TranslateResult

    /**
     * Asks the server to re-read the user's subscription, then forces a status refresh so the quota shows the new
     * plan. The server's plan, or null when the call was refused.
     */
    suspend fun syncSubscription(): Plan?
}
