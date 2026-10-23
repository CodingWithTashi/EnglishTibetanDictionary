package com.kharagedition.tibetandictionary.ui.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.GoogleSignInButton
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.SheetTitle
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

private val SheetBottom = 24.dp
private val ButtonTop = 8.dp

/**
 * Today's usage, from the pill: "3 of 10 used today", "Resets at midnight", and the next step up: sign in (a
 * guest, to get [freeLimit] a day) or the Pro fake door (free). Pro has no button. Tag: `today_sheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodaySheet(
    usage: UsagePillUi,
    plan: Plan?,
    freeLimit: Int?,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    DictModalSheet(onDismissRequest = onDismiss) {
        TodaySheetContent(usage, plan, freeLimit, onSignIn, onUpgrade)
    }
}

@Composable
private fun TodaySheetContent(
    usage: UsagePillUi,
    plan: Plan?,
    freeLimit: Int?,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val language = DictTheme.language
    val used = TibetanNumerals.formatCount(usage.limit - usage.remaining, language)
    Column(Modifier.fillMaxWidth().testTag("today_sheet").padding(bottom = SheetBottom)) {
        SheetTitle(stringResource(R.string.today_used, used, TibetanNumerals.formatCount(usage.limit, language)))
        Column(
            Modifier.padding(horizontal = DictTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.blockGap),
        ) {
            MixedText(
                stringResource(R.string.today_resets),
                DictTheme.type.secondary,
                DictTheme.type.tibetanInline14,
                color = DictTheme.colors.muted,
            )
            val action = when (plan) {
                Plan.GUEST -> freeLimit?.let {
                    stringResource(R.string.today_sign_in, TibetanNumerals.formatCount(it, language))
                } ?: stringResource(R.string.today_sign_in_unknown)
                Plan.FREE -> stringResource(R.string.upgrade_to_pro)
                Plan.PRO, null -> null
            }
            val buttonModifier = Modifier
                .padding(top = ButtonTop)
                .fillMaxWidth()
            when {
                action == null -> Unit
                plan == Plan.GUEST -> GoogleSignInButton(action, onSignIn, buttonModifier, size = ButtonSize.Cta)
                else -> FilledButton(action, onClick = onUpgrade, modifier = buttonModifier, size = ButtonSize.Cta)
            }
        }
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun TodaySheetPreview() {
    ComponentPreview(edgeToEdge = true) {
        TodaySheetContent(UsagePillUi(remaining = 1, limit = 3, low = true), Plan.GUEST, freeLimit = 10, {}, {})
    }
}
