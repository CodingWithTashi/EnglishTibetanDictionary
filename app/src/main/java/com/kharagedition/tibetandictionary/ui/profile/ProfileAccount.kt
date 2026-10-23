package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.ui.components.AccountCard
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.SettingsRow
import com.kharagedition.tibetandictionary.ui.components.SheetTitle
import com.kharagedition.tibetandictionary.ui.components.SignedOutAccountCard
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** The plan card (1z): 16 inside, 10 between its lines, a 4dp usage bar. */
private val PlanPadding = 16.dp
private val PlanGap = 10.dp
private val UsageBarHeight = 4.dp

/** Space under the account sheet's last row, as on the other Profile sheets. */
private val SheetBottom = 24.dp

internal const val ACCOUNT_CARD_TAG = "profile_account_card"

/**
 * The account card: a signed-in Google [account] (tap: the account sheet), or the signed-out card whose button signs
 * in ("Sign in to get 10 free translations a day"), also for a guest.
 */
@Composable
internal fun ProfileAccountCard(account: AccountUi?, onEvent: (ProfileEvent) -> Unit) {
    val modifier = Modifier.testTag(ACCOUNT_CARD_TAG)
    if (account != null) {
        AccountCard(account, onClick = { onEvent(ProfileEvent.OpenAccount) }, modifier = modifier)
    } else {
        SignedOutAccountCard(
            title = stringResource(R.string.profile_signed_out_title),
            body = stringResource(R.string.profile_signed_out_body),
            actionLabel = stringResource(R.string.sign_in),
            onSignIn = { onEvent(ProfileEvent.SignIn(PromptSource.PROFILE_ACCOUNT)) },
            modifier = modifier,
        )
    }
}

/**
 * Today's translations (1z) on a surface1 card. The free plan: "Free plan" with Upgrade (the Pro sheet), the usage
 * bar and "3 of 10 translations used today · 100 characters each". A guest: "Guest" with "Sign in for 10", the bar and
 * "1 of 3 used today". No user yet: one line of pitch.
 */
@Composable
internal fun PlanCard(plan: PlanCardUi, onEvent: (ProfileEvent) -> Unit) {
    val language = DictTheme.language
    val count = { n: Int -> TibetanNumerals.formatCount(n, language) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .background(DictTheme.colors.surface1)
            .padding(PlanPadding)
            .testTag("profile_plan_card"),
        verticalArrangement = Arrangement.spacedBy(PlanGap),
    ) {
        when (plan) {
            PlanCardUi.NoUser -> PlanLine(stringResource(R.string.profile_plan_none))
            is PlanCardUi.Guest -> {
                PlanHeader(stringResource(R.string.profile_plan_guest)) {
                    FilledButton(
                        plan.freeLimit?.let { stringResource(R.string.profile_plan_sign_in_for, count(it)) }
                            ?: stringResource(R.string.profile_plan_sign_in_more),
                        { onEvent(ProfileEvent.SignIn(PromptSource.PROFILE_PLAN)) },
                        Modifier.testTag("profile_plan_sign_in"),
                        size = ButtonSize.Xs,
                    )
                }
                UsageBar(plan.used, plan.limit)
                PlanLine(stringResource(R.string.profile_plan_guest_usage, count(plan.used), count(plan.limit)))
            }
            is PlanCardUi.Free -> {
                PlanHeader(stringResource(R.string.profile_plan_free)) {
                    TextButton(
                        stringResource(R.string.profile_plan_upgrade),
                        { onEvent(ProfileEvent.OpenPro) },
                        Modifier.testTag("profile_plan_upgrade"),
                        size = ButtonSize.Xs,
                    )
                }
                UsageBar(plan.used, plan.limit)
                PlanLine(
                    stringResource(
                        R.string.profile_plan_free_usage,
                        count(plan.used),
                        count(plan.limit),
                        count(plan.maxChars),
                    ),
                )
            }
        }
    }
}

/** The plan's name (15/600) with its [action] at the end. */
@Composable
private fun PlanHeader(title: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MixedText(
            title,
            DictTheme.type.buttonLarge,
            DictTheme.type.tibetanInline16,
            Modifier.weight(1f),
            color = DictTheme.colors.ink,
        )
        action()
    }
}

@Composable
private fun PlanLine(text: String) {
    MixedText(text, DictTheme.type.caption, DictTheme.type.tibetanInline14, color = DictTheme.colors.muted)
}

/** [used] of [limit] as an accent bar on a surface2 track; the line under it says the same, so it is decorative. */
@Composable
private fun UsageBar(used: Int, limit: Int) {
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(UsageBarHeight)
            .clip(DictShapes.pill)
            .background(DictTheme.colors.surface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(DictTheme.colors.accent),
        )
    }
}

/**
 * The account sheet: [account]'s name and e-mail, then Sign out and Delete account (in the danger colour, asking
 * first). Opts in to the experimental Material 3 API because [DictModalSheet]'s signature exposes `SheetState`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(account: AccountUi?, onEvent: (ProfileEvent) -> Unit, onDismiss: () -> Unit) {
    DictModalSheet(onDismiss) {
        SheetTitle(account?.name ?: account?.email ?: stringResource(R.string.account_sheet_title))
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = DictTheme.dimens.screenPadding,
                    end = DictTheme.dimens.screenPadding,
                    bottom = SheetBottom,
                ),
        ) {
            if (account?.name != null) PlanLine(account.email)
            SettingsRow(
                label = stringResource(R.string.sign_out),
                modifier = Modifier.testTag("profile_sign_out"),
                onClick = { onEvent(ProfileEvent.SignOut) },
            )
            SettingsRow(
                label = stringResource(R.string.delete_account),
                modifier = Modifier.testTag("profile_delete_account"),
                onClick = { onEvent(ProfileEvent.AskDeleteAccount) },
                divider = false,
            )
        }
    }
}

/** "Delete your account?": what goes and what stays, with Delete (danger) and Cancel. */
@Composable
internal fun DeleteAccountDialog(deleting: Boolean, onEvent: (ProfileEvent) -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    // While the delete runs the dialog stays, showing progress, and nothing closes it.
    AlertDialog(
        onDismissRequest = { if (!deleting) onEvent(ProfileEvent.DismissDelete) },
        confirmButton = {
            TextButton(
                stringResource(if (deleting) R.string.deleting else R.string.delete),
                { onEvent(ProfileEvent.ConfirmDeleteAccount) },
                enabled = !deleting,
                contentColor = colors.danger,
            )
        },
        dismissButton = {
            TextButton(stringResource(R.string.cancel), { onEvent(ProfileEvent.DismissDelete) }, enabled = !deleting)
        },
        modifier = Modifier.testTag("profile_delete_confirm"),
        title = {
            val title = stringResource(R.string.delete_account_title)
            MixedText(title, type.buttonLarge, type.tibetanGloss, color = colors.ink)
        },
        text = {
            val body = stringResource(R.string.delete_account_body)
            MixedText(body, type.bodySmall, type.tibetanInline16, color = colors.muted)
        },
        containerColor = colors.background,
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun PlanCardPreview() {
    ComponentPreview {
        PlanCard(PlanCardUi.Free(used = 3, limit = 10, maxChars = 100), onEvent = {})
        PlanCard(PlanCardUi.Guest(used = 1, limit = 3, freeLimit = 10), onEvent = {})
        PlanCard(PlanCardUi.NoUser, onEvent = {})
    }
}
