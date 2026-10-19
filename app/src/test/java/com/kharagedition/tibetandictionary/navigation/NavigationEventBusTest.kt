package com.kharagedition.tibetandictionary.navigation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NavigationEventBusTest {

    private val bus = NavigationEventBus()

    @Test
    fun `a link emitted before anyone collects is delivered when the nav host starts collecting`() = runTest {
        bus.emit(DeepLink.OpenEntry(42))

        bus.events.test {
            assertThat(awaitItem()).isEqualTo(DeepLink.OpenEntry(42))
        }
    }

    @Test
    fun `links reach a collector in order`() = runTest {
        bus.events.test {
            bus.emit(DeepLink.OpenSearch("bkra"))
            bus.emit(DeepLink.OpenEntry(7))

            assertThat(awaitItem()).isEqualTo(DeepLink.OpenSearch("bkra"))
            assertThat(awaitItem()).isEqualTo(DeepLink.OpenEntry(7))
        }
    }

    @Test
    fun `a consumed link is not replayed to the next collector`() = runTest {
        bus.emit(DeepLink.OpenEntry(42))
        bus.events.test {
            awaitItem()
            bus.consumed()
        }

        bus.events.test {
            expectNoEvents()
        }
    }
}
