package com.kharagedition.tibetandictionary.ui.about

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
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

    private val databases = DictionaryDatabases { error("not opened in About tests") }.apply {
        activate(DictionaryPack.TIBETAN)
    }

    private fun TestScope.state(): AboutUiState {
        val vm = AboutViewModel(dictionary, databases)
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

    @Test
    fun `About shows the active pack's attribution`() = runTest {
        databases.activate(DictionaryPack.DZONGKHA)
        dictionary.capabilities.value = SampleData.capabilities.copy(
            attribution = SampleData.attribution.copy(
                sourceName = "DDC Dzongkha Dictionary",
                author = "Dzongkha Development Commission",
                license = "Permission of the DDC",
            ),
        )

        val state = state()

        assertThat(state.pack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(state.sourceName).isEqualTo("DDC Dzongkha Dictionary")
        assertThat(state.author).isEqualTo("Dzongkha Development Commission")
        assertThat(state.licence).isEqualTo("Permission of the DDC")
    }
}
