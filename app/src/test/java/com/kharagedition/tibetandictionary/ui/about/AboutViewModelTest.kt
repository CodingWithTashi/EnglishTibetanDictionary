package com.kharagedition.tibetandictionary.ui.about

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.SampleData
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

    @Test
    fun `About shows the active pack's attribution`() = runTest {
        dictionary.capabilities.value = SampleData.capabilities.copy(
            pack = DictionaryPack.DZONGKHA,
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

    @Test
    fun `the pack and its entry count both come from the capabilities`() = runTest {
        val dzCapabilities = SampleData.capabilities.copy(
            pack = DictionaryPack.DZONGKHA,
            directions = listOf(SampleData.boEn.copy(entryCount = 81_474)),
        )
        val vm = AboutViewModel(dictionary)

        vm.uiState.test {
            dictionary.capabilities.value = dzCapabilities
            val seen = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item)?.value }
                .filterNotNull()

            assertThat(seen.first().pack).isEqualTo(DictionaryPack.TIBETAN)
            for (state in seen) {
                val count = if (state.pack == DictionaryPack.DZONGKHA) 81_474 else SampleData.capabilities.totalEntries
                assertThat(state.entryCount).isEqualTo(count)
            }
            assertThat(seen.last().pack).isEqualTo(DictionaryPack.DZONGKHA)
        }
    }
}
