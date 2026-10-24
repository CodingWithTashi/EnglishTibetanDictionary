package com.kharagedition.tibetandictionary.data.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BillingModelsTest {

    @Test
    fun `plans are found by their exact Play subscription id, with or without the base plan`() {
        assertThat(ProPlan.fromProductId("dictionary_pro")).isEqualTo(ProPlan.MONTHLY)
        assertThat(ProPlan.fromProductId("dictionary_pro:monthly")).isEqualTo(ProPlan.MONTHLY)
        assertThat(ProPlan.fromProductId("dictionary_pro_annually")).isEqualTo(ProPlan.YEARLY)
        assertThat(ProPlan.fromProductId("dictionary_pro_annually:yearly")).isEqualTo(ProPlan.YEARLY)
        assertThat(ProPlan.fromProductId("dictionary_pro_x")).isNull()
        assertThat(ProPlan.fromProductId("")).isNull()
        assertThat(ProPlan.fromProductId(null)).isNull()
    }

    @Test
    fun `plan codes are the analytics and server names`() {
        assertThat(ProPlan.MONTHLY.code).isEqualTo("monthly")
        assertThat(ProPlan.YEARLY.code).isEqualTo("yearly")
    }

    @Test
    fun `manage subscription opens Google Play's page for the plan's subscription`() {
        assertThat(manageSubscriptionUrl(ProPlan.MONTHLY)).isEqualTo(
            "https://play.google.com/store/account/subscriptions?sku=dictionary_pro" +
                "&package=com.kharagedition.tibetandictionary",
        )
        assertThat(manageSubscriptionUrl(ProPlan.YEARLY)).isEqualTo(
            "https://play.google.com/store/account/subscriptions?sku=dictionary_pro_annually" +
                "&package=com.kharagedition.tibetandictionary",
        )
    }

    @Test
    fun `without a known plan manage subscription opens the app's subscriptions list`() {
        assertThat(manageSubscriptionUrl(null)).isEqualTo(
            "https://play.google.com/store/account/subscriptions?package=com.kharagedition.tibetandictionary",
        )
    }

    @Test
    fun `billing error codes are the analytics names`() {
        assertThat(BillingError.entries.map { it.code })
            .containsExactly("network", "store", "not_allowed", "unavailable", "unknown").inOrder()
    }
}
