package com.kharagedition.tibetandictionary

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.di.DataStoreModule
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepositoryImpl
import com.kharagedition.tibetandictionary.ui.app.APP_ROOT_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Final review M4: with Dark stored, a cold start draws in dark from the first frame and never recreates
 * [MainActivity] to switch theme. Runs the real app ([DictionaryApplication], its Hilt graph and the installer).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = DictionaryApplication::class)
class ColdStartThemeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<DictionaryApplication>()

    /** The app's own prefs file (the DataStore delegate keeps one instance per process). */
    private val prefs = UserPreferencesRepositoryImpl(DataStoreModule.provideUserPrefsDataStore(app), DictionaryPack.TIBETAN)

    /** The night mode and the DataStore instance are process-wide: leave them as other tests expect them. */
    @After
    fun reset() {
        runBlocking { prefs.setAppearance(Appearance.SYSTEM) }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun `a cold start with Dark stored is dark from the start and is never recreated`() {
        runBlocking { prefs.setAppearance(Appearance.DARK) }
        // A new process reads it in DictionaryApplication.onCreate, before the activity exists.
        app.storedAppearance.applyBlocking()
        val created = mutableListOf<Activity>()
        app.registerActivityLifecycleCallbacks(CreatedActivities(created))

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(timeoutMillis = 60_000) {
                compose.onAllNodesWithTag(APP_ROOT_TAG).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()

            assertThat(created).hasSize(1)
            val nightMask = created.single().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            assertThat(nightMask).isEqualTo(Configuration.UI_MODE_NIGHT_YES)
        }
    }

    private class CreatedActivities(private val into: MutableList<Activity>) : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) into += activity
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
