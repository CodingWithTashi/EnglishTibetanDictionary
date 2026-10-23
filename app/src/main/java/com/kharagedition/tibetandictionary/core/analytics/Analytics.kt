package com.kharagedition.tibetandictionary.core.analytics

/** Product events. None carries user text: no query, source or translation is ever logged. */
interface Analytics {
    /** Records [event]; must never throw. */
    fun log(event: AnalyticsEvent)
}

/** Where a translation was asked for. */
enum class TranslateSurface(val value: String) {
    TRANSLATE_TAB("translate_tab"),
    SEARCH("search"),
}

/** Which button led to a sign-in or Pro prompt. */
enum class PromptSource(val value: String) {
    TRANSLATE_LIMIT("translate_limit"),
    SEARCH_LIMIT("search_limit"),
    TODAY_SHEET("today_sheet"),
    PROFILE_ACCOUNT("profile_account"),
    PROFILE_PLAN("profile_plan"),
    TOO_LONG("too_long"),
}

sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, String>

    data class Translate(
        val outcome: String,
        val cached: Boolean,
        val pair: String,
        val plan: String?,
        val surface: TranslateSurface,
    ) : AnalyticsEvent {
        override val name get() = "translate"
        override val params
            get() = buildMap {
                put("outcome", outcome)
                put("cached", cached.toString())
                put("pair", pair)
                plan?.let { put("plan", it) }
                put("surface", surface.value)
            }
    }

    data class QuotaLimitHit(val plan: String, val surface: TranslateSurface) : AnalyticsEvent {
        override val name get() = "quota_limit_hit"
        override val params get() = mapOf("plan" to plan, "surface" to surface.value)
    }

    /** The user tapped a sign-in button. */
    data class SignInPrompt(val source: PromptSource) : AnalyticsEvent {
        override val name get() = "sign_in_prompt"
        override val params get() = mapOf("source" to source.value)
    }

    data class SignInSuccess(val linked: Boolean) : AnalyticsEvent {
        override val name get() = "sign_in_success"
        override val params get() = mapOf("linked" to linked.toString())
    }

    data class ProInterest(val source: PromptSource) : AnalyticsEvent {
        override val name get() = "pro_interest"
        override val params get() = mapOf("source" to source.value)
    }

    data object AccountDeleted : AnalyticsEvent {
        override val name get() = "account_deleted"
        override val params get() = emptyMap<String, String>()
    }
}
