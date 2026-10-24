package com.kharagedition.tibetandictionary.ui.paywall

import androidx.compose.runtime.Immutable
import com.kharagedition.tibetandictionary.data.billing.ProOffer
import com.kharagedition.tibetandictionary.data.billing.ProOffers
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.ui.model.UiText

/** Buying Pro, or ([SWITCH]) replacing a monthly subscription with yearly; [code] is the `paywall_view` mode. */
enum class PaywallMode(val code: String) {
    PURCHASE("offers"),
    SWITCH("switch"),
}

/** The Free/Pro comparison: Free's daily limit and characters, Pro's fair-use limit and characters. */
@Immutable
data class ProTable(val freePerDay: Int, val freeChars: Int, val proLimit: Int, val proChars: Int) {
    companion object {
        /** Until a status has the numbers; the server's defaults. */
        val FALLBACK = ProTable(freePerDay = 10, freeChars = 100, proLimit = 300, proChars = 500)
    }
}

/** What the Pro sheet shows. [Offers.working] and [Switch.working]: a purchase or restore is running. */
@Immutable
sealed interface ProSheetUi {
    /** Billing is switched off or this build can't sell: "Coming soon". */
    data object FakeDoor : ProSheetUi
    data object NeedsSignIn : ProSheetUi
    data object Loading : ProSheetUi
    data class Offers(val offers: ProOffers, val selected: ProPlan, val working: Boolean = false) : ProSheetUi
    data class Switch(val yearly: ProOffer, val working: Boolean = false) : ProSheetUi
    data object Error : ProSheetUi
    data object Pending : ProSheetUi

    /** The store already has Pro for this user; the sheet syncs the server and closes. */
    data object AlreadyPro : ProSheetUi
}

@Immutable
data class PaywallUiState(val sheet: ProSheetUi, val table: ProTable)

sealed interface PaywallEvent {
    data class Select(val plan: ProPlan) : PaywallEvent
    data object Retry : PaywallEvent
    data object Restore : PaywallEvent
}

sealed interface PaywallEffect {
    /** Close the sheet, then show [toast] if there is one. */
    data class Close(val toast: UiText?) : PaywallEffect

    /** Show [text]; the sheet stays. */
    data class Toast(val text: UiText) : PaywallEffect
}
