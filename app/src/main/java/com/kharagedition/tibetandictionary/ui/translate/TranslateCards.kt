package com.kharagedition.tibetandictionary.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.GoogleSignInButton
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.OutlinedButton
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

private val CardPadding = 16.dp
private val CardGap = 10.dp
private val NoticeGap = 14.dp

/** Whether [this] block takes the Translate button's place (the limits, paused) or sits under it (failures). */
internal val TranslateBlock.replacesButton: Boolean
    get() = this is TranslateBlock.GuestLimit ||
        this == TranslateBlock.DailyLimit ||
        this == TranslateBlock.FairUseLimit ||
        this == TranslateBlock.Disabled

/**
 * The card for [block]: the guest's sign-in card, the daily limit (1t), Pro's fair-use limit (no upgrade), or a notice
 * with Retry (1u).
 */
@Composable
internal fun TranslateBlockCard(
    block: TranslateBlock,
    onEvent: (TranslateEvent) -> Unit,
    onSignIn: (PromptSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val retry = { onEvent(TranslateEvent.Retry) }
    when (block) {
        is TranslateBlock.GuestLimit -> SignInCard(
            block,
            onSignIn = { onSignIn(PromptSource.TRANSLATE_LIMIT) },
            onLookUp = { onEvent(TranslateEvent.LookUpWords) },
            modifier = modifier,
        )
        TranslateBlock.DailyLimit -> LimitCard(
            onUpgrade = { onEvent(TranslateEvent.OpenPro(PromptSource.TRANSLATE_LIMIT)) },
            onLookUp = { onEvent(TranslateEvent.LookUpWords) },
            modifier = modifier,
        )
        TranslateBlock.FairUseLimit -> OutlinedCard("translate_block_fair_use", modifier) {
            CardText(stringResource(R.string.translate_fair_use_title), stringResource(R.string.today_resets))
            LookUpButton({ onEvent(TranslateEvent.LookUpWords) }, Modifier.fillMaxWidth())
        }
        TranslateBlock.Offline -> NoticeCard(
            "translate_block_offline",
            stringResource(R.string.translate_offline_title),
            stringResource(R.string.translate_offline_body),
            modifier,
            icon = DictIcons.WifiOff,
            onRetry = retry,
        )
        TranslateBlock.Busy -> NoticeCard(
            "translate_block_busy",
            stringResource(R.string.translate_busy_title),
            stringResource(R.string.translate_busy_body),
            modifier,
            onRetry = retry,
        )
        TranslateBlock.Unexpected -> NoticeCard(
            "translate_block_unexpected",
            stringResource(R.string.translate_unexpected_title),
            stringResource(R.string.translate_unexpected_body),
            modifier,
            onRetry = retry,
        )
        TranslateBlock.Disabled -> NoticeCard(
            "translate_block_disabled",
            stringResource(R.string.translate_disabled_title),
            stringResource(R.string.translate_disabled_body),
            modifier,
        )
    }
}

/**
 * A guest out of translations (no screen in the design; drawn like 1t): how many they used, how many signing in
 * adds today, then Google's [Continue with Google] button over [Look up words], each the card's width.
 */
@Composable
internal fun SignInCard(
    block: TranslateBlock.GuestLimit,
    onSignIn: () -> Unit,
    onLookUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = DictTheme.language
    val title = stringResource(R.string.translate_guest_title, TibetanNumerals.formatCount(block.used, language))
    val body = block.more
        ?.let { stringResource(R.string.translate_guest_body, TibetanNumerals.formatCount(it, language)) }
        ?: stringResource(R.string.translate_guest_body_unknown)
    OutlinedCard("translate_block_guest_limit", modifier) {
        CardText(title, body)
        GoogleSignInButton(
            stringResource(R.string.continue_with_google),
            onSignIn,
            Modifier
                .fillMaxWidth()
                .testTag("translate_sign_in"),
        )
        LookUpButton(onLookUp, Modifier.fillMaxWidth())
    }
}

/** 1t: the signed-in user's limit, with the Pro fake door and [Look up words]. */
@Composable
private fun LimitCard(onUpgrade: () -> Unit, onLookUp: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard("translate_block_daily_limit", modifier) {
        CardText(stringResource(R.string.translate_daily_title), stringResource(R.string.translate_daily_body))
        Row(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
            FilledButton(
                stringResource(R.string.upgrade_to_pro),
                onUpgrade,
                Modifier
                    .weight(1f)
                    .testTag("translate_upgrade"),
            )
            LookUpButton(onLookUp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LookUpButton(onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        stringResource(R.string.translate_look_up),
        onClick,
        modifier.testTag("translate_look_up"),
        accentBorder = true,
    )
}

/** 1u: a surface1 row with an optional [icon], the title and body, and [Retry] when [onRetry] is set. */
@Composable
private fun NoticeCard(
    tag: String,
    title: String,
    body: String,
    modifier: Modifier,
    icon: ImageVector? = null,
    onRetry: (() -> Unit)? = null,
) {
    val colors = DictTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) {}
            .background(colors.surface1, DictShapes.large)
            .padding(CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NoticeGap),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(DictTheme.dimens.iconMedium), tint = colors.muted)
        }
        Column(Modifier.weight(1f)) { CardText(title, body, small = true) }
        if (onRetry != null) {
            OutlinedButton(
                stringResource(R.string.retry),
                onRetry,
                Modifier.testTag("translate_retry"),
                size = ButtonSize.Small,
                accentBorder = true,
            )
        }
    }
}

/** The limit cards' frame: 1dp cardBorder, radius 20, padding 16. */
@Composable
private fun OutlinedCard(tag: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) {}
            .border(DictTheme.dimens.border, DictTheme.colors.cardBorder, DictShapes.large)
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap),
        content = content,
    )
}

/** A card's title (600) and body (muted); [small] is the notice size. */
@Composable
private fun CardText(title: String, body: String, small: Boolean = false) {
    val type = DictTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(if (small) 0.dp else 4.dp)) {
        MixedText(title, type.button, type.tibetanInline16, color = DictTheme.colors.ink)
        MixedText(
            body,
            if (small) type.caption else type.secondary,
            type.tibetanInline14,
            color = DictTheme.colors.muted,
        )
    }
}
