package com.kharagedition.tibetandictionary.ui.main

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.navigation.MainTab
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the Learn tab is hidden while Learn is off`() {
        assertThat(visibleTabs(learnEnabled = false))
            .containsExactly(MainTab.SEARCH, MainTab.SAVED, MainTab.PROFILE)
            .inOrder()
    }

    @Test
    fun `the Learn tab sits between Saved and Profile when Learn is on`() {
        assertThat(visibleTabs(learnEnabled = true))
            .containsExactly(MainTab.SEARCH, MainTab.SAVED, MainTab.LEARN, MainTab.PROFILE)
            .inOrder()
    }

    @Test
    fun `the scaffold shows the tabs for the current Learn flag`() {
        val expected = if (Features.LEARN) {
            listOf(MainTab.SEARCH, MainTab.SAVED, MainTab.LEARN, MainTab.PROFILE)
        } else {
            listOf(MainTab.SEARCH, MainTab.SAVED, MainTab.PROFILE)
        }

        assertThat(MainViewModel(SavedStateHandle()).tabs).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `starts on the Search tab and follows selectTab`() = runTest {
        val vm = MainViewModel(SavedStateHandle())

        vm.tab.test {
            assertThat(awaitItem()).isEqualTo(MainTab.SEARCH)

            vm.selectTab(MainTab.SAVED)
            assertThat(awaitItem()).isEqualTo(MainTab.SAVED)

            vm.selectTab(MainTab.PROFILE)
            assertThat(awaitItem()).isEqualTo(MainTab.PROFILE)
        }
    }

    @Test
    fun `the selected tab survives process death`() {
        val handle = SavedStateHandle()
        MainViewModel(handle).selectTab(MainTab.PROFILE)

        assertThat(MainViewModel(handle).tab.value).isEqualTo(MainTab.PROFILE)
    }
}
