package com.kharagedition.tibetandictionary.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// Splash 3a measurements.
private val IconShape = RoundedCornerShape(percent = 25)
private val TibetanNameTop = 22.dp
private val LatinNameOverlap = (-4).dp
private val StatusBottom = 40.dp
private val StatusGap = 10.dp
private val ProgressWidth = 120.dp
private val LatinNameTracking = 0.02.em

/**
 * First-launch install (3a): the prayer-flag strand at the top, the app icon with the Tibetan and English names
 * in the middle, and a 120dp progress bar over "Preparing … · offline" at the bottom. When [state] carries a
 * failure, a full empty state with "Try again" ([onRetry]) replaces it.
 */
@Composable
fun InstallingScreen(state: AppRootState.Installing, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(DictTheme.colors.background)
            .systemBarsPadding(),
    ) {
        if (state.failure != null) {
            InstallFailed(onRetry, Modifier.align(Alignment.Center))
        } else {
            FlagStrand(FlagStrandSize.Splash, Modifier.align(Alignment.TopCenter))
            AppNames(Modifier.align(Alignment.Center))
            InstallProgress(state, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun AppNames(modifier: Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // TODO(L5, T5.1): draw ic_splash_icon (with its shadow) here instead of this accent placeholder.
        Box(
            Modifier
                .size(DictTheme.dimens.splashIcon)
                .background(colors.accent, IconShape),
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
private fun InstallProgress(state: AppRootState.Installing, modifier: Modifier) {
    val status = when (val total = state.totalEntries) {
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
private fun InstallFailed(onRetry: () -> Unit, modifier: Modifier) {
    FullEmptyState(
        icon = DictIcons.EmptyState.SearchOff,
        title = stringResource(R.string.install_failed),
        body = stringResource(R.string.install_failed_body),
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
