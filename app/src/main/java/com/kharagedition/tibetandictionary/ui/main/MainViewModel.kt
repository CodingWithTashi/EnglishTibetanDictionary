package com.kharagedition.tibetandictionary.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.navigation.MainTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The bottom-bar tabs in bar order: every [MainTab] except Learn while [learnEnabled] is off. */
fun visibleTabs(learnEnabled: Boolean = Features.LEARN): List<MainTab> =
    MainTab.entries.filter { learnEnabled || it != MainTab.LEARN }

/** Selected tab of the main scaffold. Activity-scoped so deep links and pushed routes can switch tabs. */
@HiltViewModel
class MainViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val tabs: List<MainTab> = visibleTabs()

    val tab: StateFlow<MainTab> = savedStateHandle.getStateFlow(KEY_TAB, MainTab.SEARCH)

    fun selectTab(tab: MainTab) {
        savedStateHandle[KEY_TAB] = tab
    }

    private companion object {
        const val KEY_TAB = "selected_tab"
    }
}
