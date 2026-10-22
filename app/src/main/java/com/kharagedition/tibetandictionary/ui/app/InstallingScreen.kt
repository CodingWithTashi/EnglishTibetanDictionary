package com.kharagedition.tibetandictionary.ui.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.resources
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.FlagStrand
import com.kharagedition.tibetandictionary.ui.components.FlagStrandSize
import com.kharagedition.tibetandictionary.ui.components.FullEmptyState
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.ProgressBar
import com.kharagedition.tibetandictionary.ui.components.TextButton
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.preview.DictPreview
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Test tag of the installing screen's root. */
const val INSTALLING_SCREEN_TAG = "installing_screen"

// Splash 3a measurements.
/** Size of ic_splash_tile: the 84dp icon tile centred in a frame that leaves room for its drop shadow. */
private val SplashTileFrame = 136.dp
private val TibetanNameTop = 22.dp
private val LatinNameOverlap = (-4).dp
private val StatusBottom = 40.dp
private val StatusGap = 10.dp
private val ProgressWidth = 120.dp
private val LatinNameTracking = 0.02.em

/**
 * First-launch install (3a) of [AppRootState.Installing.pack]: see [InstallingContent]. The entry count comes from
 * [state], else from [bundledEntryCount].
 *
 * @param bundledEntryCount the entry count the data build recorded for the pack's bundled asset (0 = unknown).
 */
@Composable
fun InstallingScreen(
    state: AppRootState.Installing,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bundledEntryCount: Int = integerResource(state.pack.resources.entryCount),
) {
    InstallingContent(
        pack = state.pack,
        title = stringResource(R.string.installing_dictionary),
        // Before the installer knows the count, use the one the data build recorded (0 = unknown).
        totalEntries = state.totalEntries ?: bundledEntryCount.takeIf { it > 0 },
        progress = state.progress,
        failure = state.failure,
        onRetry = onRetry,
        modifier = modifier.testTag(INSTALLING_SCREEN_TAG),
    )
}

/**
 * The install body (3a) for [pack]: the prayer-flag strand at the top, the pack's icon tile with its Tibetan-script
 * and Latin names in the middle, and a 120dp progress bar over the status line at the bottom: "Preparing N entries ·
 * offline" when [totalEntries] is known, else [title]. A non-null [failure] replaces all of it with a full empty
 * state with "Try again" ([onRetry]) and, when [secondaryActionLabel] is given, a quiet button under it calling
 * [onSecondaryAction].
 */
@Composable
fun InstallingContent(
    pack: DictionaryPack,
    title: String,
    progress: Float?,
    totalEntries: Int?,
    failure: Throwable?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .systemBarsPadding(),
    ) {
        if (failure != null) {
            InstallFailed(failure, onRetry, secondaryActionLabel, onSecondaryAction, Modifier.align(Alignment.Center))
        } else {
            FlagStrand(FlagStrandSize.Splash, Modifier.align(Alignment.TopCenter))
            PackNames(pack, Modifier.align(Alignment.Center))
            InstallProgress(title, progress, totalEntries, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PackNames(pack: DictionaryPack, modifier: Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val resources = pack.resources
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The pack's tile, as the system splash draws it. Its drawable includes the shadow, so it is larger than the
        // icon slot and overflows it evenly on every side (requiredSize centres it on the slot).
        Image(
            painterResource(resources.tile),
            contentDescription = null,
            modifier = Modifier
                .size(DictTheme.dimens.splashIcon)
                .requiredSize(SplashTileFrame),
        )
        TibetanText(
            stringResource(resources.title),
            Modifier.padding(top = TibetanNameTop),
            style = type.tibetanSplash,
            color = colors.ink,
        )
        MixedText(
            stringResource(resources.titleLatin),
            type.buttonLarge.copy(letterSpacing = LatinNameTracking),
            type.tibetanInline16,
            Modifier.offset(y = LatinNameOverlap),
            color = colors.ink,
        )
    }
}

@Composable
private fun InstallProgress(title: String, progress: Float?, totalEntries: Int?, modifier: Modifier) {
    val status = when (totalEntries) {
        null -> title
        else -> stringResource(
            R.string.installing_entries,
            TibetanNumerals.formatCount(totalEntries, DictTheme.language),
        )
    }
    Column(
        modifier.padding(bottom = StatusBottom),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StatusGap),
    ) {
        ProgressBar(progress, Modifier.width(ProgressWidth))
        MixedText(status, DictTheme.type.tiny, DictTheme.type.tibetanInline14, color = DictTheme.colors.muted)
    }
}

@Composable
private fun InstallFailed(
    cause: Throwable,
    onRetry: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: (() -> Unit)?,
    modifier: Modifier,
) {
    // A cancelled copy is not a storage problem: say it was interrupted instead of giving the storage advice.
    val body = if (cause is CancellationException) R.string.install_interrupted else R.string.install_failed_body
    FullEmptyState(
        icon = DictIcons.EmptyState.SearchOff,
        title = stringResource(R.string.install_failed),
        body = stringResource(body),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StatusGap),
        ) {
            FilledButton(stringResource(R.string.try_again), onRetry, size = ButtonSize.Medium)
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(secondaryActionLabel, onSecondaryAction, size = ButtonSize.Medium)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun InstallingPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        InstallingScreen(
            AppRootState.Installing(DictionaryPack.TIBETAN, progress = 0.6f, totalEntries = 180_412),
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun InstallingDzongkhaPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        InstallingScreen(AppRootState.Installing(DictionaryPack.DZONGKHA, progress = 0.3f, totalEntries = null), onRetry = {})
    }
}

@PreviewLightDark
@Composable
private fun InstallFailedPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        InstallingScreen(
            AppRootState.Installing(DictionaryPack.TIBETAN, null, null, failure = IOException()),
            onRetry = {},
        )
    }
}
