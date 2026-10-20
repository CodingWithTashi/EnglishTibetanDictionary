package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.DirectionPickerSheet
import com.kharagedition.tibetandictionary.ui.components.LanguagePickerSheet
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.SettingsRow
import com.kharagedition.tibetandictionary.ui.components.TextSizeSheet
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Sheet title padding (8 above, 12 below) and space under the last row, as on the shared picker sheets. */
private val TitleTop = 8.dp
private val TitleBottom = 12.dp
private val SheetBottom = 24.dp

/** External-link mark after each app in the More apps sheet. */
private val LinkIcon = 18.dp

/** The sheet [state] has open, wired to [onEvent]; dismissing any of them sends [ProfileEvent.DismissSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileSheets(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    val dismiss = { onEvent(ProfileEvent.DismissSheet) }
    when (state.sheet) {
        ProfileSheet.TEXT_SIZE -> TextSizeSheet(
            textSize = state.textSize,
            lineSpacing = state.lineSpacing,
            onTextSizeChange = { onEvent(ProfileEvent.SetTextSize(it)) },
            onLineSpacingChange = { onEvent(ProfileEvent.SetLineSpacing(it)) },
            onDismiss = dismiss,
        )
        ProfileSheet.DIRECTION -> state.defaultDirection?.let { selected ->
            DirectionPickerSheet(
                directions = state.directions,
                selected = selected,
                onPick = { onEvent(ProfileEvent.SetDefaultDirection(it)) },
                onDismiss = dismiss,
            )
        }
        ProfileSheet.LANGUAGE -> LanguagePickerSheet(
            selected = state.language,
            onPick = { onEvent(ProfileEvent.SetLanguage(it)) },
            onDismiss = dismiss,
        )
        ProfileSheet.MORE_APPS -> DictModalSheet(dismiss) {
            MoreAppsContent(state.otherApps) { onEvent(ProfileEvent.OpenOtherApp(it)) }
        }
        null -> Unit
    }
}

/** "More apps": the developer's other apps, each opening its store listing. */
@Composable
private fun MoreAppsContent(apps: List<OtherApp>, onOpen: (OtherApp) -> Unit) {
    val dimens = DictTheme.dimens
    MixedText(
        stringResource(R.string.more_apps),
        DictTheme.type.noResultsTitle,
        DictTheme.type.tibetanGloss,
        Modifier
            .fillMaxWidth()
            .padding(start = dimens.screenPadding, top = TitleTop, end = dimens.screenPadding, bottom = TitleBottom)
            .semantics { heading() },
        color = DictTheme.colors.ink,
    )
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = dimens.screenPadding, end = dimens.screenPadding, bottom = SheetBottom),
    ) {
        apps.forEach { app ->
            SettingsRow(
                label = stringResource(app.label),
                valueContent = {
                    Icon(DictIcons.OpenExternal, contentDescription = null, Modifier.size(LinkIcon), tint = DictTheme.colors.muted)
                },
                onClick = { onOpen(app) },
                divider = app != apps.last(),
            )
        }
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun MoreAppsPreview() {
    ComponentPreview(edgeToEdge = true) {
        Column(
            Modifier
                .clip(DictShapes.sheet)
                .background(DictTheme.colors.background),
        ) {
            MoreAppsContent(OtherApp.entries) {}
        }
    }
}
