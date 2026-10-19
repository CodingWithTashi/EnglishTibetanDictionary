package com.kharagedition.tibetandictionary.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.components.BottomNavBar
import com.kharagedition.tibetandictionary.ui.home.HomeScreen
import com.kharagedition.tibetandictionary.ui.learn.LearnScreen
import com.kharagedition.tibetandictionary.ui.preview.DictPreview
import com.kharagedition.tibetandictionary.ui.profile.ProfileScreen
import com.kharagedition.tibetandictionary.ui.saved.SavedScreen
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/**
 * The [com.kharagedition.tibetandictionary.navigation.Main] destination: the selected tab above the bottom bar.
 * [viewModel] is activity-scoped (see `AppNavHost`) so the selected tab outlives pushed routes.
 */
@Composable
fun MainScaffold(
    onOpenSearch: (query: String?, voice: Boolean) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    MainScaffoldContent(tab = tab, tabs = viewModel.tabs, onSelectTab = viewModel::selectTab) { current ->
        when (current) {
            MainTab.SEARCH -> HomeScreen(onOpenSearch = onOpenSearch, onOpenEntry = onOpenEntry)
            MainTab.SAVED -> SavedScreen(onOpenEntry = onOpenEntry)
            MainTab.LEARN -> LearnScreen()
            MainTab.PROFILE -> ProfileScreen(onOpenAbout = onOpenAbout)
        }
    }
}

/**
 * Stateless scaffold: the [tab]'s content above a [BottomNavBar] of [tabs]. Each tab's saveable state (scroll
 * positions, typed text) is kept while another tab is shown. Back on any tab but Search returns to Search; on
 * Search it falls through, so the app exits.
 */
@Composable
internal fun MainScaffoldContent(
    tab: MainTab,
    tabs: List<MainTab>,
    onSelectTab: (MainTab) -> Unit,
    tabContent: @Composable (MainTab) -> Unit,
) {
    BackHandler(enabled = tab != MainTab.SEARCH) { onSelectTab(MainTab.SEARCH) }
    val tabStates = rememberSaveableStateHolder()
    Column(
        Modifier
            .fillMaxSize()
            .background(DictTheme.colors.background),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            tabStates.SaveableStateProvider(tab.name) { tabContent(tab) }
        }
        BottomNavBar(tabs = tabs, selected = tab, onSelect = onSelectTab, language = DictTheme.language)
    }
}

@PreviewLightDark
@Composable
private fun MainScaffoldPreview() {
    DictPreview(dark = isSystemInDarkTheme()) {
        MainScaffoldContent(tab = MainTab.SEARCH, tabs = visibleTabs(), onSelectTab = {}) {
            HomeScreen(onOpenSearch = { _, _ -> }, onOpenEntry = {})
        }
    }
}
