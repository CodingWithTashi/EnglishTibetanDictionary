package com.kharagedition.tibetandictionary.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A request from outside the nav graph (widget, "look up" from other apps, a new `app://` intent). */
sealed interface DeepLink {
    data class OpenEntry(val entryId: Long) : DeepLink

    data class OpenSearch(val query: String) : DeepLink
}

/**
 * Carries deep links from the Activity (intents) to the nav host. The last link is replayed so one that arrives
 * before the host is composed (installing, cold start) is not lost; the host calls [consumed] once handled so it
 * is not replayed after a configuration change.
 */
@Singleton
class NavigationEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<DeepLink>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<DeepLink> = _events.asSharedFlow()

    fun emit(link: DeepLink) {
        _events.tryEmit(link)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumed() {
        _events.resetReplayCache()
    }
}

/** Exposes the singleton [NavigationEventBus] to composables. */
@HiltViewModel
class NavigationEventsViewModel @Inject constructor(val bus: NavigationEventBus) : ViewModel()
