package com.kharagedition.tibetandictionary.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kharagedition.tibetandictionary.MainActivity
import com.kharagedition.tibetandictionary.core.di.RepositoryModule
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.fakes.FakeActivityRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeInstaller
import com.kharagedition.tibetandictionary.fakes.FakeRecentSearchesRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.FakeWordOfTheDayRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The shell on a Hilt graph with fake repositories and an installed dictionary: start destination, app links and
 * back between routes. The destinations are still stubs that only show their route name.
 */
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class AppNavHostTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val fakePrefs = FakeUserPreferencesRepository()

    @BindValue @JvmField
    val installer: DictionaryInstaller = FakeInstaller().apply { state.value = InstallState.Ready(totalEntries = 3) }

    @BindValue @JvmField
    val prefs: UserPreferencesRepository = fakePrefs

    @BindValue @JvmField
    val dictionary: DictionaryRepository = FakeDictionaryRepository()

    @BindValue @JvmField
    val saved: SavedRepository = FakeSavedRepository()

    @BindValue @JvmField
    val recents: RecentSearchesRepository = FakeRecentSearchesRepository()

    @BindValue @JvmField
    val activityLog: ActivityRepository = FakeActivityRepository()

    @BindValue @JvmField
    val wordOfTheDay: WordOfTheDayRepository = FakeWordOfTheDayRepository()

    @Inject lateinit var bus: NavigationEventBus

    private lateinit var activity: ActivityController<MainActivity>

    private fun launch(onboarded: Boolean) {
        fakePrefs.prefs.value = UserPrefs(onboarded = onboarded)
        hilt.inject()
        activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        compose.waitForIdle()
    }

    private fun openLink(uri: String) {
        activity.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        compose.waitForIdle()
    }

    private fun pressBack() {
        activity.get().onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.pause().stop().destroy()
    }

    @Test
    fun `an onboarded user starts on the Search tab`() {
        launch(onboarded = true)

        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithTag("nav_search").assertExists()
    }

    @Test
    fun `a new user starts on onboarding without the bottom bar`() {
        launch(onboarded = false)

        compose.onNodeWithText("Onboarding").assertExists()
        compose.onNodeWithTag("nav_search").assertDoesNotExist()
    }

    @Test
    fun `links are ignored during onboarding`() {
        launch(onboarded = false)

        bus.emit(DeepLink.OpenEntry(42))
        compose.waitForIdle()

        compose.onNodeWithText("Onboarding").assertExists()
        compose.onNodeWithTag("entry_screen").assertDoesNotExist()
    }

    @Test
    fun `an entry link pushes the entry without the bar and back returns to the tabs`() {
        launch(onboarded = true)

        openLink("app://entry/42")

        compose.onNodeWithTag("entry_screen").assertExists()
        compose.onNodeWithTag("nav_search").assertDoesNotExist()

        pressBack()

        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithTag("nav_search").assertExists()
    }

    @Test
    fun `a search link opens search`() {
        launch(onboarded = true)

        openLink("app://search?query=bkra")

        compose.onNodeWithText("Search").assertExists()
        compose.onNodeWithTag("nav_search").assertDoesNotExist()
    }
}
