package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.billing.ProOffer
import com.kharagedition.tibetandictionary.data.billing.ProOffers
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.ui.paywall.PaywallEvent
import com.kharagedition.tibetandictionary.ui.paywall.PaywallUiState
import com.kharagedition.tibetandictionary.ui.paywall.ProSheetUi
import com.kharagedition.tibetandictionary.ui.paywall.ProTable
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Pro's characters per translation, also named by Translate's "Need longer text?" line. */
internal const val PRO_MAX_CHARS = 500

private val TitleTop = 18.dp
private val SubtitleTop = 8.dp
private val TableTop = 24.dp
private val CellPadding = 12.dp
private val ButtonTop = 24.dp
private val NoteTop = 10.dp
private val PlansTop = 20.dp
private val PlanGap = 10.dp
private val PlanPlaceholder = 64.dp
private val ProgressTop = 12.dp
private val SheetBottom = 24.dp
private const val LABEL_WEIGHT = 1.4f

/**
 * The Pro sheet (1za), drawn from [state]: "Translate without limits" and the Free/Pro table, then what [ProSheetUi]
 * says: the fake door's disabled "Coming soon", sign-in, the plans with Continue and Restore purchase, an error with
 * retry, a pending payment, or "already Pro". Switching to yearly has its own title and the yearly plan alone. It
 * can't be swiped away while a purchase runs.
 *
 * Tags: `pro_sheet`, `pro_sheet_coming_soon`, `pro_sheet_fair_use`, `pro_sheet_sign_in`, `pro_sheet_loading`,
 * `pro_sheet_plan_yearly`, `pro_sheet_plan_monthly`, `pro_sheet_continue`, `pro_sheet_progress`, `pro_sheet_restore`,
 * `pro_sheet_error`, `pro_sheet_retry`, `pro_sheet_pending`, `pro_sheet_already`, `pro_sheet_switch`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(
    state: PaywallUiState,
    onEvent: (PaywallEvent) -> Unit,
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DictModalSheet(onDismissRequest = onDismiss, modifier = modifier, dismissible = !state.sheet.working) {
        ProSheetContent(state, onEvent, onContinue, onSignIn)
    }
}

private val ProSheetUi.working: Boolean
    get() = (this as? ProSheetUi.Offers)?.working == true || (this as? ProSheetUi.Switch)?.working == true

@Composable
private fun ProSheetContent(
    state: PaywallUiState,
    onEvent: (PaywallEvent) -> Unit,
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
) {
    val sheet = state.sheet
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("pro_sheet")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DictTheme.dimens.screenPadding)
            .padding(bottom = SheetBottom),
    ) {
        FlagStrand(FlagStrandSize.Onboarding)
        if (sheet is ProSheetUi.Switch) {
            SwitchBody(sheet, onContinue)
            return@Column
        }
        Heading(stringResource(R.string.pro_title))
        Note(stringResource(R.string.pro_subtitle), Modifier.padding(top = SubtitleTop), center = false)
        ComparisonTable(state.table, fairUse = sheet != ProSheetUi.FakeDoor)
        when (sheet) {
            ProSheetUi.FakeDoor -> FakeDoorBody()
            ProSheetUi.NeedsSignIn -> GoogleSignInButton(
                stringResource(R.string.pro_sign_in_to_continue),
                onClick = onSignIn,
                modifier = Modifier
                    .padding(top = ButtonTop)
                    .fillMaxWidth()
                    .testTag("pro_sheet_sign_in"),
                size = ButtonSize.Cta,
            )
            ProSheetUi.Loading -> PlanPlaceholders()
            is ProSheetUi.Offers -> OffersBody(sheet, onEvent, onContinue)
            ProSheetUi.Error -> Column(
                Modifier
                    .padding(top = ButtonTop)
                    .fillMaxWidth()
                    .testTag("pro_sheet_error"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Note(stringResource(R.string.pro_error), color = DictTheme.colors.ink)
                OutlinedButton(
                    stringResource(R.string.try_again),
                    onClick = { onEvent(PaywallEvent.Retry) },
                    modifier = Modifier
                        .padding(top = NoteTop)
                        .testTag("pro_sheet_retry"),
                )
            }
            ProSheetUi.Pending -> Note(
                stringResource(R.string.pro_pending),
                Modifier
                    .padding(top = ButtonTop)
                    .testTag("pro_sheet_pending"),
                color = DictTheme.colors.ink,
            )
            ProSheetUi.AlreadyPro -> Column(Modifier.padding(top = ButtonTop)) {
                Note(
                    stringResource(R.string.pro_already),
                    Modifier.testTag("pro_sheet_already"),
                    color = DictTheme.colors.ink,
                )
                ProgressBar(progress = null, Modifier.padding(top = ProgressTop))
            }
            is ProSheetUi.Switch -> Unit
        }
    }
}

@Composable
private fun Heading(text: String) {
    MixedText(
        text,
        DictTheme.type.onboardingTitle,
        DictTheme.type.tibetanGreeting,
        Modifier
            .padding(top = TitleTop)
            .semantics { heading() },
        color = DictTheme.colors.ink,
    )
}

/** A muted caption line, centred unless [center] is false. */
@Composable
private fun Note(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DictTheme.colors.muted,
    center: Boolean = true,
) {
    MixedText(
        text,
        if (center) DictTheme.type.caption else DictTheme.type.bodySmall,
        if (center) DictTheme.type.tibetanInline14 else DictTheme.type.tibetanInline15,
        modifier.fillMaxWidth(),
        color = color,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** Free against Pro; with [fairUse], Pro's "Unlimited" carries an asterisk and the fair-use limit follows. */
@Composable
private fun ComparisonTable(table: ProTable, fairUse: Boolean) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val language = DictTheme.language
    val unlimited = stringResource(R.string.pro_unlimited)
    Column(Modifier.padding(top = TableTop)) {
        TableRow(divider = false) {
            Cell("", type.label, colors.muted, label = true)
            Cell(stringResource(R.string.pro_free), type.labelSemibold, colors.muted)
            Cell(stringResource(R.string.pro_plan), type.labelSemibold, colors.accent)
        }
        TableRow {
            Cell(stringResource(R.string.pro_per_day), type.secondary, colors.ink, label = true)
            Cell(TibetanNumerals.formatCount(table.freePerDay, language), type.secondary, colors.ink)
            Cell(if (fairUse) "$unlimited*" else unlimited, type.labelSemibold, colors.ink)
        }
        TableRow {
            Cell(stringResource(R.string.pro_characters), type.secondary, colors.ink, label = true)
            Cell(TibetanNumerals.formatCount(table.freeChars, language), type.secondary, colors.ink)
            Cell(TibetanNumerals.formatCount(table.proChars, language), type.labelSemibold, colors.ink)
        }
        TableRow {
            Cell(stringResource(R.string.pro_dictionary), type.secondary, colors.ink, label = true)
            Cell(stringResource(R.string.pro_full), type.secondary, colors.ink)
            Cell(stringResource(R.string.pro_full), type.labelSemibold, colors.ink)
        }
        HorizontalDivider(color = colors.divider)
    }
    if (fairUse) {
        Note(
            stringResource(R.string.pro_fair_use_note, TibetanNumerals.formatCount(table.proLimit, language)),
            Modifier
                .padding(top = NoteTop)
                .testTag("pro_sheet_fair_use"),
            center = false,
        )
    }
}

@Composable
private fun FakeDoorBody() {
    FilledButton(
        stringResource(R.string.pro_coming_soon),
        onClick = {},
        modifier = Modifier
            .padding(top = ButtonTop)
            .fillMaxWidth()
            .testTag("pro_sheet_coming_soon"),
        size = ButtonSize.Cta,
        enabled = false,
    )
    Note(stringResource(R.string.pro_note), Modifier.padding(top = NoteTop))
}

/** Two plan-sized blocks while Play's offers load; hidden from accessibility services. */
@Composable
private fun PlanPlaceholders() {
    Column(
        Modifier
            .padding(top = PlansTop)
            .testTag("pro_sheet_loading"),
        verticalArrangement = Arrangement.spacedBy(PlanGap),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PlanPlaceholder)
                    .clearAndSetSemantics {}
                    .background(DictTheme.colors.surface1, DictShapes.medium),
            )
        }
    }
}

@Composable
private fun ColumnScope.OffersBody(sheet: ProSheetUi.Offers, onEvent: (PaywallEvent) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.padding(top = PlansTop), verticalArrangement = Arrangement.spacedBy(PlanGap)) {
        sheet.offers.yearly?.let { offer ->
            PlanRow(offer, sheet.selected == ProPlan.YEARLY) { onEvent(PaywallEvent.Select(ProPlan.YEARLY)) }
        }
        sheet.offers.monthly?.let { offer ->
            PlanRow(offer, sheet.selected == ProPlan.MONTHLY) { onEvent(PaywallEvent.Select(ProPlan.MONTHLY)) }
        }
    }
    PurchaseButton(stringResource(R.string.continue_), "pro_sheet_continue", sheet.working, onContinue)
    Note(stringResource(R.string.pro_terms), Modifier.padding(top = NoteTop))
    TextButton(
        stringResource(R.string.pro_restore),
        onClick = { onEvent(PaywallEvent.Restore) },
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .testTag("pro_sheet_restore"),
        size = ButtonSize.Small,
        enabled = !sheet.working,
    )
}

/** Switching monthly to yearly: the yearly plan, what happens to the monthly time, and the switch. */
@Composable
private fun SwitchBody(sheet: ProSheetUi.Switch, onContinue: () -> Unit) {
    Heading(stringResource(R.string.pro_switch_title))
    Column(Modifier.padding(top = PlansTop)) {
        PlanRow(sheet.yearly, selected = true, onClick = {})
    }
    Note(stringResource(R.string.pro_switch_note), Modifier.padding(top = NoteTop), center = false)
    PurchaseButton(stringResource(R.string.pro_switch_button), "pro_sheet_switch", sheet.working, onContinue)
}

/** The plan's name, then Play's price (and, for yearly, its price per month). Tag `pro_sheet_plan_{code}`. */
@Composable
private fun PlanRow(offer: ProOffer, selected: Boolean, onClick: () -> Unit) {
    val perMonth = offer.pricePerMonth?.let { stringResource(R.string.pro_per_month, it) }
    OptionCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag("pro_sheet_plan_${offer.plan.code}"),
        description = listOfNotNull(offer.price, perMonth).joinToString(" · "),
        stacked = true,
    ) {
        MixedText(
            stringResource(if (offer.plan == ProPlan.YEARLY) R.string.pro_yearly else R.string.pro_monthly),
            DictTheme.type.buttonLarge,
            DictTheme.type.tibetanGloss,
        )
    }
}

/** The sheet's main button; while [working], disabled under an indeterminate bar. */
@Composable
private fun PurchaseButton(text: String, tag: String, working: Boolean, onClick: () -> Unit) {
    if (working) {
        ProgressBar(
            progress = null,
            Modifier
                .padding(top = ProgressTop)
                .testTag("pro_sheet_progress"),
        )
    }
    FilledButton(
        text,
        onClick = onClick,
        modifier = Modifier
            .padding(top = if (working) ProgressTop else ButtonTop)
            .fillMaxWidth()
            .testTag(tag),
        size = ButtonSize.Cta,
        enabled = !working,
    )
}

/** One table row under a divider line (the header has none). */
@Composable
private fun TableRow(divider: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (divider) HorizontalDivider(color = DictTheme.colors.divider)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = CellPadding),
        horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        content = content,
    )
}

/** A [label] cell is the wider first column, start-aligned; the plan columns are centred. */
@Composable
private fun RowScope.Cell(text: String, style: TextStyle, color: Color, label: Boolean = false) {
    MixedText(
        text,
        style,
        DictTheme.type.tibetanInline15,
        Modifier.weight(if (label) LABEL_WEIGHT else 1f),
        color = color,
        textAlign = if (label) TextAlign.Start else TextAlign.Center,
    )
}

private val PreviewOffers = ProOffers(
    monthly = ProOffer(ProPlan.MONTHLY, price = "₹49.00", pricePerMonth = null),
    yearly = ProOffer(ProPlan.YEARLY, price = "₹399.00", pricePerMonth = "₹33.25"),
)

@PreviewLightDark
@BoPreview
@Composable
private fun ProSheetFakeDoorPreview() {
    ComponentPreview(edgeToEdge = true) {
        ProSheetContent(PaywallUiState(ProSheetUi.FakeDoor, ProTable.FALLBACK), {}, {}, {})
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun ProSheetOffersPreview() {
    ComponentPreview(edgeToEdge = true) {
        ProSheetContent(
            PaywallUiState(ProSheetUi.Offers(PreviewOffers, ProPlan.YEARLY), ProTable.FALLBACK),
            {},
            {},
            {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ProSheetSwitchPreview() {
    ComponentPreview(edgeToEdge = true) {
        ProSheetContent(PaywallUiState(ProSheetUi.Switch(PreviewOffers.yearly!!), ProTable.FALLBACK), {}, {}, {})
    }
}
