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
import com.kharagedition.tibetandictionary.ui.components.ButtonSize
import com.kharagedition.tibetandictionary.ui.components.FilledButton
import com.kharagedition.tibetandictionary.ui.components.FlagStrand
import com.kharagedition.tibetandictionary.ui.components.FlagStrandSize
import com.kharagedition.tibetandictionary.ui.components.FullEmptyState
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.ProgressBar
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
 * First-launch install (3a): the prayer-flag strand at the top, the app icon with the Tibetan and English names
 * in the middle, and a 120dp progress bar over "Preparing … · offline" at the bottom (the entry count comes from
 * [state], else from [bundledEntryCount]). When [state] carries a failure, a full empty state with "Try again"
 * ([onRetry]) replaces it.
 *
 * @param bundledEntryCount the entry count the data build recorded for the bundled asset (0 = unknown).
 */
@Composable
fun InstallingScreen(
    state: AppRootState.Installing,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bundledEntryCount: Int = integerResource(R.integer.dictionary_entry_count),
) {
    Box(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .testTag(INSTALLING_SCREEN_TAG)
            .systemBarsPadding(),
    ) {
        if (state.failure != null) {
            InstallFailed(state.failure, onRetry, Modifier.align(Alignment.Center))
        } else {
            FlagStrand(FlagStrandSize.Splash, Modifier.align(Alignment.TopCenter))
            AppNames(Modifier.align(Alignment.Center))
            InstallProgress(state, bundledEntryCount, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun AppNames(modifier: Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The same tile as the system splash. Its drawable includes the shadow, so it is larger than the icon slot
        // and overflows it evenly on every side (requiredSize centres it on the slot).
        Image(
            painterResource(R.drawable.ic_splash_tile),
            contentDescription = null,
            modifier = Modifier
                .size(DictTheme.dimens.splashIcon)
                .requiredSize(SplashTileFrame),
        )
        TibetanText(
            stringResource(R.string.app_name_tibetan),
            Modifier.padding(top = TibetanNameTop),
            style = type.tibetanSplash,
            color = colors.ink,
        )
        MixedText(
            stringResource(R.string.app_name),
            type.buttonLarge.copy(letterSpacing = LatinNameTracking),
            type.tibetanInline16,
            Modifier.offset(y = LatinNameOverlap),
            color = colors.ink,
        )
    }
}

@Composable
private fun InstallProgress(state: AppRootState.Installing, bundledEntryCount: Int, modifier: Modifier) {
    // Before the installer knows the count, use the one the data build recorded for the bundled asset (0 = unknown).
    val total = state.totalEntries ?: bundledEntryCount.takeIf { it > 0 }
    val status = when (total) {
        null -> stringResource(R.string.installing_dictionary)
        else -> stringResource(R.string.installing_entries, TibetanNumerals.formatCount(total, DictTheme.language))
    }
    Column(
        modifier.padding(bottom = StatusBottom),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StatusGap),
    ) {
        ProgressBar(state.progress, Modifier.width(ProgressWidth))
        MixedText(status, DictTheme.type.tiny, DictTheme.type.tibetanInline14, color = DictTheme.colors.muted)
    }
}

@Composable
private fun InstallFailed(cause: Throwable, onRetry: () -> Unit, modifier: Modifier) {
    // A cancelled copy is not a storage problem: say it was interrupted instead of giving the storage advice.
    val body = if (cause is CancellationException) R.string.install_interrupted else R.string.install_failed_body
    FullEmptyState(
        icon = DictIcons.EmptyState.SearchOff,
        title = stringResource(R.string.install_failed),
        body = stringResource(body),
        modifier = modifier,
    ) {
        FilledButton(stringResource(R.string.try_again), onRetry, size = ButtonSize.Medium)
    }
}

@PreviewLightDark
@Composable
private fun InstallingPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        InstallingScreen(AppRootState.Installing(progress = 0.6f, totalEntries = 180_412), onRetry = {})
    }
}

@PreviewLightDark
@Composable
private fun InstallFailedPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        InstallingScreen(AppRootState.Installing(progress = null, totalEntries = null, failure = IOException()), onRetry = {})
    }
}
