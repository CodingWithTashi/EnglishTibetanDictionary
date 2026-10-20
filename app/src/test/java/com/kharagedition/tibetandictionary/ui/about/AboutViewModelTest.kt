package com.kharagedition.tibetandictionary.ui.about

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val dictionary = FakeDictionaryRepository()

    private fun TestScope.state(): AboutUiState {
        val vm = AboutViewModel(dictionary)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return checkNotNull(vm.uiState.value)
    }

    @Test
    fun `About shows the app version, the entry count and the data's attribution`() = runTest {
        val state = state()

        assertThat(state.versionName).isEqualTo(BuildConfig.VERSION_NAME)
        assertThat(state.entryCount).isEqualTo(SampleData.capabilities.totalEntries)
        assertThat(state.attribution).isEqualTo(SampleData.attribution.text)
    }

    @Test
    fun `no count and no attribution text when the data has none`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(
            directions = emptyList(),
            attribution = SampleData.attribution.copy(text = " "),
        )

        val state = state()

        assertThat(state.entryCount).isNull()
        assertThat(state.attribution).isNull()
    }
}
