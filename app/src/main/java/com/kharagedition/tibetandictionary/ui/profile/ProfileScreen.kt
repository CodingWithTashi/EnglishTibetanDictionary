package com.kharagedition.tibetandictionary.ui.profile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.startActivitySafely
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.AppTextSize
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.nameRes
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.ui.components.AccountCard
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DirectionLabel
import com.kharagedition.tibetandictionary.ui.components.ExtremeScale
import com.kharagedition.tibetandictionary.ui.components.ExtremeScalePreview
import com.kharagedition.tibetandictionary.ui.components.LatinText
import com.kharagedition.tibetandictionary.ui.components.LocalToaster
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.PreviewSamples
import com.kharagedition.tibetandictionary.ui.components.ScreenTitle
import com.kharagedition.tibetandictionary.ui.components.SegmentedControl3
import com.kharagedition.tibetandictionary.ui.components.SettingsGroup
import com.kharagedition.tibetandictionary.ui.components.SettingsRow
import com.kharagedition.tibetandictionary.ui.components.SettingsTrailing
import com.kharagedition.tibetandictionary.ui.components.SignedOutAccountCard
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.components.extendStart
import com.kharagedition.tibetandictionary.ui.components.lineSpacingLabel
import com.kharagedition.tibetandictionary.ui.components.textSizeLabel
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Screen padding above the title and below the version line; gap between the screen's blocks. */
private val ScreenTop = 16.dp
private val ScreenBottom = 24.dp
private val BlockGap = 16.dp

/** Reading sample box under the text-size row: 8×16 padding, 12 between the Tibetan word and its gloss. */
private val SamplePaddingVertical = 8.dp
private val SamplePaddingHorizontal = 16.dp
private val SampleGap = 12.dp

/** Footer links: 8dp each side (16 between labels); the row is pulled out by the same so the labels align. */
private val LinkSide = 8.dp

/** Reading sample (a size sample, not copy): a word and its gloss drawn at the current text settings. */
private const val SAMPLE_HEADWORD = "བཀྲ་ཤིས།"
private const val SAMPLE_GLOSS = "auspicious"

/** Joins the text-size name and the line spacing in the text-size row's value ("Default · 1.9"). */
private const val VALUE_SEPARATOR = " · "

private const val MINUTES_PER_HOUR = 60

/** Theme segments, in display order. */
private val ThemeOrder = listOf(Appearance.LIGHT, Appearance.DARK, Appearance.SYSTEM)

internal const val PROFILE_SCREEN_TAG = "profile_screen"

/** The Profile tab (2f/3d): its settings and footer links. [onOpenAbout] opens About. */
@Composable
fun ProfileScreen(onOpenAbout: () -> Unit) {
    val viewModel = hiltViewModel<ProfileViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Each time the tab is shown (also back from the background or recreated): the app language may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onEvent(ProfileEvent.Shown) }
    ProfileEffects(viewModel.effects, onOpenAbout)
    val loaded = state
    if (loaded != null) {
        ProfileScreen(loaded, viewModel::onEvent)
    } else {
        // Prefs and capabilities arrive within a frame; show the background instead of default values.
        Box(
            Modifier
                .fillMaxSize()
                .background(DictTheme.colors.background),
        )
    }
}

/** Performs [effects]: About, external links, the share sheet and toasts (worded in the UI language). */
@Composable
private fun ProfileEffects(effects: Flow<ProfileEffect>, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    // Toasts suspend until closed; they outlive the collector's turn but not the screen.
    val toastScope = rememberCoroutineScope()
    val openAbout by rememberUpdatedState(onOpenAbout)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ProfileEffect.NavigateAbout -> openAbout()
                is ProfileEffect.OpenUrl -> context.startActivitySafely(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                is ProfileEffect.ShareApp ->
                    context.shareText(
                        resources.getString(
                            R.string.share_app_text,
                            resources.getString(effect.pack.nameRes()),
                            effect.listingUrl,
                        ),
                    )
                is ProfileEffect.Toast -> toastScope.launch { toaster.show(effect.message.resolve(resources)) }
            }
        }
    }
}

private fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivitySafely(Intent.createChooser(send, null))
}

/**
 * The stateless Profile tab: title, the account card (with accounts), the Reading group, footer links and the
 * version line, with the open [ProfileUiState.sheet] on top.
 *
 * Test tags: `profile_screen`, `profile_row_{text_size|theme|dictionary|direction|language}`,
 * `profile_link_{about|rate|share|more|support|privacy}`.
 */
@Composable
fun ProfileScreen(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit, modifier: Modifier = Modifier) {
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .testTag(PROFILE_SCREEN_TAG)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(start = dimens.screenPadding, top = ScreenTop, end = dimens.screenPadding, bottom = ScreenBottom),
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        ScreenTitle(stringResource(R.string.profile_title), gloss = stringResource(R.string.profile_gloss))
        if (Features.ACCOUNTS) ProfileAccount(state.account, onEvent)
        ReadingGroup(state, onEvent)
        ProfileFooter(state, onEvent)
    }
    ProfileSheets(state, onEvent)
}

@Composable
private fun ProfileAccount(account: AccountUi?, onEvent: (ProfileEvent) -> Unit) {
    if (account != null) {
        AccountCard(account, onClick = { onEvent(ProfileEvent.OpenAccount) })
    } else {
        SignedOutAccountCard(
            title = stringResource(R.string.profile_signed_out_title),
            body = stringResource(R.string.profile_signed_out_body),
            actionLabel = stringResource(R.string.sign_in),
            onSignIn = { onEvent(ProfileEvent.SignIn) },
        )
    }
}

@Composable
private fun ReadingGroup(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    val wotd = state.wotdNotification
    SettingsGroup(stringResource(R.string.reading)) {
        SettingsRow(
            label = stringResource(R.string.text_size_spacing),
            modifier = Modifier.testTag("profile_row_text_size"),
            value = textSizeLabel(state.textSize) + VALUE_SEPARATOR + lineSpacingLabel(state.lineSpacing, DictTheme.language),
            onClick = { onEvent(ProfileEvent.OpenSheet(ProfileSheet.TEXT_SIZE)) },
        ) {
            ReadingSample()
        }
        SettingsRow(
            label = stringResource(R.string.theme),
            modifier = Modifier.testTag("profile_row_theme"),
            valueContent = {
                SegmentedControl3(
                    options = listOf(
                        stringResource(R.string.theme_light),
                        stringResource(R.string.theme_dark),
                        stringResource(R.string.theme_system),
                    ),
                    selectedIndex = ThemeOrder.indexOf(state.appearance),
                    onSelect = { onEvent(ProfileEvent.SetAppearance(ThemeOrder[it])) },
                )
            },
        )
        SettingsRow(
            label = stringResource(R.string.dictionary),
            modifier = Modifier.testTag("profile_row_dictionary"),
            value = stringResource(state.dictionary.nameRes()),
            onClick = { onEvent(ProfileEvent.OpenSheet(ProfileSheet.DICTIONARY)) },
            trailing = SettingsTrailing.Chevron,
        )
        state.defaultDirection?.let { direction ->
            SettingsRow(
                label = stringResource(R.string.default_direction),
                modifier = Modifier.testTag("profile_row_direction"),
                valueContent = { DirectionLabel(direction, color = DictTheme.colors.muted) },
                onClick = { onEvent(ProfileEvent.OpenSheet(ProfileSheet.DIRECTION)) },
                trailing = SettingsTrailing.Chevron,
            )
        }
        SettingsRow(
            label = stringResource(R.string.app_language),
            modifier = Modifier.testTag("profile_row_language"),
            value = languageName(state.language),
            onClick = { onEvent(ProfileEvent.OpenSheet(ProfileSheet.LANGUAGE)) },
            trailing = SettingsTrailing.Chevron,
            divider = wotd != null,
        )
        if (wotd != null) {
            SettingsRow(
                label = stringResource(R.string.wotd_notification),
                subLabel = notificationTime(wotd.minuteOfDay),
                trailing = SettingsTrailing.Switch(wotd.enabled) { onEvent(ProfileEvent.ToggleWotdNotification) },
                divider = false,
            )
        }
    }
}

/** "English" or "བོད་ཡིག": each language is named in its own script. */
@Composable
private fun languageName(language: AppLanguage): String =
    stringResource(if (language == AppLanguage.BO) R.string.language_bo else R.string.language_en)

/** [minuteOfDay] as a short local time ("9:00 AM"), in Tibetan digits for the bo UI. */
@Composable
private fun notificationTime(minuteOfDay: Int): String {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(LocalLocale.current.platformLocale)
        .format(LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR))
    return if (DictTheme.language.usesTibetanDigits) TibetanNumerals.toTibetan(time) else time
}

/** A word and its gloss at the current text size and line spacing, under the text-size row. */
@Composable
private fun ReadingSample() {
    val colors = DictTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DictShapes.small)
            .background(colors.surface1)
            .padding(horizontal = SamplePaddingHorizontal, vertical = SamplePaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(SampleGap),
    ) {
        TibetanText(SAMPLE_HEADWORD, Modifier.alignByBaseline(), style = DictTheme.type.tibetanSample, color = colors.ink)
        LatinText(SAMPLE_GLOSS, Modifier.alignByBaseline(), style = DictTheme.type.bodySmall, color = colors.muted)
    }
}

/** A 1dp rule, the visible footer links and the version line. */
@Composable
private fun ProfileFooter(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(DictTheme.dimens.border)
                .background(colors.divider),
        )
        FlowRow(Modifier.extendStart(LinkSide)) {
            state.links.forEach { link ->
                FooterLink(linkLabel(link), Modifier.testTag("profile_link_" + link.tagSuffix())) {
                    onEvent(ProfileEvent.OpenLink(link))
                }
            }
        }
        MixedText(
            versionLine(stringResource(state.dictionary.nameRes()), state.versionName, state.entryCount),
            type.tiny,
            type.tibetanInline14,
            color = colors.faint,
        )
    }
}

/** A quiet 13 muted footer label with a 48dp touch target. */
@Composable
private fun FooterLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val minTouch = DictTheme.dimens.minTouch
    Box(
        modifier
            .heightIn(min = minTouch)
            .widthIn(min = minTouch)
            .clip(DictShapes.extraSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LinkSide),
        contentAlignment = Alignment.Center,
    ) {
        MixedText(label, DictTheme.type.secondary, DictTheme.type.tibetanInline15, color = DictTheme.colors.muted)
    }
}

@Composable
private fun linkLabel(link: ProfileLink): String = stringResource(
    when (link) {
        ProfileLink.ABOUT -> R.string.about
        ProfileLink.RATE -> R.string.rate
        ProfileLink.SHARE -> R.string.share_app
        ProfileLink.MORE_APPS -> R.string.more_apps
        ProfileLink.SUPPORT -> R.string.support
        ProfileLink.PRIVACY -> R.string.privacy
    },
)

private fun ProfileLink.tagSuffix(): String = when (this) {
    ProfileLink.ABOUT -> "about"
    ProfileLink.RATE -> "rate"
    ProfileLink.SHARE -> "share"
    ProfileLink.MORE_APPS -> "more"
    ProfileLink.SUPPORT -> "support"
    ProfileLink.PRIVACY -> "privacy"
}

/** Preview state: the 2f mockup (signed out, default settings, three directions). */
private val previewState = ProfileUiState(
    defaultDirection = PreviewSamples.boEn,
    directions = PreviewSamples.directions,
    versionName = "3.0.0",
    entryCount = 180_412,
    links = ProfileLinkTargets().visible,
    otherApps = OtherApp.entries,
)

@PreviewLightDark
@BoPreview
@Composable
private fun ProfilePreview() {
    ComponentPreview(edgeToEdge = true) { ProfileScreen(previewState, onEvent = {}) }
}

@PreviewLightDark
@Composable
private fun ProfileLargeTextPreview() {
    ComponentPreview(textScale = AppTextSize.LARGE.scale, edgeToEdge = true) {
        ProfileScreen(previewState.copy(textSize = AppTextSize.LARGE, entryCount = null), onEvent = {})
    }
}

@ExtremeScalePreview
@Composable
private fun ProfileExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        ProfileScreen(previewState.copy(textSize = AppTextSize.LARGEST, lineSpacing = LineSpacing.MAX), onEvent = {})
    }
}
