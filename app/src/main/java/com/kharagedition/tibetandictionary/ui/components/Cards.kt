package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.theme.DictTypography

/** Word-of-the-day gaps: 4 between lines, 6 more above the gloss and the actions. */
private val WotdLineGap = 4.dp
private val WotdExtraGap = 6.dp

/** The action row's 48dp touch targets start 12dp before the text edge, so icons line up with the text. */
private val WotdActionsInset = 12.dp

/** Account avatar, sync dot and the gaps inside account and stats cards. */
private val Avatar = 44.dp
private val SyncDot = 6.dp

/** Avatar initials size (16, fixed). */
private val AvatarText = 16.dp
private val CardGap = 14.dp
private val SmallGap = 6.dp

/** Tip box padding (12×14) and the text-size preview card padding (22×20). */
private val TipPaddingVertical = 12.dp
private val TipPaddingHorizontal = 14.dp
private val PreviewPaddingVertical = 22.dp
private val PreviewPaddingHorizontal = 20.dp

/** Tip text line height, as a multiple of its 12sp size. */
private const val TIP_LINE_RATIO = 1.5f

/**
 * The Home word-of-the-day card on surface1 (radius 16): a [FlagStrand] flush along the top, then the accent
 * "WORD OF THE DAY" overline with the date, the headword (Jomolhari 34/1.9), the Wylie (when known), the gloss
 * (15/1.5) and an action row: speaker (only when [WordOfTheDayUi.hasAudio]), heart (filled accent when saved),
 * share, and "Open".
 */
@Composable
fun WordOfTheDayCard(
    wotd: WordOfTheDayUi,
    onPlay: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val type = DictTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .background(colors.surface1),
    ) {
        FlagStrand(FlagStrandSize.Card, Modifier.padding(horizontal = dimens.wotdBodyPaddingHorizontal))
        Column(
            Modifier.padding(
                start = dimens.wotdBodyPaddingHorizontal,
                top = dimens.wotdBodyPaddingTop,
                end = dimens.wotdBodyPaddingHorizontal,
                bottom = dimens.wotdBodyPaddingBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(WotdLineGap),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Overline(
                    stringResource(R.string.wotd_overline),
                    Modifier.weight(1f),
                    style = type.overlineWotd,
                    color = colors.accent,
                )
                MixedText(wotd.date, type.tiny, type.tibetanInline14, color = colors.muted)
            }
            MixedText(
                wotd.headword,
                type.screenTitle,
                type.tibetanWotd,
                Modifier.semantics { heading() },
                color = colors.ink,
            )
            if (wotd.wylie != null) WylieLine(wotd.wylie, matchRange = null, Modifier.pullUp(WylieOverlap))
            MixedText(
                wotd.gloss,
                type.bodyWotdGloss,
                type.tibetanGloss,
                Modifier.padding(top = WotdExtraGap - WotdLineGap),
                color = colors.ink,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = WotdExtraGap)
                    .extendStart(WotdActionsInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val button = dimens.iconButtonMedium
                if (wotd.hasAudio) {
                    DictIconButton(DictIcons.Speaker, stringResource(R.string.play_audio), onPlay, size = button, tint = colors.accent)
                }
                DictIconButton(
                    if (wotd.isSaved) DictIcons.HeartFilled else DictIcons.Heart,
                    stringResource(if (wotd.isSaved) R.string.saved_cd else R.string.save),
                    onToggleSave,
                    size = button,
                    tint = if (wotd.isSaved) colors.accent else colors.muted,
                )
                DictIconButton(DictIcons.Share, stringResource(R.string.share), onShare, size = button, tint = colors.muted)
                Spacer(Modifier.weight(1f))
                TextButton(stringResource(R.string.wotd_open), onOpen, size = ButtonSize.Xs)
            }
        }
    }
}

/**
 * Learn progress on Home: a 1dp cardBorder card (radius 16, padding 14×16) with [title] (14/500), an optional
 * [subtitle] (12 muted) and [ProgressDots] for [weekFlags]. The caller formats both lines from
 * [com.kharagedition.tibetandictionary.ui.model.LearnStatsUi] (Learn ships behind `Features.LEARN`).
 */
@Composable
fun StatsCard(
    title: String,
    subtitle: String?,
    weekFlags: List<Boolean>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .border(dimens.border, colors.cardBorder, DictShapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.cardPaddingHorizontal, vertical = dimens.cardPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.blockGap),
    ) {
        Column(Modifier.weight(1f)) {
            MixedText(title, DictTheme.type.bodySmallMedium, DictTheme.type.tibetanInline16, color = colors.ink)
            if (subtitle != null) {
                MixedText(subtitle, DictTheme.type.caption, DictTheme.type.tibetanInline14, color = colors.muted)
            }
        }
        ProgressDots(weekFlags)
    }
}

/**
 * The signed-in account on Profile: surface1 card with a 44dp accentContainer avatar showing the initials, the
 * e-mail (14/500), the sync line with a success dot when [AccountUi.syncedText] is set, and a chevron.
 */
@Composable
fun AccountCard(account: AccountUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    AccountCardFrame(modifier, onClick) {
        Box(
            Modifier
                .size(Avatar)
                .background(colors.accentContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative and inside a fixed circle, so the initials do not scale with text settings.
            val initialsSize = with(LocalDensity.current) { AvatarText.toSp() }
            Text(
                account.initials,
                Modifier.clearAndSetSemantics {},
                colors.onAccentContainer,
                style = type.headwordLatin.copy(fontSize = initialsSize, lineHeight = initialsSize, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(account.email, color = colors.ink, style = type.bodySmallMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (account.syncedText != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
                    Box(Modifier.size(SyncDot).background(colors.success, CircleShape))
                    MixedText(account.syncedText, type.caption, type.tibetanInline14, color = colors.muted)
                }
            }
        }
        Icon(DictIcons.ChevronRight, contentDescription = null, Modifier.size(DictTheme.dimens.iconSmall), tint = colors.muted)
    }
}

/**
 * The signed-out account card: a surface2 avatar with a person icon, [title] and [body], and a small filled
 * [actionLabel] button. Copy comes from the caller (accounts ship behind `Features.ACCOUNTS`).
 */
@Composable
fun SignedOutAccountCard(
    title: String,
    body: String,
    actionLabel: String,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    AccountCardFrame(modifier, onClick = null) {
        Box(
            Modifier
                .size(Avatar)
                .background(colors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DictIcons.Person, contentDescription = null, Modifier.size(DictTheme.dimens.iconLarge), tint = colors.muted)
        }
        Column(Modifier.weight(1f)) {
            MixedText(title, type.bodySmallMedium, type.tibetanInline16, color = colors.ink)
            MixedText(body, type.caption, type.tibetanInline14, color = colors.muted)
        }
        FilledButton(actionLabel, onSignIn, size = ButtonSize.Xs)
    }
}

/** The surface1 card both account cards share; the caller's [modifier] wraps it (padding stays outside). */
@Composable
private fun AccountCardFrame(modifier: Modifier, onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    val dimens = DictTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .background(DictTheme.colors.surface1)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dimens.cardPaddingHorizontal, vertical = dimens.cardPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGap),
        content = content,
    )
}

/**
 * A dashed hint box (radius 12, padding 12×14): 12/1.5 muted text whose Tibetan runs draw in ink Jomolhari 15,
 * e.g. the Wylie tip on Search.
 */
@Composable
fun TipBox(text: String, modifier: Modifier = Modifier) {
    val type = DictTheme.type
    val style = type.caption.copy(lineHeight = type.caption.fontSize * TIP_LINE_RATIO)
    MixedText(
        text,
        style,
        type.tibetanInline15,
        modifier
            .fillMaxWidth()
            .dashedBorder(DictTheme.colors.outline, DictShapes.small)
            .padding(horizontal = TipPaddingHorizontal, vertical = TipPaddingVertical),
        color = DictTheme.colors.muted,
        tibetanColor = DictTheme.colors.ink,
    )
}

private const val SAMPLE_HEADWORD = "བཀྲ་ཤིས།"
private const val SAMPLE_WYLIE = "bkra shis"
private const val SAMPLE_GLOSS_1 = "auspicious, fortunate"
private const val SAMPLE_GLOSS_2 = "good luck, blessing"
private const val SAMPLE_TIBETAN_SENSE = "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།"
private const val NBSP = "\u00A0"

/**
 * A live sample entry drawn at [textSize] and [lineSpacing] (not the app's current settings), for the
 * onboarding step and the text-size sheet: headword 34/1.9, Wylie, numbered glosses 15/1.6 and a Tibetan sense
 * line 21/1.9.
 */
@Composable
fun PreviewCard(textSize: AppTextSize, lineSpacing: Float, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val type = remember(textSize, lineSpacing) { DictTypography(textSize.scale, LineSpacing.clamp(lineSpacing)) }
    val glosses = remember(colors.accent) {
        val number = SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)
        buildAnnotatedString {
            withStyle(number) { append("1") }
            append("$NBSP $SAMPLE_GLOSS_1 $NBSP")
            withStyle(number) { append("2") }
            append("$NBSP $SAMPLE_GLOSS_2")
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .background(colors.surface1)
            .padding(horizontal = PreviewPaddingHorizontal, vertical = PreviewPaddingVertical),
    ) {
        TibetanText(SAMPLE_HEADWORD, style = type.tibetanWotd, color = colors.ink)
        WylieLine(SAMPLE_WYLIE, matchRange = null, Modifier.pullUp(WylieOverlap), style = type.wylie)
        Text(glosses, Modifier.padding(top = DictTheme.dimens.chipGap), colors.ink, style = type.body)
        TibetanText(SAMPLE_TIBETAN_SENSE, Modifier.padding(top = WotdLineGap), style = type.tibetanPreviewSense, color = colors.ink)
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun CardsPreview() {
    ComponentPreview {
        WordOfTheDayCard(PreviewSamples.wotd, {}, {}, {}, {})
        WordOfTheDayCard(PreviewSamples.wotd.copy(wylie = null, hasAudio = false, isSaved = true), {}, {}, {}, {})
        StatsCard("9 words learned this week", "4 due for review in Learn", listOf(true, true, true, true, true, false, false), {})
        StatsCard("2 words learned this week", null, listOf(true, false, true, false, false, false, false), {})
        AccountCard(AccountUi("tenzin.dolma@gmail.com", "TD", "Synced 2 min ago · 42 words"), {})
        SignedOutAccountCard("Not signed in", "Sync saved words across devices", "Sign in", {})
        TipBox(stringResource(R.string.wylie_tip))
        PreviewCard(AppTextSize.DEFAULT, LineSpacing.DEFAULT)
    }
}

@PreviewLightDark
@Composable
private fun PreviewCardSizesPreview() {
    ComponentPreview {
        PreviewCard(AppTextSize.SMALL, LineSpacing.MIN)
        PreviewCard(AppTextSize.LARGEST, LineSpacing.MAX)
    }
}

@ExtremeScalePreview
@Composable
private fun WordOfTheDayCardExtremeScalePreview() {
    ExtremeScale {
        WordOfTheDayCard(PreviewSamples.wotd, {}, {}, {}, {})
    }
}
