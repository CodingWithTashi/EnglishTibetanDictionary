package com.kharagedition.tibetandictionary.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainActivity
import com.kharagedition.tibetandictionary.SampleData
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
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
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
 * back between routes. Every destination but Learn is the real screen (entry 42 is not one of the sample entries, so
 * it shows the missing notice).
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
    val installer: DictionaryInstaller =
        FakeInstaller().apply { state.value = InstallState.Ready(DictionaryPack.TIBETAN, totalEntries = 3) }

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

    private fun launch(onboarded: Boolean, intent: Intent? = null) {
        fakePrefs.prefs.value = UserPrefs(onboarded = onboarded)
        hilt.inject()
        activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        compose.waitForIdle()
    }

    private fun linkIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    private fun openLink(uri: String) {
        activity.newIntent(linkIntent(uri))
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun pressBack() {
        activity.get().onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    private fun assertOnTabs() {
        compose.onNodeWithTag("home_screen").assertExists()
        compose.onNodeWithTag("nav_search").assertExists()
    }

    /** Taps the See also row that opens entry [id]. */
    private fun openSeeAlso(id: Long) {
        compose.onNodeWithTag("entry_related_$id").performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** The entry on top is the one whose See also links to [seeAlso] (tashi and tashi delek link to each other). */
    private fun assertOnEntry(seeAlso: Long) {
        compose.onNodeWithTag("entry_screen").assertExists()
        compose.onNodeWithTag("entry_related_$seeAlso").assertExists()
    }

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.pause().stop().destroy()
    }

    private companion object {
        val TASHI = SampleData.tashi.id
        val TASHI_DELEK = SampleData.tashiDelek.id
    }

    @Test
    fun `an onboarded user starts on the Search tab`() {
        launch(onboarded = true)

        assertOnTabs()
    }

    @Test
    fun `a new user starts on onboarding without the bottom bar`() {
        launch(onboarded = false)

        compose.onNodeWithTag("onboarding_screen").assertExists()
        compose.onNodeWithTag("nav_search").assertDoesNotExist()
    }

    @Test
    fun `back after finishing onboarding leaves the app instead of returning to onboarding`() {
        launch(onboarded = false)

        tap("onboarding_continue")
        tap("onboarding_continue")
        assertOnTabs()

        pressBack()

        compose.onNodeWithTag("onboarding_screen").assertDoesNotExist()
        assertThat(activity.get().isFinishing).isTrue()
    }

    @Test
    fun `links are ignored during onboarding`() {
        launch(onboarded = false)

        bus.emit(DeepLink.OpenEntry(42))
        compose.waitForIdle()

        compose.onNodeWithTag("onboarding_screen").assertExists()
        compose.onNodeWithTag("entry_screen").assertDoesNotExist()
    }

    @Test
    fun `an entry link pushes the entry without the bar and back returns to the tabs`() {
        launch(onboarded = true)

        openLink("app://entry/42")

        compose.onNodeWithTag("entry_screen").assertExists()
        compose.onNodeWithTag("nav_search").assertDoesNotExist()

        pressBack()

        assertOnTabs()
    }

    @Test
    fun `a search link opens search with its query`() {
        launch(onboarded = true)

        openLink("app://search?query=bkra")

        compose.onNodeWithTag("search_screen").assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("bkra")
        compose.onNodeWithTag("nav_search").assertDoesNotExist()
    }

    @Test
    fun `a cold-start entry link opens the entry over the tabs`() {
        launch(onboarded = true, intent = linkIntent("app://entry/42"))

        compose.onNodeWithTag("entry_screen").assertExists()
        // The link is taken off the intent, so NavController never handles it a second time.
        assertThat(activity.get().intent.data).isNull()

        pressBack()

        assertOnTabs()
    }

    @Test
    fun `a cold-start entry link waits for onboarding`() {
        launch(onboarded = false, intent = linkIntent("app://entry/42"))

        compose.onNodeWithTag("onboarding_screen").assertExists()
        compose.onNodeWithTag("entry_screen").assertDoesNotExist()
        assertThat(activity.get().intent.data).isNull()
    }

    @Test
    fun `a link replayed from Recents is not followed again`() {
        val replay = linkIntent("app://entry/42").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        launch(onboarded = true, intent = replay)

        assertOnTabs()
        compose.onNodeWithTag("entry_screen").assertDoesNotExist()
    }

    @Test
    fun `see-also on an entry pushes another entry`() {
        launch(onboarded = true)
        openLink("app://entry/$TASHI")

        openSeeAlso(TASHI_DELEK)
        assertOnEntry(seeAlso = TASHI)

        pressBack()
        assertOnEntry(seeAlso = TASHI_DELEK)

        pressBack()
        assertOnTabs()
    }

    @Test
    fun `back from About returns to the Profile tab`() {
        launch(onboarded = true)
        tap("nav_profile")
        compose.onNodeWithTag("profile_link_about").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("about_screen").assertExists()
        compose.onNodeWithTag("nav_profile").assertDoesNotExist()

        pressBack()

        compose.onNodeWithTag("profile_screen").assertExists()
        compose.onNodeWithTag("nav_profile").assertIsSelected()
    }

    @Test
    fun `About opens the licences and back returns to About`() {
        launch(onboarded = true)
        tap("nav_profile")
        compose.onNodeWithTag("profile_link_about").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("about_licences").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("licences_screen").assertExists()
        // The texts load off the main thread.
        val heading = "Dictionary data · GNU General Public License, version 3"
        compose.waitUntil(5_000) { compose.onAllNodesWithText(heading).fetchSemanticsNodes().isNotEmpty() }

        pressBack()

        compose.onNodeWithTag("licences_screen").assertDoesNotExist()
        compose.onNodeWithTag("about_screen").assertExists()
    }

    @Test
    fun `a double tap on back during the transition pops one screen`() {
        launch(onboarded = true)
        openLink("app://entry/$TASHI")
        openSeeAlso(TASHI_DELEK)
        assertOnEntry(seeAlso = TASHI)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("entry_back").performClick()
        compose.onNodeWithTag("entry_back").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertOnEntry(seeAlso = TASHI_DELEK)
    }
}
