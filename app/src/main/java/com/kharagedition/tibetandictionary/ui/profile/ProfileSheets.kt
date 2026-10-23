package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.DictModalSheet
import com.kharagedition.tibetandictionary.ui.components.DictionaryPickerSheet
import com.kharagedition.tibetandictionary.ui.components.DirectionPickerSheet
import com.kharagedition.tibetandictionary.ui.components.LanguagePickerSheet
import com.kharagedition.tibetandictionary.ui.components.SettingsRow
import com.kharagedition.tibetandictionary.ui.components.SheetTitle
import com.kharagedition.tibetandictionary.ui.components.TextSizeSheet
import com.kharagedition.tibetandictionary.ui.theme.DictIcons
import com.kharagedition.tibetandictionary.ui.theme.DictShapes
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Space under the last row, as on the shared picker sheets. */
private val SheetBottom = 24.dp

/** External-link mark after each app in the More apps sheet. */
private val LinkIcon = 18.dp

/** The sheet [state] has open, wired to [onEvent]; dismissing any of them sends [ProfileEvent.DismissSheet]. */
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
        ProfileSheet.DICTIONARY -> DictionaryPickerSheet(
            selected = state.dictionary,
            onPick = { onEvent(ProfileEvent.SetDictionary(it)) },
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
        ProfileSheet.MORE_APPS -> MoreAppsSheet(state.otherApps, onOpen = { onEvent(ProfileEvent.OpenOtherApp(it)) }, dismiss)
        ProfileSheet.ACCOUNT -> AccountSheet(state.account, onEvent, dismiss)
        null -> Unit
    }
}

/**
 * The More apps sheet. Opts in to the experimental Material 3 API because [DictModalSheet]'s signature exposes
 * `SheetState`; the shared picker sheets take no `SheetState`, so callers of those need no opt-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreAppsSheet(apps: List<OtherApp>, onOpen: (OtherApp) -> Unit, onDismiss: () -> Unit) {
    DictModalSheet(onDismiss) { MoreAppsContent(apps, onOpen) }
}

/** "More apps": the developer's other apps, each opening its store listing. */
@Composable
private fun MoreAppsContent(apps: List<OtherApp>, onOpen: (OtherApp) -> Unit) {
    val dimens = DictTheme.dimens
    SheetTitle(stringResource(R.string.more_apps))
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
