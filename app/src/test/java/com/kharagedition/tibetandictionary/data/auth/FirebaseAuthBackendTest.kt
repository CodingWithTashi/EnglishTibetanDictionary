package com.kharagedition.tibetandictionary.data.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.firebase.FirebaseAvailability
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric has no default FirebaseApp, as on a build without google-services. */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthBackendTest {
    private val backend = FirebaseAuthBackend(FirebaseAvailability(ApplicationProvider.getApplicationContext()))

    @Test
    fun `without Firebase there is no user`() {
        assertThat(backend.user.value).isNull()
    }

    @Test
    fun `without Firebase signing in is offline`() {
        assertThrows(IOException::class.java) { runBlocking { backend.signInAnonymously() } }
    }
}
