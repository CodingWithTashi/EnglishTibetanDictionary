package com.kharagedition.tibetandictionary.data.translation

import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * [TranslationApi] over [CallableInvoker]. Expected refusals map to [TranslationRefusal]; anything else is
 * [TranslationRefusal.Unexpected] and is recorded. Recorded errors never carry the text or the translation.
 */
class CallableTranslationApi @Inject constructor(
    private val invoker: CallableInvoker,
    private val errors: ErrorReporter,
) : TranslationApi {

    override suspend fun translate(text: String, pair: LangPair, timeZone: String): ApiResult<TranslateReply> {
        val data = mapOf(
            "text" to text,
            "source" to pair.source.code,
            "target" to pair.target.code,
            "timeZone" to timeZone,
        )
        return call("translate", data) { reply ->
            TranslateReply(
                text = TranslatedText(
                    translation = reply.string("translation"),
                    cached = reply.boolean("cached"),
                    provider = Provider.fromCode(reply["provider"] as? String),
                ),
                quota = reply["quota"]?.let { quotaOf(it as? Map<*, *> ?: malformed("quota")) },
            )
        }
    }

    override suspend fun status(timeZone: String): ApiResult<StatusReply> =
        call("getTranslationStatus", mapOf("timeZone" to timeZone)) { reply ->
            StatusReply(
                enabled = reply.boolean("enabled"),
                quota = quotaOf(reply),
                freeLimit = reply.int("freeLimit"),
                pairs = (reply["pairs"] as? List<*> ?: malformed("pairs"))
                    .mapNotNullTo(mutableSetOf()) { (it as? String)?.let(LangPair::fromKey) },
            )
        }

    override suspend fun deleteAccount(): ApiResult<Unit> = call("deleteAccount", null) {}

    private suspend fun <T> call(name: String, data: Map<String, Any?>?, parse: (Map<*, *>) -> T): ApiResult<T> {
        val reply = try {
            invoker.call(name, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ApiResult.Refused(refusalOf(e))
        }
        return try {
            ApiResult.Ok(parse(reply as? Map<*, *> ?: malformed("reply")))
        } catch (e: RuntimeException) {
            errors.record(IllegalStateException("malformed $name reply", e))
            ApiResult.Refused(TranslationRefusal.Unexpected)
        }
    }

    private fun refusalOf(e: Exception): TranslationRefusal {
        if (e is IOException) return TranslationRefusal.Offline
        if (e is AccountGoneException) return TranslationRefusal.AccountGone
        if (e !is CallableException) return unexpected(e)
        val details = e.details.orEmpty()
        val reason = details["reason"] as? String
        val refusal = when (e.code) {
            "UNAUTHENTICATED" -> {
                // Mostly App Check rejected before the handler ran (no reason): worth knowing about at launch.
                errors.record(e)
                TranslationRefusal.Unauthenticated
            }
            "RESOURCE_EXHAUSTED" -> when (reason) {
                "daily_limit" -> dailyLimitOf(details)
                "rate_limited" -> TranslationRefusal.RateLimited(details.optInt("retryAfterSec"))
                "service_budget" -> TranslationRefusal.Busy
                else -> null
            }
            "UNAVAILABLE" -> when (reason) {
                "provider_unavailable" -> TranslationRefusal.Busy
                null -> TranslationRefusal.Offline
                else -> null
            }
            "FAILED_PRECONDITION" -> when (reason) {
                "translation_disabled" -> TranslationRefusal.Disabled
                "guest_account" -> TranslationRefusal.GuestAccount
                else -> null
            }
            "INVALID_ARGUMENT" -> when (reason) {
                "text_too_long" -> tooLongOf(details)
                "unsupported_pair" -> TranslationRefusal.UnsupportedPair
                "not_tibetan_script" -> TranslationRefusal.NotTibetanScript
                "wrong_script" -> TranslationRefusal.WrongScript
                "empty_text" -> TranslationRefusal.EmptyText
                else -> null
            }
            else -> null
        }
        return refusal ?: unexpected(e)
    }

    private fun unexpected(e: Exception): TranslationRefusal {
        errors.record(e)
        return TranslationRefusal.Unexpected
    }

    private fun dailyLimitOf(details: Map<String, Any?>): TranslationRefusal? {
        val limit = details.optInt("limit") ?: return null
        val resetsAt = (details["resetsAt"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return null
        val plan = Plan.fromCode(details["plan"] as? String) ?: Plan.FREE
        return TranslationRefusal.DailyLimit(plan, limit, resetsAt, details.optInt("freeLimit"))
    }

    private fun tooLongOf(details: Map<String, Any?>): TranslationRefusal? {
        val maxChars = details.optInt("maxChars") ?: return null
        val length = details.optInt("length") ?: return null
        return TranslationRefusal.TooLong(maxChars, length)
    }

    private fun quotaOf(map: Map<*, *>) = Quota(
        plan = Plan.fromCode(map["plan"] as? String) ?: Plan.FREE,
        used = map.int("used"),
        limit = map.int("limit"),
        resetsAt = Instant.parse(map.string("resetsAt")),
        maxChars = map.int("maxChars"),
    )
}

/** Numbers arrive as Int, Long or Double, depending on how the SDK decoded them. */
private fun Map<*, *>.optInt(key: String): Int? = (this[key] as? Number)?.toInt()

private fun Map<*, *>.int(key: String): Int = optInt(key) ?: malformed(key)

private fun Map<*, *>.string(key: String): String = this[key] as? String ?: malformed(key)

private fun Map<*, *>.boolean(key: String): Boolean = this[key] as? Boolean ?: malformed(key)

/** Names only the missing field, never a value: replies carry the user's translation. */
private fun malformed(field: String): Nothing = throw IllegalStateException("missing or wrong type: $field")
