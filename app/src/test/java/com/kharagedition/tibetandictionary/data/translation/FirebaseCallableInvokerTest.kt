package com.kharagedition.tibetandictionary.data.translation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.kharagedition.tibetandictionary.core.firebase.FirebaseAvailability
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** The SDK's constructors are internal; a mock stands in for a real failure. */
private fun functionsException(withCode: Code, withDetails: Any? = null, withCause: Throwable? = null) =
    mockk<FirebaseFunctionsException> {
        every { code } returns withCode
        every { details } returns withDetails
        every { cause } returns withCause
    }

// Robolectric: the SDK's exception classes touch android.text and android.util when they load.
@RunWith(AndroidJUnit4::class)
class FirebaseCallableInvokerTest {

    @Test
    fun `a refusal keeps its code and details`() {
        val failure = callableFailure(
            functionsException(Code.RESOURCE_EXHAUSTED, mapOf("reason" to "daily_limit", "limit" to 10)),
        )
        assertThat(failure).isInstanceOf(CallableException::class.java)
        failure as CallableException
        assertThat(failure.code).isEqualTo("RESOURCE_EXHAUSTED")
        assertThat(failure.details).containsExactly("reason", "daily_limit", "limit", 10)
    }

    @Test
    fun `details that are not a map are dropped`() {
        val failure = callableFailure(functionsException(Code.INTERNAL, "oops")) as CallableException
        assertThat(failure.details).isNull()
    }

    @Test
    fun `no network is offline, though the SDK reports it as INTERNAL`() {
        val failure = callableFailure(functionsException(Code.INTERNAL, withCause = UnknownHostException()))
        assertThat(failure).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a timeout is offline`() {
        val failure = callableFailure(functionsException(Code.DEADLINE_EXCEEDED, withCause = InterruptedIOException()))
        assertThat(failure).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a network failure while getting tokens is offline`() {
        assertThat(callableFailure(FirebaseNetworkException("down"))).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a deleted or disabled user is AccountGone`() {
        val gone = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user")
        assertThat(callableFailure(gone)).isInstanceOf(AccountGoneException::class.java)
    }

    @Test
    fun `a deleted user behind a functions error is AccountGone too`() {
        val gone = FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled")
        assertThat(callableFailure(functionsException(Code.INTERNAL, withCause = gone)))
            .isInstanceOf(AccountGoneException::class.java)
    }

    @Test
    fun `anything else is passed through`() {
        val bug = IllegalStateException("bug")
        assertThat(callableFailure(bug)).isSameInstanceAs(bug)
    }

    @Test
    fun `without Firebase every call is offline`() {
        // Robolectric has no default FirebaseApp, as on a build without google-services.
        val availability = FirebaseAvailability(ApplicationProvider.getApplicationContext())
        assertThrows(IOException::class.java) {
            runBlocking { FirebaseCallableInvoker(availability).call("translate", null) }
        }
    }
}
