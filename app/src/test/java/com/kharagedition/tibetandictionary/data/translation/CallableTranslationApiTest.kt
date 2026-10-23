package com.kharagedition.tibetandictionary.data.translation

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val RESETS_AT: Instant = Instant.parse("2026-10-23T18:00:00.000Z")
private val BO_EN = LangPair(Lang.BO, Lang.EN)

/** Answers every call with [reply], or throws [failure]; keeps each call's name and data. */
private class ScriptedInvoker(var reply: Any? = null, var failure: Exception? = null) : CallableInvoker {
    val calls = mutableListOf<Pair<String, Map<String, Any?>?>>()

    override suspend fun call(name: String, data: Map<String, Any?>?): Any? {
        calls += name to data
        failure?.let { throw it }
        return reply
    }
}

private fun quotaMap(plan: String = "free", used: Any = 1, limit: Any = 10, maxChars: Any = 100) = mapOf(
    "plan" to plan,
    "used" to used,
    "limit" to limit,
    "remaining" to 9,
    "resetsAt" to "2026-10-23T18:00:00.000Z",
    "maxChars" to maxChars,
)

private fun refusal(code: String, reason: String?, vararg extra: Pair<String, Any?>) =
    CallableException(code, reason?.let { mapOf("reason" to it, *extra) })

@RunWith(Enclosed::class)
class CallableTranslationApiTest {

    class Replies {
        private val invoker = ScriptedInvoker()
        private val errors = RecordingErrorReporter()
        private val api = CallableTranslationApi(invoker, errors)

        @Test
        fun `translate parses the reply`() = runTest {
            invoker.reply = mapOf(
                "translation" to "Tashi Delek",
                "source" to "bo",
                "target" to "en",
                "cached" to false,
                "provider" to "azure",
                "quota" to quotaMap(used = 1L, maxChars = 100.0),
            )
            assertThat(api.translate("ཀ", BO_EN, "Asia/Thimphu")).isEqualTo(
                ApiResult.Ok(
                    TranslateReply(
                        TranslatedText("Tashi Delek", cached = false, Provider.AZURE),
                        Quota(Plan.FREE, used = 1, limit = 10, resetsAt = RESETS_AT, maxChars = 100),
                    ),
                ),
            )
        }

        @Test
        fun `a cached reply without quota parses with quota null`() = runTest {
            invoker.reply = mapOf("translation" to "Tashi Delek", "cached" to true, "provider" to "google")
            assertThat(api.translate("ཀ", BO_EN, "UTC"))
                .isEqualTo(ApiResult.Ok(TranslateReply(TranslatedText("Tashi Delek", true, Provider.GOOGLE), null)))
        }

        @Test
        fun `an unknown provider is OTHER`() = runTest {
            invoker.reply = mapOf("translation" to "x", "cached" to false, "provider" to "deepl")
            val reply = (api.translate("ཀ", BO_EN, "UTC") as ApiResult.Ok).value
            assertThat(reply.text.provider).isEqualTo(Provider.OTHER)
        }

        @Test
        fun `translate sends text, codes and time zone`() = runTest {
            invoker.reply = mapOf("translation" to "x", "cached" to false, "provider" to "azure")
            api.translate("ཀ", BO_EN, "Asia/Thimphu")
            assertThat(invoker.calls).containsExactly(
                "translate" to mapOf("text" to "ཀ", "source" to "bo", "target" to "en", "timeZone" to "Asia/Thimphu"),
            )
        }

        @Test
        fun `status parses pairs and freeLimit`() = runTest {
            invoker.reply = quotaMap(plan = "guest", used = 1, limit = 3) + mapOf(
                "enabled" to true,
                "pairs" to listOf("bo-en", "en-bo", "zz-en"),
                "freeLimit" to 10,
            )
            assertThat(api.status("Asia/Thimphu")).isEqualTo(
                ApiResult.Ok(
                    StatusReply(
                        enabled = true,
                        quota = Quota(Plan.GUEST, used = 1, limit = 3, resetsAt = RESETS_AT, maxChars = 100),
                        freeLimit = 10,
                        pairs = setOf(BO_EN, LangPair(Lang.EN, Lang.BO)),
                    ),
                ),
            )
            assertThat(invoker.calls).containsExactly("getTranslationStatus" to mapOf("timeZone" to "Asia/Thimphu"))
        }

        @Test
        fun `deleteAccount sends no data`() = runTest {
            invoker.reply = mapOf("deleted" to true)
            assertThat(api.deleteAccount()).isEqualTo(ApiResult.Ok(Unit))
            assertThat(invoker.calls).containsExactly("deleteAccount" to null)
        }

        @Test
        fun `remaining never goes below zero`() {
            assertThat(Quota(Plan.FREE, used = 12, limit = 10, resetsAt = RESETS_AT, maxChars = 100).remaining)
                .isEqualTo(0)
        }

        @Test
        fun `a malformed reply is Unexpected and recorded`() = runTest {
            invoker.reply = mapOf("cached" to false, "provider" to "azure")
            assertThat(api.translate("ཀ", BO_EN, "UTC")).isEqualTo(ApiResult.Refused(TranslationRefusal.Unexpected))
            assertThat(errors.recorded).hasSize(1)
        }

        @Test
        fun `daily_limit for a guest carries plan GUEST and freeLimit 10`() = runTest {
            invoker.failure = refusal(
                "RESOURCE_EXHAUSTED", "daily_limit",
                "limit" to 3, "resetsAt" to "2026-10-23T18:00:00.000Z", "plan" to "guest", "freeLimit" to 10,
            )
            assertThat(api.translate("ཀ", BO_EN, "UTC")).isEqualTo(
                ApiResult.Refused(TranslationRefusal.DailyLimit(Plan.GUEST, 3, RESETS_AT, freeLimit = 10)),
            )
        }

        @Test
        fun `an unexpected failure is recorded`() = runTest {
            invoker.failure = CallableException("INTERNAL", null)
            assertThat(api.status("UTC")).isEqualTo(ApiResult.Refused(TranslationRefusal.Unexpected))
            assertThat(errors.recorded).hasSize(1)
        }

        @Test
        fun `deleteAccount as a guest is GuestAccount`() = runTest {
            invoker.failure = refusal("FAILED_PRECONDITION", "guest_account")
            assertThat(api.deleteAccount()).isEqualTo(ApiResult.Refused(TranslationRefusal.GuestAccount))
        }

        @Test
        fun `cancellation is not a refusal`() {
            invoker.failure = CancellationException("left the screen")
            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking { api.translate("ཀ", BO_EN, "UTC") }
            }
        }
    }

    @RunWith(Parameterized::class)
    class Refusals(
        @Suppress("unused") private val name: String,
        private val failure: Exception,
        private val expected: TranslationRefusal,
    ) {
        @Test
        fun `refusals map per the README`() = runTest {
            val errors = RecordingErrorReporter()
            val api = CallableTranslationApi(ScriptedInvoker(failure = failure), errors)
            assertThat(api.translate("ཀ", BO_EN, "UTC")).isEqualTo(ApiResult.Refused(expected))
            // App Check or token rejections are recorded too: they mean the app is misconfigured or attacked.
            val recorded = expected == TranslationRefusal.Unexpected || expected == TranslationRefusal.Unauthenticated
            assertThat(errors.recorded).hasSize(if (recorded) 1 else 0)
        }

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{0}")
            fun cases(): List<Array<Any>> = listOf(
                arrayOf(
                    "daily_limit",
                    refusal(
                        "RESOURCE_EXHAUSTED", "daily_limit",
                        "limit" to 10L, "resetsAt" to "2026-10-23T18:00:00.000Z", "plan" to "free", "freeLimit" to 10,
                    ),
                    TranslationRefusal.DailyLimit(Plan.FREE, 10, RESETS_AT, freeLimit = 10),
                ),
                arrayOf(
                    "daily_limit without plan or freeLimit",
                    refusal("RESOURCE_EXHAUSTED", "daily_limit", "limit" to 10, "resetsAt" to "2026-10-23T18:00:00.000Z"),
                    TranslationRefusal.DailyLimit(Plan.FREE, 10, RESETS_AT, freeLimit = null),
                ),
                arrayOf(
                    "daily_limit without resetsAt",
                    refusal("RESOURCE_EXHAUSTED", "daily_limit", "limit" to 10),
                    TranslationRefusal.Unexpected,
                ),
                arrayOf(
                    "rate_limited",
                    refusal("RESOURCE_EXHAUSTED", "rate_limited", "retryAfterSec" to 30.0),
                    TranslationRefusal.RateLimited(30),
                ),
                arrayOf("service_budget", refusal("RESOURCE_EXHAUSTED", "service_budget"), TranslationRefusal.Busy),
                arrayOf(
                    "provider_unavailable",
                    refusal("UNAVAILABLE", "provider_unavailable"),
                    TranslationRefusal.Busy,
                ),
                arrayOf("UNAVAILABLE with no reason", refusal("UNAVAILABLE", null), TranslationRefusal.Offline),
                arrayOf("IOException", IOException("no network"), TranslationRefusal.Offline),
                arrayOf(
                    "translation_disabled",
                    refusal("FAILED_PRECONDITION", "translation_disabled"),
                    TranslationRefusal.Disabled,
                ),
                arrayOf("guest_account", refusal("FAILED_PRECONDITION", "guest_account"), TranslationRefusal.GuestAccount),
                arrayOf(
                    "text_too_long",
                    refusal("INVALID_ARGUMENT", "text_too_long", "maxChars" to 100, "length" to 132),
                    TranslationRefusal.TooLong(maxChars = 100, length = 132),
                ),
                arrayOf(
                    "unsupported_pair",
                    refusal("INVALID_ARGUMENT", "unsupported_pair"),
                    TranslationRefusal.UnsupportedPair,
                ),
                arrayOf(
                    "not_tibetan_script",
                    refusal("INVALID_ARGUMENT", "not_tibetan_script"),
                    TranslationRefusal.NotTibetanScript,
                ),
                arrayOf("wrong_script", refusal("INVALID_ARGUMENT", "wrong_script"), TranslationRefusal.WrongScript),
                arrayOf("empty_text", refusal("INVALID_ARGUMENT", "empty_text"), TranslationRefusal.EmptyText),
                arrayOf(
                    "unauthenticated",
                    refusal("UNAUTHENTICATED", "unauthenticated"),
                    TranslationRefusal.Unauthenticated,
                ),
                arrayOf(
                    "App Check rejected (no reason)",
                    refusal("UNAUTHENTICATED", null),
                    TranslationRefusal.Unauthenticated,
                ),
                arrayOf(
                    "account gone",
                    AccountGoneException(IllegalStateException("no user")),
                    TranslationRefusal.AccountGone,
                ),
                arrayOf("bad_request", refusal("INVALID_ARGUMENT", "bad_request"), TranslationRefusal.Unexpected),
                arrayOf("INTERNAL", refusal("INTERNAL", null), TranslationRefusal.Unexpected),
                arrayOf("any other exception", IllegalStateException("bug"), TranslationRefusal.Unexpected),
            )
        }
    }
}
