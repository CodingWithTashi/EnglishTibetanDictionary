package com.kharagedition.tibetandictionary.data.billing

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import io.mockk.mockk
import org.junit.Test

class BillingSetupTest {

    private class FakeSdk : StoreSdk {
        val configuredAs = mutableListOf<String>()
        var failure: Exception? = null

        override val isConfigured: Boolean get() = configuredAs.isNotEmpty()

        override fun configure(context: Context, key: String, appUserId: String) {
            failure?.let { throw it }
            configuredAs += appUserId
        }
    }

    private val sdk = FakeSdk()
    private val errors = RecordingErrorReporter()
    private val context = mockk<Context>(relaxed = true)

    private fun setUp(key: String = "goog_test") = BillingSetup(errors, key, sdk).apply { configure(context) }

    @Test
    fun `set up, it can sell but starts nothing until a Google user`() {
        val setup = setUp()

        assertThat(setup.canSell).isTrue()
        assertThat(sdk.configuredAs).isEmpty()
    }

    @Test
    fun `the first Google user starts RevenueCat as that user, once`() {
        val setup = setUp()

        assertThat(setup.configureAs("u1")).isTrue()
        assertThat(setup.configureAs("u2")).isTrue()
        assertThat(sdk.configuredAs).containsExactly("u1")
    }

    @Test
    fun `a build without a key can't sell and never starts`() {
        val setup = setUp(key = "")

        assertThat(setup.canSell).isFalse()
        assertThat(setup.configureAs("u1")).isFalse()
        assertThat(sdk.configuredAs).isEmpty()
    }

    @Test
    fun `before set-up, a Google user starts nothing`() {
        val setup = BillingSetup(errors, "goog_test", sdk)

        assertThat(setup.canSell).isFalse()
        assertThat(setup.configureAs("u1")).isFalse()
        assertThat(sdk.configuredAs).isEmpty()
    }

    @Test
    fun `a failing start is recorded and reported`() {
        sdk.failure = IllegalStateException("bad key")
        val setup = setUp()

        assertThat(setup.configureAs("u1")).isFalse()
        assertThat(errors.recorded).hasSize(1)
    }
}
