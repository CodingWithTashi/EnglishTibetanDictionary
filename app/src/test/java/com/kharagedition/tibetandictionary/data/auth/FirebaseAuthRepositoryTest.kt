package com.kharagedition.tibetandictionary.data.auth

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.fakes.FakeAuthBackend
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

private class FakeCredentials(var idToken: String? = "google-token") : GoogleCredentialSource {
    var requests = 0
    var clears = 0
    var clearFailure: Exception? = null

    override suspend fun requestIdToken(activity: Activity): String? {
        requests++
        return idToken
    }

    override suspend fun clearCredentialState() {
        clears++
        clearFailure?.let { throw it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepositoryTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val credentials = FakeCredentials()
    private val errors = RecordingErrorReporter()

    private fun TestScope.repository(backend: FakeAuthBackend, scope: CoroutineScope = backgroundScope) =
        FirebaseAuthRepository(backend, credentials, errors, scope)

    private val guest = AuthUser("u1", isAnonymous = true, name = null, email = null)

    @Test
    fun `account is null until a user exists`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend()
        assertThat(repository(backend).account.value).isNull()
        assertThat(backend.anonymousSignIns).isEqualTo(0)
    }

    @Test
    fun `ensureUser signs in anonymously once when there is no user`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend()
        val repository = repository(backend)
        val first = async { repository.ensureUser() }
        val second = async { repository.ensureUser() }
        assertThat(first.await().uid).isEqualTo(second.await().uid)
        assertThat(backend.anonymousSignIns).isEqualTo(1)
        assertThat(repository.account.value).isEqualTo(Account("guest-1", isGuest = true, name = null, email = null))
    }

    @Test
    fun `ensureUser returns the existing user without signing in`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend(guest)
        assertThat(repository(backend).ensureUser().uid).isEqualTo("u1")
        assertThat(backend.anonymousSignIns).isEqualTo(0)
    }

    @Test
    fun `ensureUser offline throws IOException`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend().apply { anonymousFailure = IOException("offline") }
        val repository = repository(backend)
        val failure = runCatching { repository.ensureUser() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a guest linking Google keeps the uid`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend(guest)
        val repository = repository(backend)
        assertThat(repository.signInWithGoogle(activity)).isEqualTo(SignInResult.Success(linked = true))
        assertThat(repository.account.value).isEqualTo(Account("u1", isGuest = false, name = "Tashi", email = "t@x.com"))
        assertThat(backend.links).containsExactly("google-token")
        assertThat(backend.refreshes).isEqualTo(1)
    }

    @Test
    fun `an account that already exists switches uid`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend(guest).apply { linkFailure = AccountCollisionException() }
        val repository = repository(backend)
        assertThat(repository.signInWithGoogle(activity)).isEqualTo(SignInResult.Success(linked = false))
        assertThat(backend.googleSignIns).containsExactly("google-token")
        assertThat(repository.account.value?.uid).isEqualTo("u2")
        assertThat(backend.refreshes).isEqualTo(1)
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `with no user, Google signs in directly`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend()
        val repository = repository(backend)
        assertThat(repository.signInWithGoogle(activity)).isEqualTo(SignInResult.Success(linked = false))
        assertThat(backend.links).isEmpty()
        assertThat(backend.googleSignIns).containsExactly("google-token")
    }

    @Test
    fun `cancelling changes nothing`() = runTest(UnconfinedTestDispatcher()) {
        credentials.idToken = null
        val backend = FakeAuthBackend(guest)
        val repository = repository(backend)
        assertThat(repository.signInWithGoogle(activity)).isEqualTo(SignInResult.Cancelled)
        assertThat(backend.calls).isEqualTo(0)
        assertThat(repository.account.value?.isGuest).isTrue()
    }

    @Test
    fun `a failure is reported`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend(guest).apply { linkFailure = IllegalStateException("broken") }
        val result = repository(backend).signInWithGoogle(activity)
        assertThat(result).isInstanceOf(SignInResult.Failed::class.java)
        assertThat(errors.recorded).hasSize(1)
    }

    @Test
    fun `an offline sign-in fails without a report`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend(guest).apply { linkFailure = IOException("offline") }
        val result = repository(backend).signInWithGoogle(activity)
        assertThat((result as SignInResult.Failed).cause).isInstanceOf(IOException::class.java)
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `sign-out clears the credential state`() = runTest(UnconfinedTestDispatcher()) {
        val backend = FakeAuthBackend(AuthUser("u1", isAnonymous = false, name = "Tashi", email = "t@x.com"))
        val repository = repository(backend)
        repository.signOut()
        assertThat(backend.signOuts).isEqualTo(1)
        assertThat(credentials.clears).isEqualTo(1)
        assertThat(repository.account.value).isNull()
    }

    @Test
    fun `a failure clearing the credential state is recorded, not thrown`() = runTest(UnconfinedTestDispatcher()) {
        credentials.clearFailure = IllegalStateException("no provider")
        val backend = FakeAuthBackend(guest)
        repository(backend).signOut()
        assertThat(backend.signOuts).isEqualTo(1)
        assertThat(errors.recorded).hasSize(1)
    }

    @Test
    fun `cancellation is rethrown`() {
        val backend = FakeAuthBackend(guest).apply { linkFailure = kotlinx.coroutines.CancellationException("gone") }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                FirebaseAuthRepository(backend, credentials, errors, this).signInWithGoogle(activity)
            }
        }
    }
}
