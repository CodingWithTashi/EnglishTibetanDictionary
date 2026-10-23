package com.kharagedition.tibetandictionary.ui.translate

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.resources
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.DictIconButton
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.Overline
import com.kharagedition.tibetandictionary.ui.components.PRO_MAX_CHARS
import com.kharagedition.tibetandictionary.ui.components.ScreenTitle
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.components.TextChip
import com.kharagedition.tibetandictionary.ui.components.TibetanChip
import com.kharagedition.tibetandictionary.ui.components.dottedUnderline
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import kotlinx.coroutines.launch

private val PillHeight = 30.dp
private val PillPadding = 12.dp
private val BarHeight = 48.dp
private val CardTop = 14.dp
private val CardBottom = 8.dp
private val InputMinHeight = 104.dp
private val OverRing = 1.5.dp
private val ProgressHeight = 3.dp
private val ProgressGap = 4.dp
private val WordGap = 6.dp
private val SkeletonBarWidths = listOf(260.dp, 220.dp, 140.dp)
private val SkeletonBarHeight = 14.dp
private const val SKELETON_PULSE_MS = 900
private const val SKELETON_DIM = 0.4f
private const val HALF_TURN = 180f

/** How many times the swap button has turned half a circle, for tests. */
internal val SwapTurns = SemanticsPropertyKey<Int>("SwapTurns")
private val RecentRowHeight = 56.dp
private val LabelGap = 8.dp
private val HintPadding = 12.dp

/** The title with its gloss, and the usage pill at the end once the quota is known (tap opens Today). */
@Composable
internal fun TranslateHeader(pill: UsagePillUi?, plan: Plan?, onOpenToday: () -> Unit) {
    ScreenTitle(
        stringResource(R.string.translate_title),
        gloss = stringResource(R.string.translate_gloss),
        trailing = pill?.let { { UsagePill(it, plan, onOpenToday) } },
    )
}

/** "7 of 10 left" (amber from 3 left, tag `translate_pill_low`), "Limit reached" at 0, "Pro" on Pro. */
@Composable
private fun UsagePill(pill: UsagePillUi, plan: Plan?, onClick: () -> Unit) {
    val colors = DictTheme.colors
    val language = DictTheme.language
    val pro = plan == Plan.PRO
    val (background, content) = when {
        pro -> colors.accentContainer to colors.onAccentContainer
        pill.low -> colors.warnContainer to colors.onWarnContainer
        else -> colors.surface1 to colors.muted
    }
    val text = when {
        pro -> stringResource(R.string.translate_pro_pill)
        pill.remaining == 0 -> stringResource(R.string.translate_limit_reached)
        else -> stringResource(
            R.string.translate_left,
            TibetanNumerals.formatCount(pill.remaining, language),
            TibetanNumerals.formatCount(pill.limit, language),
        )
    }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = PillHeight)
            .clip(DictShapes.pill)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(if (pill.low && !pro) "translate_pill_low" else "translate_pill")
            .padding(horizontal = PillPadding),
        contentAlignment = Alignment.Center,
    ) {
        MixedText(text, DictTheme.type.sectionLabel, DictTheme.type.tibetanInline14, color = content, maxLines = 1)
    }
}

/**
 * Source ⇄ target on a surface1 track; the swap is off when the other direction isn't offered, and turns half a
 * circle with each tap.
 */
@Composable
internal fun LanguageBar(pair: LangPair, canSwap: Boolean, onSwap: () -> Unit) {
    val colors = DictTheme.colors
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(turns * HALF_TURN, label = "swap")
    Row(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(colors.surface1, DictShapes.pill)
            .padding(DictTheme.dimens.switcherPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageSegment(langName(pair.source), Modifier.weight(1f))
        DictIconButton(
            DictIcons.Active.Translate,
            stringResource(R.string.translate_swap),
            {
                turns++
                onSwap()
            },
            Modifier
                .testTag("translate_swap")
                .semantics { this[SwapTurns] = turns }
                .graphicsLayer { rotationZ = rotation },
            size = DictTheme.dimens.iconButtonMedium,
            iconSize = DictTheme.dimens.iconSmall,
            tint = colors.accent,
            enabled = canSwap,
        )
        LanguageSegment(langName(pair.target), Modifier.weight(1f))
    }
}

@Composable
private fun LanguageSegment(name: String, modifier: Modifier) {
    val colors = DictTheme.colors
    Box(
        modifier
            .height(DictTheme.dimens.switcher)
            .background(if (colors.isDark) colors.surface2 else colors.background, DictShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        MixedText(name, DictTheme.type.button, DictTheme.type.tibetanInline16, color = colors.ink, maxLines = 1)
    }
}

/** A language's name in the UI language ("Tibetan", "Dzongkha", "English"). */
@Composable
internal fun langName(lang: Lang): String = stringResource(
    when (lang) {
        Lang.BO -> DictionaryPack.TIBETAN.resources.language
        Lang.DZ -> DictionaryPack.DZONGKHA.resources.language
        Lang.EN -> R.string.translate_lang_english
    },
)

/**
 * The input card: the text (never cut, however long), Paste and Clear, and the counter "n / max" (amber from 85%,
 * red with a red ring above max, with a bar under it once it isn't normal).
 */
@Composable
internal fun InputCard(state: TranslateUiState, source: Lang, onEvent: (TranslateEvent) -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val dimens = DictTheme.dimens
    val tibetan = source.usesTibetanScript
    val textStyle = (if (tibetan) type.tibetanExample else type.fieldText).copy(color = colors.ink)
    val over = state.counter == CounterLevel.OVER
    val placeholder = if (tibetan) R.string.translate_placeholder_tibetan else R.string.translate_placeholder_english
    val counterColor = when (state.counter) {
        CounterLevel.NORMAL -> colors.muted
        CounterLevel.NEAR -> colors.warn
        CounterLevel.OVER -> colors.danger
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DictShapes.large)
            .background(colors.surface1)
            .then(if (over) Modifier.border(OverRing, colors.danger, DictShapes.large) else Modifier)
            .padding(horizontal = dimens.cardPaddingHorizontal)
            .padding(top = CardTop, bottom = CardBottom),
    ) {
        BasicTextField(
            value = state.input,
            onValueChange = { onEvent(TranslateEvent.InputChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InputMinHeight)
                .testTag("translate_input"),
            enabled = state.inputEnabled,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { field ->
                Box {
                    if (state.input.text.isEmpty()) {
                        MixedText(
                            stringResource(placeholder),
                            type.fieldText,
                            type.tibetanExample,
                            color = colors.muted,
                        )
                    }
                    field()
                }
            },
        )
        InputActions(state, counterColor, onEvent)
        if (state.counter != CounterLevel.NORMAL) {
            val fraction = (state.length.toFloat() / state.maxChars.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(bottom = ProgressGap)
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .background(colors.surface2, DictShapes.pill),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(ProgressHeight)
                        .background(counterColor, DictShapes.pill),
                )
            }
        }
    }
}

/** Paste (from the clipboard, at the cursor), Clear when there is text, and the counter. */
@Composable
private fun InputActions(state: TranslateUiState, counterColor: Color, onEvent: (TranslateEvent) -> Unit) {
    val colors = DictTheme.colors
    val dimens = DictTheme.dimens
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = DictTheme.language
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DictIconButton(
            DictIcons.Paste,
            stringResource(R.string.translate_paste),
            onClick = {
                scope.launch {
                    val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 } ?: return@launch
                    val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                    if (text.isNotEmpty()) onEvent(TranslateEvent.Paste(text))
                }
            },
            modifier = Modifier.testTag("translate_paste"),
            size = dimens.iconButtonMedium,
            iconSize = dimens.iconSmall,
            tint = colors.muted,
            enabled = state.inputEnabled,
        )
        if (state.input.text.isNotEmpty()) {
            DictIconButton(
                DictIcons.Close,
                stringResource(R.string.clear),
                onClick = { onEvent(TranslateEvent.Clear) },
                modifier = Modifier.testTag("translate_clear"),
                size = dimens.iconButtonMedium,
                iconSize = dimens.iconSmall,
                tint = colors.muted,
                enabled = state.inputEnabled,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(
                R.string.translate_counter,
                TibetanNumerals.formatCount(state.length, language),
                TibetanNumerals.formatCount(state.maxChars, language),
            ),
            Modifier.testTag("translate_counter"),
            color = counterColor,
            style = DictTheme.type.captionMedium,
        )
    }
}

/** "Type in Tibetan script — Wylie isn't translated", or "This looks like Tibetan" with [Switch]. */
@Composable
internal fun HintRow(hint: InlineHint, onSwitch: () -> Unit) {
    val colors = DictTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("translate_hint")
            .semantics(mergeDescendants = true) {}
            .background(colors.warnContainer, DictShapes.small)
            .padding(horizontal = HintPadding, vertical = LabelGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MixedText(
            stringResource(
                when (hint) {
                    InlineHint.NOT_TIBETAN_SCRIPT -> R.string.translate_hint_wylie
                    InlineHint.LOOKS_TIBETAN -> R.string.translate_hint_looks_tibetan
                },
            ),
            DictTheme.type.secondary,
            DictTheme.type.tibetanInline14,
            Modifier.weight(1f),
            color = colors.onWarnContainer,
        )
        if (hint == InlineHint.LOOKS_TIBETAN) {
            TextButton(
                stringResource(R.string.translate_switch),
                onSwitch,
                Modifier.testTag("translate_switch_direction"),
                size = ButtonSize.Xs,
            )
        }
    }
}

/** "Translate", or "Translating…" while a call runs. */
@Composable
internal fun TranslateButton(state: TranslateUiState, onTranslate: () -> Unit) {
    val loading = state.phase == TranslatePhase.LOADING
    FilledButton(
        stringResource(if (loading) R.string.translate_loading else R.string.translate_action),
        onTranslate,
        Modifier
            .fillMaxWidth()
            .testTag("translate_button"),
        size = ButtonSize.Cta,
        enabled = state.canTranslate,
    )
}

/** 1s: "Need longer text? Pro allows 500 characters." (free plan, near the limit); opens the Pro sheet. */
@Composable
internal fun ProHint(onClick: () -> Unit) {
    MixedText(
        stringResource(R.string.translate_pro_hint, TibetanNumerals.formatCount(PRO_MAX_CHARS, DictTheme.language)),
        DictTheme.type.caption,
        DictTheme.type.tibetanInline14,
        Modifier
            .fillMaxWidth()
            .clip(DictShapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("translate_pro_hint")
            .padding(vertical = LabelGap),
        color = DictTheme.colors.muted,
        textAlign = TextAlign.Center,
    )
}

/** Pulsing placeholder bars where the result will be, while a call runs; hidden from accessibility services. */
@Composable
internal fun TranslateSkeleton() {
    val colors = DictTheme.colors
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = SKELETON_DIM,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.testTag("translate_skeleton")) {
        Column(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
                .background(colors.surface1, DictShapes.large)
                .padding(horizontal = DictTheme.dimens.cardPaddingHorizontal, vertical = CardTop + 4.dp),
            verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.blockGap),
        ) {
            SkeletonBarWidths.forEach { width ->
                Box(
                    Modifier
                        .widthIn(max = width)
                        .fillMaxWidth()
                        .height(SkeletonBarHeight)
                        .graphicsLayer { alpha = pulse.value }
                        .background(colors.surface2, DictShapes.extraSmall),
                )
            }
        }
    }
}

/**
 * The result: the target language, "From cache · not counted" on a cache hit, the words (each with a query opens
 * Search; line breaks kept), Copy, Share, Save and the attribution Google requires.
 */
@Composable
internal fun ResultCard(result: TranslationResultUi, target: Lang, onEvent: (TranslateEvent) -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val dimens = DictTheme.dimens
    val lines = remember(result.text, result.words, target) { wordLines(result.text, result.words, target) }
    val wordStyle = if (target.usesTibetanScript) type.tibetanExample else type.fieldText
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("translate_result")
            .background(colors.surface1, DictShapes.large)
            .padding(start = dimens.cardPaddingHorizontal, top = CardTop, end = dimens.cardPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(WordGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(langName(target), Modifier.weight(1f), style = type.overlineWotd, color = colors.accent)
            if (result.cached) {
                MixedText(
                    stringResource(R.string.translate_from_cache),
                    type.tiny,
                    type.tibetanBadge,
                    color = colors.muted,
                )
            }
        }
        Column {
            lines.forEach { line ->
                if (line.isEmpty()) {
                    Spacer(Modifier.height(LabelGap))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(WordGap)) {
                        line.forEach { (index, word) -> Word(word, index, wordStyle, onEvent) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ResultAction(DictIcons.Copy, stringResource(R.string.copy), "translate_copy") {
                onEvent(TranslateEvent.Copy)
            }
            ResultAction(DictIcons.Share, stringResource(R.string.share), "translate_share") {
                onEvent(TranslateEvent.Share)
            }
            ResultAction(
                if (result.saved) DictIcons.HeartFilled else DictIcons.Heart,
                stringResource(if (result.saved) R.string.unsave else R.string.save),
                "translate_save",
                tint = if (result.saved) colors.accent else colors.muted,
            ) { onEvent(TranslateEvent.ToggleSave) }
            Spacer(Modifier.weight(1f))
            MixedText(
                stringResource(
                    when (result.provider) {
                        Provider.GOOGLE -> R.string.translate_by_google
                        Provider.AZURE -> R.string.translate_by_microsoft
                        Provider.OTHER -> R.string.translate_machine
                    },
                ),
                type.tiny,
                type.tibetanBadge,
                color = colors.muted,
            )
        }
    }
}

/** One output word: dotted-underlined and tappable (opens Search) when it has a query. */
@Composable
private fun Word(word: OutputWord, index: Int, style: TextStyle, onEvent: (TranslateEvent) -> Unit) {
    val query = word.query
    Text(
        word.text,
        Modifier
            .testTag("translate_word_$index")
            .then(
                if (query != null) {
                    // The dots go outside the clip, which would cut the first and last.
                    Modifier
                        .dottedUnderline(DictTheme.colors.faint)
                        .clip(DictShapes.extraSmall)
                        .clickable(role = Role.Button) { onEvent(TranslateEvent.WordTap(word)) }
                } else {
                    Modifier
                },
            ),
        color = DictTheme.colors.ink,
        style = style,
    )
}

@Composable
private fun ResultAction(
    icon: ImageVector,
    label: String,
    tag: String,
    tint: Color = DictTheme.colors.muted,
    onClick: () -> Unit,
) {
    DictIconButton(
        icon,
        label,
        onClick,
        Modifier.testTag(tag),
        size = DictTheme.dimens.iconButtonMedium,
        iconSize = DictTheme.dimens.iconSmall,
        tint = tint,
    )
}

/**
 * [words] (from the whole [text], where a line break separates like a space) grouped by the lines of [text], each
 * with its index in [words]; an empty line is an empty group.
 */
internal fun wordLines(text: String, words: List<OutputWord>, target: Lang): List<List<IndexedValue<OutputWord>>> {
    var next = 0
    return text.split('\n').map { line ->
        val count = TranslationText.words(line, target).size
        val end = (next + count).coerceAtMost(words.size)
        (next until end).map { IndexedValue(it, words[it]) }.also { next = end }
    }
}

/** "Try" and the pack's sample chips; a chip fills the input. */
@Composable
internal fun SampleChips(samples: List<String>, onTap: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        SectionLabel(stringResource(R.string.translate_try))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
            verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        ) {
            samples.forEachIndexed { index, sample ->
                val modifier = Modifier.testTag("translate_sample_$index")
                if (TibetanScript.hasTibetan(sample)) {
                    TibetanChip(sample, onClick = { onTap(sample) }, modifier = modifier)
                } else {
                    TextChip(sample, onClick = { onTap(sample) }, modifier = modifier)
                }
            }
        }
    }
}

/** "Recent" with Clear, then one row per recent translation; a row restores it with no call. */
@Composable
internal fun RecentList(recents: List<RecentTranslationUi>, onTap: (Long) -> Unit, onClear: () -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.translate_recent), Modifier.weight(1f))
            TextButton(
                stringResource(R.string.clear),
                onClear,
                Modifier.testTag("translate_clear_recents"),
                size = ButtonSize.Xs,
            )
        }
        recents.forEach { recent ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RecentRowHeight)
                    .clickable(role = Role.Button) { onTap(recent.id) }
                    .testTag("translate_recent_${recent.id}"),
                verticalArrangement = Arrangement.Center,
            ) {
                MixedText(
                    recent.source,
                    type.body,
                    type.tibetanInline16,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MixedText(
                    recent.translation,
                    type.secondary,
                    type.tibetanInline14,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = colors.divider)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val type = DictTheme.type
    MixedText(text, type.sectionLabel, type.tibetanInline14, modifier, color = DictTheme.colors.muted)
}
