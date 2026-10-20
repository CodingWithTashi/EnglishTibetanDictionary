package com.kharagedition.tibetandictionary.core.init

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoredAppearanceTest {

    private val errors = RecordingErrorReporter()

    /** The default night mode is process-wide: leave it as the next test expects it. */
    @After
    fun resetNightMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun applyStored(appearance: Appearance): Int {
        StoredAppearance(FakeUserPreferencesRepository(UserPrefs(appearance = appearance)), errors).applyBlocking()
        return AppCompatDelegate.getDefaultNightMode()
    }

    @Test
    fun `the stored appearance becomes the default night mode`() {
        assertThat(applyStored(Appearance.DARK)).isEqualTo(AppCompatDelegate.MODE_NIGHT_YES)
        assertThat(applyStored(Appearance.LIGHT)).isEqualTo(AppCompatDelegate.MODE_NIGHT_NO)
        assertThat(applyStored(Appearance.SYSTEM)).isEqualTo(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `a failed read is recorded and keeps the system mode`() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        val failing = object : UserPreferencesRepository by FakeUserPreferencesRepository() {
            override suspend fun snapshot(): UserPrefs = throw IOException("unreadable")
        }

        StoredAppearance(failing, errors).applyBlocking()

        assertThat(AppCompatDelegate.getDefaultNightMode()).isEqualTo(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        assertThat(errors.recorded.map { it.message }).containsExactly("unreadable")
    }
}
