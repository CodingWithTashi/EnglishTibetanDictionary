package com.kharagedition.tibetandictionary.data.translation

import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.Direction
import java.time.Instant

/** A language the translation backend handles, by its API code. */
enum class Lang(val code: String) {
    BO("bo"),
    DZ("dz"),
    EN("en"),
    ;

    /** Tibetan and Dzongkha are written in Tibetan script. */
    val usesTibetanScript: Boolean get() = this != EN

    companion object {
        fun fromCode(code: String): Lang? = entries.firstOrNull { it.code == code }
    }
}

/** A translation direction, keyed as the backend's `pairs` are ("bo-en"). */
data class LangPair(val source: Lang, val target: Lang) {
    val key: String get() = "${source.code}-${target.code}"

    fun swapped(): LangPair = LangPair(target, source)

    companion object {
        fun fromKey(key: String): LangPair? {
            val parts = key.split('-')
            if (parts.size != 2) return null
            val source = Lang.fromCode(parts[0]) ?: return null
            val target = Lang.fromCode(parts[1]) ?: return null
            return LangPair(source, target)
        }
    }
}

/** The directions the Translate tab offers for this pack, the default first: its language ⇄ English. */
fun DictionaryPack.translationPairs(): List<LangPair> = when (this) {
    DictionaryPack.TIBETAN -> listOf(LangPair(Lang.BO, Lang.EN), LangPair(Lang.EN, Lang.BO))
    DictionaryPack.DZONGKHA -> listOf(LangPair(Lang.DZ, Lang.EN), LangPair(Lang.EN, Lang.DZ))
}

/** The pack whose Translate tab offers this direction; null for one none offers. */
fun LangPair.pack(): DictionaryPack? = DictionaryPack.entries.firstOrNull { this in it.translationPairs() }

/** The translation for this dictionary direction; null when it is monolingual or a language isn't bo, dz or en. */
fun Direction.translationPair(): LangPair? {
    val source = Lang.fromCode(sourceLang) ?: return null
    val target = Lang.fromCode(targetLang) ?: return null
    return LangPair(source, target).takeIf { source != target }
}

/** The user's translation plan; the server decides it from the ID token. */
enum class Plan(val code: String) {
    GUEST("guest"),
    FREE("free"),
    PRO("pro"),
    ;

    companion object {
        fun fromCode(code: String?): Plan? = entries.firstOrNull { it.code == code }
    }
}

/** Who translated; Google's attribution must be shown. */
enum class Provider(val code: String) {
    AZURE("azure"),
    GOOGLE("google"),
    OTHER(""),
    ;

    companion object {
        fun fromCode(code: String?): Provider = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/** Today's usage, as of the last reply. */
data class Quota(val plan: Plan, val used: Int, val limit: Int, val resetsAt: Instant, val maxChars: Int) {
    val remaining: Int get() = (limit - used).coerceAtLeast(0)
}

data class TranslatedText(val translation: String, val cached: Boolean, val provider: Provider)

/** [quota] is null only on a cached reply whose usage the server could not read. */
data class TranslateReply(val text: TranslatedText, val quota: Quota?)

/**
 * [billingEnabled] is the remote paywall switch; [proLimit] and [proMaxChars] are Pro's daily fair-use limit and
 * characters. A backend from before billing omits all three: the switch reads off and the limits null.
 */
data class StatusReply(
    val enabled: Boolean,
    val quota: Quota,
    val freeLimit: Int,
    val pairs: Set<LangPair>,
    val billingEnabled: Boolean = false,
    val proLimit: Int? = null,
    val proMaxChars: Int? = null,
)

/** Why there is no translation; mapped from the callable's code and `details.reason` (backend/README.md). */
sealed interface TranslationRefusal {
    data class DailyLimit(val plan: Plan, val limit: Int, val resetsAt: Instant, val freeLimit: Int?) :
        TranslationRefusal

    data class TooLong(val maxChars: Int, val length: Int) : TranslationRefusal
    data class RateLimited(val retryAfterSec: Int?) : TranslationRefusal
    data object Offline : TranslationRefusal

    /** provider_unavailable or service_budget. */
    data object Busy : TranslationRefusal
    data object Disabled : TranslationRefusal
    data object UnsupportedPair : TranslationRefusal
    data object NotTibetanScript : TranslationRefusal
    data object WrongScript : TranslationRefusal
    data object EmptyText : TranslationRefusal

    /** App Check or the ID token was rejected, or no user was sent; recorded, and the user stays signed in. */
    data object Unauthenticated : TranslationRefusal

    /** The user was deleted or disabled elsewhere, so its token can't be refreshed; the app signs out locally. */
    data object AccountGone : TranslationRefusal
    data object GuestAccount : TranslationRefusal

    /** syncSubscription couldn't reach RevenueCat; the webhook still brings the server up to date. */
    data object BillingUnavailable : TranslationRefusal
    data object Unexpected : TranslationRefusal
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Refused(val refusal: TranslationRefusal) : ApiResult<Nothing>
}
