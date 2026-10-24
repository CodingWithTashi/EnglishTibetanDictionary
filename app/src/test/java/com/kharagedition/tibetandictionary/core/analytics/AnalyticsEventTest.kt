package com.kharagedition.tibetandictionary.core.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsEventTest {

    @Test
    fun `translate event carries only the allowed params`() {
        val event = AnalyticsEvent.Translate(
            outcome = "translated",
            cached = true,
            pair = "bo-en",
            plan = "guest",
            surface = TranslateSurface.SEARCH,
        )
        assertThat(event.name).isEqualTo("translate")
        assertThat(event.params).containsExactly(
            "outcome", "translated",
            "cached", "true",
            "pair", "bo-en",
            "plan", "guest",
            "surface", "search",
        )
    }

    @Test
    fun `a translate event without a plan leaves the key out`() {
        val event = AnalyticsEvent.Translate(
            outcome = "offline",
            cached = false,
            pair = "en-bo",
            plan = null,
            surface = TranslateSurface.TRANSLATE_TAB,
        )
        assertThat(event.params).containsExactly(
            "outcome", "offline",
            "cached", "false",
            "pair", "en-bo",
            "surface", "translate_tab",
        )
    }

    @Test
    fun `the other events have the documented names and params`() {
        AnalyticsEvent.QuotaLimitHit(plan = "free", surface = TranslateSurface.SEARCH).let {
            assertThat(it.name).isEqualTo("quota_limit_hit")
            assertThat(it.params).containsExactly("plan", "free", "surface", "search")
        }
        AnalyticsEvent.SignInPrompt(PromptSource.TODAY_SHEET).let {
            assertThat(it.name).isEqualTo("sign_in_prompt")
            assertThat(it.params).containsExactly("source", "today_sheet")
        }
        AnalyticsEvent.SignInSuccess(linked = true).let {
            assertThat(it.name).isEqualTo("sign_in_success")
            assertThat(it.params).containsExactly("linked", "true")
        }
        AnalyticsEvent.ProInterest(PromptSource.TOO_LONG).let {
            assertThat(it.name).isEqualTo("pro_interest")
            assertThat(it.params).containsExactly("source", "too_long")
        }
        assertThat(AnalyticsEvent.AccountDeleted.name).isEqualTo("account_deleted")
        assertThat(AnalyticsEvent.AccountDeleted.params).isEmpty()
    }

    @Test
    fun `prompt sources have their documented values`() {
        assertThat(PromptSource.entries.map { it.value }).containsExactly(
            "translate_limit", "search_limit", "today_sheet", "profile_account", "profile_plan", "too_long",
        )
    }

    @Test
    fun `billing events have the documented names and params`() {
        AnalyticsEvent.PaywallView(PromptSource.TRANSLATE_LIMIT, mode = "offers").let {
            assertThat(it.name).isEqualTo("paywall_view")
            assertThat(it.params).containsExactly("source", "translate_limit", "mode", "offers")
        }
        AnalyticsEvent.PurchaseStart(plan = "yearly", source = PromptSource.PROFILE_PLAN).let {
            assertThat(it.name).isEqualTo("purchase_start")
            assertThat(it.params).containsExactly("plan", "yearly", "source", "profile_plan")
        }
        AnalyticsEvent.PurchaseResult(plan = "monthly", outcome = "error", code = "network", switch = false).let {
            assertThat(it.name).isEqualTo("purchase_result")
            assertThat(it.params).containsExactly("plan", "monthly", "outcome", "error", "code", "network", "switch", "false")
        }
        AnalyticsEvent.PurchaseResult(plan = "yearly", outcome = "success", code = null, switch = true).let {
            assertThat(it.params).containsExactly("plan", "yearly", "outcome", "success", "switch", "true")
        }
        AnalyticsEvent.RestoreResult(outcome = "none").let {
            assertThat(it.name).isEqualTo("restore_result")
            assertThat(it.params).containsExactly("outcome", "none")
        }
    }
}
