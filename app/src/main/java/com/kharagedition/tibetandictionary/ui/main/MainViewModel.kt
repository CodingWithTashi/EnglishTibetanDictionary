package com.kharagedition.tibetandictionary.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.navigation.MainTab
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The bottom-bar tabs in bar order: every [MainTab] except Learn while [learnEnabled] is off. */
fun visibleTabs(learnEnabled: Boolean = Features.LEARN): List<MainTab> =
    MainTab.entries.filter { learnEnabled || it != MainTab.LEARN }

/**
 * Selected tab of the main scaffold, and text handed to the Translate tab. Activity-scoped so deep links and pushed
 * routes can switch tabs.
 */
@HiltViewModel
class MainViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val tabs: List<MainTab> = visibleTabs()

    val tab: StateFlow<MainTab> = savedStateHandle.getStateFlow(KEY_TAB, MainTab.SEARCH)

    private val request = MutableStateFlow(restoreRequest())

    /** Text for the Translate tab to load (Open in Translate), until the tab [consumes][consumeTranslateRequest] it. */
    val translateRequest: StateFlow<TranslateRequest?> = request.asStateFlow()

    fun selectTab(tab: MainTab) {
        savedStateHandle[KEY_TAB] = tab
    }

    /** Holds [request] for the Translate tab and shows that tab. */
    fun openTranslate(request: TranslateRequest) {
        savedStateHandle[KEY_REQUEST_PAIR] = request.pair.key
        savedStateHandle[KEY_REQUEST_TEXT] = request.text
        savedStateHandle[KEY_REQUEST_TRANSLATION] = request.result?.translation
        savedStateHandle[KEY_REQUEST_CACHED] = request.result?.cached
        savedStateHandle[KEY_REQUEST_PROVIDER] = request.result?.provider?.code
        this.request.value = request
        selectTab(MainTab.TRANSLATE)
    }

    fun consumeTranslateRequest() {
        RequestKeys.forEach { savedStateHandle.remove<Any>(it) }
        request.value = null
    }

    private fun restoreRequest(): TranslateRequest? {
        val pair = savedStateHandle.get<String>(KEY_REQUEST_PAIR)?.let(LangPair::fromKey) ?: return null
        val text = savedStateHandle.get<String>(KEY_REQUEST_TEXT) ?: return null
        val result = savedStateHandle.get<String>(KEY_REQUEST_TRANSLATION)?.let { translation ->
            TranslatedText(
                translation,
                cached = savedStateHandle.get<Boolean>(KEY_REQUEST_CACHED) == true,
                provider = Provider.fromCode(savedStateHandle.get<String>(KEY_REQUEST_PROVIDER)),
            )
        }
        return TranslateRequest(pair, text, result)
    }

    private companion object {
        const val KEY_TAB = "selected_tab"
        const val KEY_REQUEST_PAIR = "translate_request_pair"
        const val KEY_REQUEST_TEXT = "translate_request_text"
        const val KEY_REQUEST_TRANSLATION = "translate_request_translation"
        const val KEY_REQUEST_CACHED = "translate_request_cached"
        const val KEY_REQUEST_PROVIDER = "translate_request_provider"
        val RequestKeys = listOf(
            KEY_REQUEST_PAIR,
            KEY_REQUEST_TEXT,
            KEY_REQUEST_TRANSLATION,
            KEY_REQUEST_CACHED,
            KEY_REQUEST_PROVIDER,
        )
    }
}
