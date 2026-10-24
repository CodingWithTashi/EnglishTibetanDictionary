package com.kharagedition.tibetandictionary.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.OutlinedButton
import com.kharagedition.tibetandictionary.ui.components.Overline
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.components.dottedUnderline
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.translate.UsagePillUi
import com.kharagedition.tibetandictionary.ui.translate.langName

/** Tap rows (1l, 1q) are 60dp, the 1o cards 64dp; both lead with a 36dp icon tile. */
private val TapRowHeight = 60.dp
private val TapCardHeight = 64.dp
private val IconTile = 36.dp
private val IconTileShape = RoundedCornerShape(10.dp)
private val TileIcon = 20.dp
private val Chevron = 20.dp
private val RowGap = 12.dp
private val CardStart = 14.dp
private val CardEnd = 12.dp
private val CardTop = 14.dp
private val NoteSide = 4.dp
private val SkeletonLong = 16.dp
private val SkeletonShort = 12.dp
private const val SKELETON_LONG_FRACTION = 0.7f
private const val SKELETON_SHORT_FRACTION = 0.42f

/** True for the states drawn as a card that replaces "No match for …" (running, translated, failed). */
internal val SmartTranslateUi.isCard: Boolean
    get() = this is SmartTranslateUi.Running || this is SmartTranslateUi.Result || this is SmartTranslateUi.Failed

/**
 * The quick translation of the settled query (1l–1r): a tap row or the phrase card (Offer), "Translating…", the
 * result with "Open in Translate", the too-long card, the limit rows, or a failure with Retry.
 *
 * Tags: `search_translate_offer`, `search_translate_button`, `search_translate_running`,
 * `search_translate_result`, `search_translate_word_{i}`, `search_translate_open`, `search_translate_too_long`,
 * `search_translate_blocked`, `search_translate_failed`, `search_translate_retry`.
 */
@Composable
internal fun SmartTranslateBlock(
    state: SmartTranslateUi,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SmartTranslateUi.Offer -> if (state.phrase && !state.instead) {
            PhraseOfferCard(state, onEvent, modifier)
        } else {
            OfferRow(state, onEvent, modifier)
        }
        is SmartTranslateUi.Running -> RunningCard(modifier)
        is SmartTranslateUi.Result -> ResultCard(state, onEvent, modifier)
        is SmartTranslateUi.TooLong -> TooLongCard(state, onEvent, modifier)
        is SmartTranslateUi.Blocked -> BlockedRow(state, onEvent, modifier)
        is SmartTranslateUi.Failed -> FailedCard(state, onEvent, modifier)
    }
}

/** "In the dictionary", over a phrase's headword rows. */
@Composable
internal fun PhraseWordsHeader(modifier: Modifier = Modifier) {
    MixedText(
        stringResource(R.string.search_in_dictionary),
        DictTheme.type.sectionLabel,
        DictTheme.type.tibetanInline14,
        modifier,
        color = DictTheme.colors.muted,
    )
}

/** 1l / 1q: "Translate “q” → English" (or "Translate instead") over the usage line, with a chevron. */
@Composable
private fun OfferRow(state: SmartTranslateUi.Offer, onEvent: (SearchEvent) -> Unit, modifier: Modifier) {
    val title = if (state.instead) {
        stringResource(R.string.search_translate_instead)
    } else {
        stringResource(R.string.search_translate_offer, state.query, langName(state.target))
    }
    TapRow("search_translate_offer", title, state.pill, modifier) { onEvent(SearchEvent.TranslateQuery) }
}

/** 1o: a phrase that isn't auto-translated, "Tap to translate → Tibetan" with a Translate button. */
@Composable
private fun PhraseOfferCard(state: SmartTranslateUi.Offer, onEvent: (SearchEvent) -> Unit, modifier: Modifier) {
    val colors = DictTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TapCardHeight)
            .testTag("search_translate_offer")
            .background(colors.surface1, DictShapes.medium)
            .padding(start = CardStart, end = CardEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        IconTile(colors.background)
        TitleAndLine(
            stringResource(R.string.search_translate_tap, langName(state.target)),
            state.pill,
            Modifier.weight(1f),
        )
        FilledButton(
            stringResource(R.string.translate_action),
            { onEvent(SearchEvent.TranslateQuery) },
            Modifier.testTag("search_translate_button"),
            size = ButtonSize.Small,
        )
    }
}

/** 1m: the translation card while the call runs; its bars are hidden from accessibility services. */
@Composable
private fun RunningCard(modifier: Modifier) {
    val colors = DictTheme.colors
    TranslationCard("search_translate_running", stringResource(R.string.translate_loading), modifier) {
        Column(
            Modifier.clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        ) {
            val bars = listOf(SKELETON_LONG_FRACTION to SkeletonLong, SKELETON_SHORT_FRACTION to SkeletonShort)
            bars.forEach { (width, height) ->
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .height(height)
                        .background(colors.surface2, DictShapes.extraSmall),
                )
            }
        }
        Spacer(Modifier.height(CardTop))
    }
}

/**
 * 1n / 1r: the translation (each word with a query searches it), the attribution Google requires, "Open in
 * Translate", and under the card "From cache · not counted" or the usage line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(state: SmartTranslateUi.Result, onEvent: (SearchEvent) -> Unit, modifier: Modifier) {
    val colors = DictTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
        TranslationCard("search_translate_result", attribution(state.text.provider)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap)) {
                state.words.forEachIndexed { index, word -> Word(word, index, onEvent) }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    stringResource(R.string.search_translate_open),
                    { onEvent(SearchEvent.OpenInTranslate) },
                    Modifier.testTag("search_translate_open"),
                    size = ButtonSize.Small,
                    trailing = DictIcons.ChevronRight,
                )
            }
        }
        val pill = state.pill
        when {
            state.text.cached -> Note(stringResource(R.string.translate_from_cache), colors.muted)
            pill != null -> Note(leftToday(pill), if (pill.low) colors.onWarnContainer else colors.muted)
        }
    }
}

/** 1o: over maxChars, with the count and "Open in Translate". */
@Composable
private fun TooLongCard(state: SmartTranslateUi.TooLong, onEvent: (SearchEvent) -> Unit, modifier: Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val language = DictTheme.language
    val count = stringResource(
        R.string.search_translate_characters,
        TibetanNumerals.formatCount(state.length, language),
        TibetanNumerals.formatCount(state.maxChars, language),
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TapCardHeight)
            .testTag("search_translate_too_long")
            .border(DictTheme.dimens.border, colors.cardBorder, DictShapes.medium)
            .padding(start = CardStart, end = CardEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Column(Modifier.weight(1f)) {
            MixedText(
                stringResource(R.string.search_translate_too_long),
                type.button,
                type.tibetanInline16,
                color = colors.ink,
            )
            MixedText(count, type.caption, type.tibetanInline14, color = colors.muted)
        }
        OutlinedButton(
            stringResource(R.string.search_translate_open),
            { onEvent(SearchEvent.OpenInTranslate) },
            Modifier.testTag("search_translate_open"),
            size = ButtonSize.Small,
        )
    }
}

/**
 * No translations left: a guest's "Sign in to translate · 0 of 3 left", "Daily limit reached · Upgrade", or Pro's
 * fair-use limit, which offers nothing.
 */
@Composable
private fun BlockedRow(state: SmartTranslateUi.Blocked, onEvent: (SearchEvent) -> Unit, modifier: Modifier) {
    if (state.plan == Plan.GUEST) {
        TapRow("search_translate_blocked", stringResource(R.string.search_translate_sign_in), state.pill, modifier) {
            onEvent(SearchEvent.SmartSignIn)
        }
    } else if (state.plan == Plan.PRO) {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = TapRowHeight)
                .semantics(mergeDescendants = true) {}
                .testTag("search_translate_blocked"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                MixedText(
                    stringResource(R.string.search_translate_fair_use),
                    DictTheme.type.button,
                    DictTheme.type.tibetanInline16,
                    color = DictTheme.colors.ink,
                )
                MixedText(
                    stringResource(R.string.today_resets),
                    DictTheme.type.caption,
                    DictTheme.type.tibetanInline14,
                    color = DictTheme.colors.muted,
                )
            }
        }
    } else {
        TapRow(
            "search_translate_blocked",
            stringResource(R.string.search_translate_limit),
            line = stringResource(R.string.search_translate_upgrade),
            lineColor = DictTheme.colors.muted,
            modifier = modifier,
        ) { onEvent(SearchEvent.SmartUpgrade) }
    }
}

/** 1u, compact: offline or busy, not counted, with Retry. */
@Composable
private fun FailedCard(state: SmartTranslateUi.Failed, onEvent: (SearchEvent) -> Unit, modifier: Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .testTag("search_translate_failed")
            .background(colors.surface1, DictShapes.medium)
            .padding(horizontal = DictTheme.dimens.cardPaddingHorizontal, vertical = CardTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (state.offline) {
            Icon(
                DictIcons.WifiOff,
                contentDescription = null,
                Modifier.size(DictTheme.dimens.iconMedium),
                tint = colors.muted,
            )
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            val (title, body) = if (state.offline) {
                R.string.translate_offline_title to R.string.translate_offline_body
            } else {
                R.string.translate_busy_title to R.string.translate_busy_body
            }
            MixedText(stringResource(title), type.button, type.tibetanInline16, color = colors.ink)
            MixedText(stringResource(body), type.caption, type.tibetanInline14, color = colors.muted)
        }
        OutlinedButton(
            stringResource(R.string.retry),
            { onEvent(SearchEvent.TranslateQuery) },
            Modifier.testTag("search_translate_retry"),
            size = ButtonSize.Small,
            accentBorder = true,
        )
    }
}

/** A tappable row: icon tile, [title] over the usage line (or [line]), and a chevron. */
@Composable
private fun TapRow(
    tag: String,
    title: String,
    pill: UsagePillUi?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = DictTheme.colors
    TapRow(
        tag,
        title,
        line = pill?.let { leftToday(it) },
        lineColor = if (pill?.low == true) colors.onWarnContainer else colors.muted,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun TapRow(
    tag: String,
    title: String,
    line: String?,
    lineColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TapRowHeight)
            .clip(DictShapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        IconTile(colors.surface1)
        Column(Modifier.weight(1f)) {
            MixedText(title, type.button, type.tibetanInline16, color = colors.ink)
            if (line != null) MixedText(line, type.caption, type.tibetanInline14, color = lineColor)
        }
        Icon(DictIcons.ChevronRight, contentDescription = null, Modifier.size(Chevron), tint = colors.faint)
    }
}

/** [title] over the usage line, amber from 3 left. */
@Composable
private fun TitleAndLine(title: String, pill: UsagePillUi?, modifier: Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column(modifier) {
        MixedText(title, type.button, type.tibetanInline16, color = colors.ink)
        if (pill != null) {
            MixedText(
                leftToday(pill),
                type.caption,
                type.tibetanInline14,
                color = if (pill.low) colors.onWarnContainer else colors.muted,
            )
        }
    }
}

/** The surface1 card of 1m/1n: "TRANSLATION" and [end] on top, then [content]. */
@Composable
private fun TranslationCard(
    tag: String,
    end: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val side = DictTheme.dimens.cardPaddingHorizontal
    Column(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .background(colors.surface1, DictShapes.medium)
            .padding(start = side, top = CardTop, end = side),
        verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(
                stringResource(R.string.search_translate_heading),
                Modifier.weight(1f),
                type.overlineWotd,
                colors.accent,
            )
            MixedText(end, type.tiny, type.tibetanBadge, color = colors.muted)
        }
        content()
    }
}

/** An output word: dotted-underlined and tappable (searches it) when it has a query. */
@Composable
private fun Word(word: OutputWord, index: Int, onEvent: (SearchEvent) -> Unit) {
    val type = DictTheme.type
    val query = word.query
    Text(
        word.text,
        Modifier
            .testTag("search_translate_word_$index")
            .then(
                if (query != null) {
                    // The dots go outside the clip, which would cut the first and last.
                    Modifier
                        .dottedUnderline(DictTheme.colors.faint)
                        .clip(DictShapes.extraSmall)
                        .clickable(role = Role.Button) { onEvent(SearchEvent.SmartWordTap(word)) }
                } else {
                    Modifier
                },
            ),
        color = DictTheme.colors.ink,
        style = if (TibetanScript.hasTibetan(word.text)) type.tibetanExample else type.fieldText,
    )
}

@Composable
private fun IconTile(background: Color) {
    Box(
        Modifier
            .size(IconTile)
            .background(background, IconTileShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DictIcons.Translate, contentDescription = null, Modifier.size(TileIcon), tint = DictTheme.colors.accent)
    }
}

@Composable
private fun Note(text: String, color: Color) {
    MixedText(
        text,
        DictTheme.type.caption,
        DictTheme.type.tibetanInline14,
        Modifier.padding(horizontal = NoteSide),
        color = color,
    )
}

/** "7 of 10 left today", in the app language's numerals; "Unlimited today" on Pro. */
@Composable
private fun leftToday(pill: UsagePillUi): String {
    if (pill.unlimited) return stringResource(R.string.today_unlimited)
    val language = DictTheme.language
    return stringResource(
        R.string.search_translate_left_today,
        TibetanNumerals.formatCount(pill.remaining, language),
        TibetanNumerals.formatCount(pill.limit, language),
    )
}

@Composable
private fun attribution(provider: Provider): String = stringResource(
    when (provider) {
        Provider.GOOGLE -> R.string.translate_by_google
        Provider.AZURE -> R.string.translate_by_microsoft
        Provider.OTHER -> R.string.translate_machine
    },
)
