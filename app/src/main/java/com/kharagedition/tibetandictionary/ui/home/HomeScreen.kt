package com.kharagedition.tibetandictionary.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.startActivitySafely
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.domain.TimeOfDay
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DirectionSwitcher
import com.kharagedition.tibetandictionary.ui.components.ExtremeScale
import com.kharagedition.tibetandictionary.ui.components.ExtremeScalePreview
import com.kharagedition.tibetandictionary.ui.components.Greeting
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples
import com.kharagedition.tibetandictionary.ui.components.SearchField
import com.kharagedition.tibetandictionary.ui.components.SearchFieldVariant
import com.kharagedition.tibetandictionary.ui.components.StatsCard
import com.kharagedition.tibetandictionary.ui.components.StreakPill
import com.kharagedition.tibetandictionary.ui.components.TextChip
import com.kharagedition.tibetandictionary.ui.components.WordOfTheDayCard
import com.kharagedition.tibetandictionary.ui.model.LearnStatsUi
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import com.kharagedition.tibetandictionary.ui.translate.langName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Home sits 12dp under the status bar (2a). */
private val HomeTop = 12.dp

/** The greeting row is at least 40 high (2a). */
private val GreetingRowHeight = 40.dp

/** "Recent" sits 10 above the chips: 4 here plus the 6 a chip's 48dp touch target adds above its 36dp pill. */
private val RecentLabelGap = 4.dp

/** The translate card (1h): 14 by 16 inside, its tile 40 with the arrows at 22, 14 between the parts. */
private val TranslateCardPadding = 14.dp
private val TranslateCardSide = 16.dp
private val TranslateCardGap = 14.dp
private val TranslateTile = 40.dp
private val TranslateTileIcon = 22.dp
private val TranslateChevron = 18.dp

private const val SCREEN_TAG = "home_screen"

/**
 * Home page of the Search tab (1a/2a): [onOpenSearch] opens the Search route with an optional query, or voice
 * input when `voice` is set; [onOpenEntry] opens an entry; [onOpenTranslate] selects the Translate tab.
 */
@Composable
fun HomeScreen(
    onOpenSearch: (query: String?, voice: Boolean) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenTranslate: () -> Unit,
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Each time Home is shown (also back from another tab, a pushed screen or the background): the day may have moved on.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onEvent(HomeEvent.Shown) }
    HomeEffects(viewModel.effects, onOpenSearch, onOpenEntry, onOpenTranslate)
    HomeContent(state, viewModel::onEvent)
}

/** Performs [effects]: navigation, the share sheet and toasts (copy resolved in the app language). */
@Composable
private fun HomeEffects(
    effects: Flow<HomeEffect>,
    onOpenSearch: (query: String?, voice: Boolean) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenTranslate: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    // Toasts suspend until closed; they outlive the collector's turn but not the screen.
    val toastScope = rememberCoroutineScope()
    val openSearch by rememberUpdatedState(onOpenSearch)
    val openEntry by rememberUpdatedState(onOpenEntry)
    val openTranslate by rememberUpdatedState(onOpenTranslate)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateSearch -> openSearch(effect.query, effect.voice)
                is HomeEffect.NavigateEntry -> openEntry(effect.entryId)
                HomeEffect.NavigateTranslate -> openTranslate()
                is HomeEffect.Share -> context.shareText(effect.text)
                is HomeEffect.Toast -> toastScope.launch {
                    toaster.show(
                        message = effect.message.resolve(resources),
                        action = effect.action?.let(resources::getString),
                        onAction = effect.onAction?.let { action -> suspend { action() } },
                    )
                }
            }
        }
    }
}

private fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivitySafely(Intent.createChooser(send, null))
}

/**
 * The stateless Home page (1a/2a), scrolling as a whole: the greeting with the streak pill, the search field (a
 * button that opens Search) with its mic, the direction switcher, the word-of-the-day card, the translate card, the
 * recent-search chips and, with Learn, the stats card. Each part shows only when [state] has something for it.
 *
 * Test tags: `home_screen`, `home_search_field`, `home_streak`, `home_wotd`, `home_translate_card`,
 * `home_recent_{index}`, `home_stats`.
 * The mic, the switcher segments and the card's actions keep their component tags and labels (`search_mic`,
 * `direction_segment_{id}`, `direction_segment_more`; "Play pronunciation", "Save"/"Saved", "Share", "Open").
 */
@Composable
fun HomeContent(state: HomeUiState, onEvent: (HomeEvent) -> Unit, modifier: Modifier = Modifier) {
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .testTag(SCREEN_TAG)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(start = dimens.screenPadding, top = HomeTop, end = dimens.screenPadding, bottom = dimens.sectionGap),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        GreetingRow(state.greeting, state.visibleStreak)
        SearchBlock(state, onEvent)
        state.wotd?.let { wotd ->
            WordOfTheDayCard(
                wotd,
                onPlay = { onEvent(HomeEvent.WotdPlay) },
                onToggleSave = { onEvent(HomeEvent.WotdToggleSave) },
                onShare = { onEvent(HomeEvent.WotdShare) },
                onOpen = { onEvent(HomeEvent.WotdOpen) },
                modifier = Modifier.testTag("home_wotd"),
            )
        }
        state.translateCard?.let { card -> TranslateCard(card, onEvent) }
        if (state.recents.isNotEmpty()) RecentSection(state.recents, onEvent)
        state.visibleStats?.let { stats -> LearnStatsCard(stats, onEvent) }
    }
}

/** The greeting for the time of day, and the streak pill at the end when there is a [streak] to show. */
@Composable
private fun GreetingRow(greeting: TimeOfDay, streak: Int?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = GreetingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.blockGap),
    ) {
        Greeting(greeting, Modifier.weight(1f))
        if (streak != null) StreakPill(streak, Modifier.testTag("home_streak"))
    }
}

/** The Home search field (tap opens Search; the mic opens voice search) over the direction switcher. */
@Composable
private fun SearchBlock(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DictTheme.dimens.blockGap)) {
        SearchField(
            value = "",
            onValueChange = {},
            placeholder = stringResource(R.string.search_placeholder),
            modifier = Modifier.testTag("home_search_field"),
            variant = SearchFieldVariant.Home,
            showMic = state.micAvailable,
            onMic = { onEvent(HomeEvent.VoiceSearch) },
            onClick = { onEvent(HomeEvent.OpenSearch) },
        )
        state.selectedDirection?.let { selected ->
            DirectionSwitcher(
                slots = state.slots,
                selected = selected,
                onSelect = { onEvent(HomeEvent.SelectDirection(it.id)) },
            )
        }
    }
}

/**
 * "Translate a sentence" over "Tibetan ⇄ English · 7 left today" (the count left out until there is a user), in a
 * bordered row with the translate tile and a chevron (1h); a tap opens the Translate tab.
 */
@Composable
private fun TranslateCard(card: TranslateCardUi, onEvent: (HomeEvent) -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val packLanguage = if (card.pair.source == Lang.EN) card.pair.target else card.pair.source
    val languages = stringResource(R.string.home_translate_languages, langName(packLanguage), langName(Lang.EN))
    val line = when {
        card.unlimited -> stringResource(R.string.home_translate_unlimited, languages)
        card.remaining != null -> stringResource(
            R.string.home_translate_left_today,
            languages,
            TibetanNumerals.formatCount(card.remaining, DictTheme.language),
        )
        else -> languages
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DictShapes.medium)
            .border(1.dp, colors.cardBorder, DictShapes.medium)
            .clickable(role = Role.Button) { onEvent(HomeEvent.OpenTranslate) }
            .padding(horizontal = TranslateCardSide, vertical = TranslateCardPadding)
            .testTag("home_translate_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TranslateCardGap),
    ) {
        Box(
            Modifier
                .size(TranslateTile)
                .background(colors.accentContainer, DictShapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DictIcons.Translate, contentDescription = null, Modifier.size(TranslateTileIcon), tint = colors.accent)
        }
        Column(Modifier.weight(1f)) {
            val title = stringResource(R.string.home_translate_title)
            MixedText(title, type.button, type.tibetanInline16, color = colors.ink)
            MixedText(line, type.caption, type.tibetanInline14, color = colors.muted)
        }
        Icon(DictIcons.ChevronRight, contentDescription = null, Modifier.size(TranslateChevron), tint = colors.muted)
    }
}

/** "Recent" over one row of query chips that scrolls sideways; a chip searches its query again. */
@Composable
private fun RecentSection(recents: List<RecentChipUi>, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RecentLabelGap)) {
        MixedText(
            stringResource(R.string.recent),
            DictTheme.type.sectionLabel,
            DictTheme.type.tibetanInline14,
            color = DictTheme.colors.muted,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DictTheme.dimens.chipGap),
        ) {
            recents.forEachIndexed { index, recent ->
                TextChip(
                    recent.query,
                    onClick = { onEvent(HomeEvent.RecentTap(recent.query)) },
                    modifier = Modifier.testTag("home_recent_$index"),
                )
            }
        }
    }
}

/** Learn progress: "{n} words learned this week", "{n} due for review in Learn" (when known) and the week's dots. */
@Composable
private fun LearnStatsCard(stats: LearnStatsUi, onEvent: (HomeEvent) -> Unit) {
    val language = DictTheme.language
    StatsCard(
        title = pluralStringResource(
            R.plurals.learned_this_week,
            stats.learnedThisWeek,
            TibetanNumerals.formatCount(stats.learnedThisWeek, language),
        ),
        subtitle = stats.dueCount?.let { due ->
            pluralStringResource(R.plurals.due_for_review, due, TibetanNumerals.formatCount(due, language))
        },
        weekFlags = stats.weekFlags,
        onClick = { onEvent(HomeEvent.StatsTap) },
        modifier = Modifier.testTag("home_stats"),
    )
}

/** Preview states built from the component samples (also the Search tab in `MainScaffold`'s preview). */
internal object HomeSamples {
    private val s = PreviewSamples

    /** Only what Home always has: no streak, word of the day, recents or mic. */
    val bare = HomeUiState(greeting = TimeOfDay.MORNING, slots = s.threeSlots, selectedDirection = s.boEn)

    val full = bare.copy(
        greeting = TimeOfDay.AFTERNOON,
        streakDays = 12,
        wotd = s.wotd,
        recents = listOf(
            RecentChipUi("སྙིང་རྗེ།"),
            RecentChipUi("compassion"),
            RecentChipUi("ཤེས་རབ།"),
            RecentChipUi("bde ba"),
        ),
        micAvailable = true,
        translateCard = TranslateCardUi(LangPair(Lang.BO, Lang.EN), remaining = 7, limit = 10),
    )
}

@PreviewLightDark
@BoPreview
@Composable
private fun HomePreview() {
    ComponentPreview(edgeToEdge = true) { HomeContent(HomeSamples.full, onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun HomeBarePreview() {
    ComponentPreview(edgeToEdge = true) { HomeContent(HomeSamples.bare, onEvent = {}) }
}

@ExtremeScalePreview
@Composable
private fun HomeExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) { HomeContent(HomeSamples.full, onEvent = {}) }
}
