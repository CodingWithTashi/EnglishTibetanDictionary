package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.billing.ProOffer
import com.kharagedition.tibetandictionary.data.billing.ProOffers
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.ui.paywall.PaywallEvent
import com.kharagedition.tibetandictionary.ui.paywall.PaywallUiState
import com.kharagedition.tibetandictionary.ui.paywall.ProSheetUi
import com.kharagedition.tibetandictionary.ui.paywall.ProTable
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<PaywallEvent>()
    private var continues = 0
    private var signIns = 0

    private val yearly = ProOffer(ProPlan.YEARLY, price = "₹399.00", pricePerMonth = "₹33.25")
    private val monthly = ProOffer(ProPlan.MONTHLY, price = "₹49.00", pricePerMonth = null)
    private val offers = ProSheetUi.Offers(ProOffers(monthly, yearly), selected = ProPlan.YEARLY)

    private fun show(sheet: ProSheetUi, table: ProTable = ProTable.FALLBACK) {
        compose.setContent {
            DictTheme(darkTheme = false) {
                ProSheet(
                    PaywallUiState(sheet, table),
                    onEvent = { events += it },
                    onContinue = { continues++ },
                    onSignIn = { signIns++ },
                    onDismiss = {},
                )
            }
        }
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the fake door's coming soon is disabled`() {
        show(ProSheetUi.FakeDoor)

        compose.onNodeWithTag("pro_sheet").assertExists()
        compose.onNodeWithText("Translate without limits").assertExists()
        compose.onNodeWithText("Unlimited").assertExists()
        compose.onNodeWithTag("pro_sheet_coming_soon").assertIsNotEnabled()
        compose.onNodeWithTag("pro_sheet_fair_use").assertDoesNotExist()
    }

    @Test
    fun `a guest signs in to continue`() {
        show(ProSheetUi.NeedsSignIn)

        tap("pro_sheet_sign_in")

        assertThat(signIns).isEqualTo(1)
        compose.onNodeWithTag("pro_sheet_continue").assertDoesNotExist()
    }

    @Test
    fun `loading shows placeholders for the plans`() {
        show(ProSheetUi.Loading)

        compose.onNodeWithTag("pro_sheet_loading").assertExists()
    }

    @Test
    fun `offers show both plans with Play's prices, yearly selected`() {
        show(offers)

        compose.onNodeWithTag("pro_sheet_plan_yearly").assertIsSelected()
            .assertTextContains("₹399.00", substring = true)
            .assertTextContains("₹33.25 / month", substring = true)
        compose.onNodeWithTag("pro_sheet_plan_monthly").assertTextContains("₹49.00", substring = true)
        tap("pro_sheet_plan_monthly")
        tap("pro_sheet_continue")
        tap("pro_sheet_restore")

        assertThat(events).containsExactly(PaywallEvent.Select(ProPlan.MONTHLY), PaywallEvent.Restore).inOrder()
        assertThat(continues).isEqualTo(1)
    }

    @Test
    fun `the table says unlimited with the fair-use limit from status`() {
        show(offers, ProTable.FALLBACK.copy(proLimit = 250))

        compose.onNodeWithText("Unlimited*").assertExists()
        compose.onNodeWithTag("pro_sheet_fair_use").assertTextContains("*Fair use: up to 250 a day")
    }

    @Test
    fun `a running purchase shows progress and can't be repeated`() {
        show(offers.copy(working = true))

        compose.onNodeWithTag("pro_sheet_progress").assertExists()
        compose.onNodeWithTag("pro_sheet_continue").assertIsNotEnabled()
    }

    @Test
    fun `an error offers retry`() {
        show(ProSheetUi.Error)

        compose.onNodeWithTag("pro_sheet_error").assertExists()
        tap("pro_sheet_retry")

        assertThat(events).containsExactly(PaywallEvent.Retry)
    }

    @Test
    fun `a pending payment says Pro starts when Play confirms it`() {
        show(ProSheetUi.Pending)

        compose.onNodeWithTag("pro_sheet_pending")
            .assertTextContains("Payment pending. Pro starts when Google Play confirms it.")
    }

    @Test
    fun `already Pro says so without a purchase button`() {
        show(ProSheetUi.AlreadyPro)

        compose.onNodeWithTag("pro_sheet_already").assertTextContains("Pro is active on this Google Play account")
        compose.onNodeWithTag("pro_sheet_continue").assertDoesNotExist()
    }

    @Test
    fun `switch shows yearly alone and switches`() {
        show(ProSheetUi.Switch(yearly))

        compose.onNodeWithTag("pro_sheet_plan_yearly").assertExists()
        compose.onNodeWithTag("pro_sheet_plan_monthly").assertDoesNotExist()
        compose.onNodeWithTag("pro_sheet_restore").assertDoesNotExist()
        tap("pro_sheet_switch")

        assertThat(continues).isEqualTo(1)
    }
}
