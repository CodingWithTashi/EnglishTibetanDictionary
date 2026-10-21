package com.kharagedition.tibetandictionary.ui.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.startActivitySafely
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DictIconButton
import com.kharagedition.tibetandictionary.ui.components.ExtremeScale
import com.kharagedition.tibetandictionary.ui.components.ExtremeScalePreview
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.ScreenTitle
import com.kharagedition.tibetandictionary.ui.components.SettingsRow
import com.kharagedition.tibetandictionary.ui.components.SettingsTrailing
import com.kharagedition.tibetandictionary.ui.components.TibetanText
import com.kharagedition.tibetandictionary.ui.components.extendStart
import com.kharagedition.tibetandictionary.ui.profile.versionLine
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Gap between About's blocks, and between a block's heading line and its body. */
private val BlockGap = 16.dp
private val LineGap = 8.dp

internal const val ABOUT_SCREEN_TAG = "about_screen"

/** About: data source, fonts and licences. [onBack] pops the route; [onOpenLicences] pushes the licence texts. */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicences: () -> Unit) {
    val viewModel = hiltViewModel<AboutViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val loaded = state
    if (loaded != null) {
        AboutScreen(
            state = loaded,
            onBack = onBack,
            onLicences = onOpenLicences,
            onEmail = {
                val subject = resources.getString(R.string.app_name) + " " + loaded.versionName
                val email = Intent(Intent.ACTION_SENDTO, "mailto:${Links.DEV_EMAIL}".toUri())
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.DEV_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                context.startActivitySafely(email)
            },
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(DictTheme.colors.background),
        )
    }
}

/**
 * The stateless About screen: back, the app's name and version line, the dictionary data credit (with the data's
 * own attribution when it has one), the fonts, a row that opens the licence texts ([onLicences]) and a row that
 * writes a correction e-mail to the developer.
 *
 * Test tags: `about_screen`, `about_licences`, `about_email`.
 */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onBack: () -> Unit,
    onLicences: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(ABOUT_SCREEN_TAG)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        AboutBackRow(onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            ScreenTitle(stringResource(R.string.about))
            Column {
                TibetanText(stringResource(R.string.app_name_tibetan), style = type.tibetanSplash, color = colors.ink)
                MixedText(versionLine(state.versionName, state.entryCount), type.secondary, type.tibetanInline15, color = colors.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(LineGap)) {
                MixedText(stringResource(R.string.about_data, "Monlam Tibetan Dictionary", "Lobsang Monlam", "GPL-3"), type.bodySmallMedium, type.tibetanInline16, color = colors.ink)
                if (state.attribution != null) {
                    MixedText(state.attribution, type.secondary, type.tibetanInline15, color = colors.muted)
                }
            }
            MixedText(stringResource(R.string.about_fonts), type.bodySmall, type.tibetanInline16, color = colors.ink)
            SettingsRow(
                label = stringResource(R.string.licences),
                modifier = Modifier.testTag("about_licences"),
                onClick = onLicences,
                trailing = SettingsTrailing.Chevron,
            )
            SettingsRow(
                label = stringResource(R.string.report_correction),
                modifier = Modifier.testTag("about_email"),
                subLabel = Links.DEV_EMAIL,
                onClick = onEmail,
                trailing = SettingsTrailing.Chevron,
                divider = false,
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** The back button of About and Licences; the icon sits on the text edge, its 48dp target reaching into the padding. */
@Composable
internal fun AboutBackRow(onBack: () -> Unit) {
    val dimens = DictTheme.dimens
    Row(
        Modifier
            .padding(start = dimens.screenPadding)
            .extendStart((dimens.iconButtonLarge - dimens.iconLarge) / 2),
    ) {
        DictIconButton(DictIcons.Back, stringResource(R.string.back), onBack)
    }
}

private val previewState = AboutUiState(
    versionName = BuildConfig.VERSION_NAME,
    entryCount = 180_412,
    attribution = "Dictionary data from the Monlam Tibetan Dictionary by Lobsang Monlam, as published in the " +
        "open-source Monlam Dictionary app under the GNU General Public License, version 3 (GPL-3.0).",
)

@PreviewLightDark
@BoPreview
@Composable
private fun AboutPreview() {
    ComponentPreview(edgeToEdge = true) { AboutScreen(previewState, onBack = {}, onLicences = {}, onEmail = {}) }
}

@ExtremeScalePreview
@Composable
private fun AboutExtremeScalePreview() {
    ExtremeScale(edgeToEdge = true) {
        AboutScreen(previewState.copy(entryCount = null), onBack = {}, onLicences = {}, onEmail = {})
    }
}
