package com.kharagedition.tibetandictionary.ui.main

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the Learn tab is hidden while Learn is off`() {
        assertThat(visibleTabs(learnEnabled = false))
            .containsExactly(MainTab.SEARCH, MainTab.TRANSLATE, MainTab.SAVED, MainTab.PROFILE)
            .inOrder()
    }

    @Test
    fun `the Learn tab sits between Saved and Profile when Learn is on`() {
        assertThat(visibleTabs(learnEnabled = true))
            .containsExactly(MainTab.SEARCH, MainTab.TRANSLATE, MainTab.SAVED, MainTab.LEARN, MainTab.PROFILE)
            .inOrder()
    }

    @Test
    fun `the scaffold shows the tabs for the current Learn flag`() {
        val expected = if (Features.LEARN) {
            listOf(MainTab.SEARCH, MainTab.TRANSLATE, MainTab.SAVED, MainTab.LEARN, MainTab.PROFILE)
        } else {
            listOf(MainTab.SEARCH, MainTab.TRANSLATE, MainTab.SAVED, MainTab.PROFILE)
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

    @Test
    fun `openTranslate selects the Translate tab and holds the request until consumed`() {
        val handle = SavedStateHandle()
        val vm = MainViewModel(handle)
        val request = TranslateRequest(LangPair(Lang.BO, Lang.EN), "བཀྲ་ཤིས་བདེ་ལེགས།")

        vm.openTranslate(request)

        assertThat(vm.tab.value).isEqualTo(MainTab.TRANSLATE)
        assertThat(vm.translateRequest.value).isEqualTo(request)

        vm.consumeTranslateRequest()

        assertThat(vm.translateRequest.value).isNull()
        assertThat(vm.tab.value).isEqualTo(MainTab.TRANSLATE)
        assertThat(MainViewModel(handle).translateRequest.value).isNull()
    }

    @Test
    fun `the request survives a recreated view model`() {
        val handle = SavedStateHandle()
        val withResult = TranslateRequest(
            LangPair(Lang.EN, Lang.BO),
            "Good morning",
            TranslatedText("ཞོགས་པ་བདེ་ལེགས།", cached = true, provider = Provider.AZURE),
        )
        MainViewModel(handle).openTranslate(withResult)

        val restored = MainViewModel(handle)

        assertThat(restored.translateRequest.value).isEqualTo(withResult)
        assertThat(restored.tab.value).isEqualTo(MainTab.TRANSLATE)

        val textOnly = TranslateRequest(LangPair(Lang.BO, Lang.EN), "ང་བོད་པ་ཡིན།")
        restored.openTranslate(textOnly)

        assertThat(MainViewModel(handle).translateRequest.value).isEqualTo(textOnly)
    }
}
