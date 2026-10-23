package com.kharagedition.tibetandictionary.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainActivity
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.di.DatabaseModule
import com.kharagedition.tibetandictionary.core.di.FirebaseModule
import com.kharagedition.tibetandictionary.core.di.RepositoryModule
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.InstallState
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import com.kharagedition.tibetandictionary.data.local.inMemoryUserDb
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.RecentSearchesRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepositoryImpl
import com.kharagedition.tibetandictionary.data.repository.SavedTranslationsRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationHistoryRepository
import com.kharagedition.tibetandictionary.data.repository.TranslationRecord
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.Quota
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslationApi
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import com.kharagedition.tibetandictionary.fakes.FakeActivityRepository
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeDictionarySwitcher
import com.kharagedition.tibetandictionary.fakes.FakeInstaller
import com.kharagedition.tibetandictionary.fakes.FakeRecentSearchesRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedTranslationsRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationApi
import com.kharagedition.tibetandictionary.fakes.FakeTranslationHistoryRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.FakeWordOfTheDayRepository
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import com.kharagedition.tibetandictionary.ui.main.MainViewModel
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
 *
 * [DatabaseModule] is uninstalled too, so the graph holds a [DictionaryDatabases] the tests can [activate][
 * DictionaryDatabases.activate] to simulate a finished pack switch; nothing in this graph reads Room through it.
 */
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, DatabaseModule::class, FirebaseModule::class)
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
    val databases: DictionaryDatabases = DictionaryDatabases { inMemoryDictionaryDb() }

    private val fakeSwitcher = FakeDictionarySwitcher()

    @BindValue @JvmField
    val switcher: DictionarySwitcher = fakeSwitcher

    @BindValue @JvmField
    val prefs: UserPreferencesRepository = fakePrefs

    @BindValue @JvmField
    val dictionary: DictionaryRepository = FakeDictionaryRepository()

    private val userDb: UserDatabase by lazy { inMemoryUserDb() }

    /** The real repository over an in-memory user.db and this graph's [databases]: the scoping is not faked. */
    @BindValue @JvmField
    val saved: SavedRepository = SavedRepositoryImpl(userDb, databases, FakeClock(), Dispatchers.Unconfined)

    @BindValue @JvmField
    val recents: RecentSearchesRepository = FakeRecentSearchesRepository()

    @BindValue @JvmField
    val activityLog: ActivityRepository = FakeActivityRepository()

    @BindValue @JvmField
    val wordOfTheDay: WordOfTheDayRepository = FakeWordOfTheDayRepository()

    private val fakeTranslation = FakeTranslationRepository()

    @BindValue @JvmField
    val translation: TranslationRepository = fakeTranslation

    @BindValue @JvmField
    val translationHistory: TranslationHistoryRepository = FakeTranslationHistoryRepository()

    private val fakeSavedTranslations = FakeSavedTranslationsRepository()

    @BindValue @JvmField
    val savedTranslations: SavedTranslationsRepository = fakeSavedTranslations

    // FirebaseModule's bindings other classes consume; its Firebase-only seams have no consumer here.
    @BindValue @JvmField
    val auth: AuthRepository = FakeAuthRepository()

    @BindValue @JvmField
    val translationApi: TranslationApi = FakeTranslationApi()

    @BindValue @JvmField
    val analytics: Analytics = RecordingAnalytics()

    @Inject lateinit var bus: NavigationEventBus

    private lateinit var activity: ActivityController<MainActivity>

    private fun launch(onboarded: Boolean, intent: Intent? = null) {
        fakePrefs.prefs.value = UserPrefs(onboarded = onboarded)
        databases.activate(DictionaryPack.TIBETAN)
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
        userDb.close()
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
    fun `the Translate tab shows the translate screen`() {
        launch(onboarded = true)

        tap("nav_translate")

        compose.onNodeWithTag("translate_screen").assertExists()
        compose.onNodeWithTag("nav_translate").assertIsSelected()
    }

    @Test
    fun `back from Translate returns to Search`() {
        launch(onboarded = true)
        tap("nav_translate")

        pressBack()

        assertOnTabs()
        compose.onNodeWithTag("nav_search").assertIsSelected()
        assertThat(activity.get().isFinishing).isFalse()
    }

    @Test
    fun `a translate request opens the Translate tab with its text`() {
        launch(onboarded = true)
        val text = "བཀྲ་ཤིས་བདེ་ལེགས།"
        val main = ViewModelProvider(activity.get())[MainViewModel::class.java]

        main.openTranslate(TranslateRequest(LangPair(Lang.BO, Lang.EN), text))
        compose.waitForIdle()

        compose.onNodeWithTag("translate_screen").assertExists()
        compose.onNodeWithTag("translate_input").assertTextContains(text)
        assertThat(main.translateRequest.value).isNull()
    }

    @Test
    fun `Open in Translate from Search pops to the Translate tab with the text`() {
        // A 3-character limit makes "house" too long for a quick translation, so Search offers Open in Translate.
        fakeTranslation.quota.value = QuotaState(Quota(Plan.FREE, 0, 10, Instant.EPOCH, maxChars = 3), freeLimit = 10)
        launch(onboarded = true)
        openLink("app://search?query=house")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("search_translate_open").fetchSemanticsNodes().isNotEmpty()
        }

        tap("search_translate_open")

        compose.onNodeWithTag("translate_screen").assertExists()
        compose.onNodeWithTag("nav_translate").assertIsSelected()
        compose.onNodeWithTag("translate_input").assertTextContains("house")
        pressBack()
        assertOnTabs()
    }

    @Test
    fun `a saved translation opens in the Translate tab with its result`() {
        val pair = LangPair(Lang.BO, Lang.EN)
        fakeSavedTranslations.records.value =
            listOf(TranslationRecord(1, pair, "བཀའ་དྲིན་ཆེ།", "Thank you.", Provider.GOOGLE, Instant.EPOCH))
        launch(onboarded = true)
        tap("nav_saved")
        // The switch shows once the saved words are read, off the main thread.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("saved_tab_translations").fetchSemanticsNodes().isNotEmpty()
        }
        tap("saved_tab_translations")

        tap("saved_translation_1")

        compose.onNodeWithTag("translate_screen").assertExists()
        compose.onNodeWithTag("nav_translate").assertIsSelected()
        compose.onNodeWithTag("translate_input").assertTextContains("བཀའ་དྲིན་ཆེ།")
        compose.onNodeWithTag("translate_result").assertExists()
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

    @Test
    fun `a completed switch pops to the Search tab of the new pack`() {
        launch(onboarded = true)
        tap("nav_profile")
        openLink("app://entry/$TASHI")
        compose.onNodeWithTag("entry_screen").assertExists()

        databases.activate(DictionaryPack.DZONGKHA)
        compose.waitForIdle()

        compose.onNodeWithTag("entry_screen").assertDoesNotExist()
        compose.onNodeWithTag("home_screen").assertExists()
        compose.onNodeWithTag("nav_search").assertIsSelected()
        pressBack()
        assertThat(activity.get().isFinishing).isTrue()
    }

    @Test
    fun `a switch during onboarding does not pop`() {
        launch(onboarded = false)

        databases.activate(DictionaryPack.DZONGKHA)
        compose.waitForIdle()

        compose.onNodeWithTag("onboarding_screen").assertExists()
    }

    @Test
    fun `the overlay hides the app below from accessibility but keeps it mounted`() {
        launch(onboarded = true)
        openLink("app://entry/$TASHI")

        fakeSwitcher.state.value = SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.5f)
        compose.waitForIdle()

        compose.onNodeWithTag("switching_overlay").assertExists()
        // Not reachable by accessibility services (merged tree), yet still composed (unmerged tree).
        compose.onNodeWithTag("entry_screen").assertDoesNotExist()
        compose.onNodeWithTag("entry_screen", useUnmergedTree = true).assertExists()
        pressBack()
        assertThat(activity.get().isFinishing).isFalse()

        fakeSwitcher.state.value = SwitchState.Idle
        compose.waitForIdle()

        compose.onNodeWithTag("switching_overlay").assertDoesNotExist()
        compose.onNodeWithTag("entry_screen").assertExists()
        pressBack()
        assertOnTabs()
    }

    @Test
    fun `a held link after a pack change opens on top of the new Main`() {
        launch(onboarded = true)
        openLink("app://entry/$TASHI")
        fakeSwitcher.state.value = SwitchState.InProgress(DictionaryPack.DZONGKHA, null)
        compose.waitForIdle()
        bus.emit(DeepLink.OpenEntry(TASHI_DELEK))

        databases.activate(DictionaryPack.DZONGKHA)
        fakeSwitcher.state.value = SwitchState.Idle
        compose.waitForIdle()

        assertOnEntry(seeAlso = TASHI)
        pressBack()
        // Nothing from before the switch is left under the linked entry.
        assertOnTabs()
        compose.onNodeWithTag("nav_search").assertIsSelected()
        pressBack()
        assertThat(activity.get().isFinishing).isTrue()
    }

    @Test
    fun `a deep link during a switch is held until it ends`() {
        launch(onboarded = true)
        fakeSwitcher.state.value = SwitchState.InProgress(DictionaryPack.DZONGKHA, null)
        compose.waitForIdle()

        openLink("app://entry/$TASHI")

        compose.onNodeWithTag("entry_screen").assertDoesNotExist()

        fakeSwitcher.state.value = SwitchState.Idle
        compose.waitForIdle()

        compose.onNodeWithTag("entry_screen").assertExists()
    }

    @Test
    fun `activating the other pack directly hides a saved word and activating the first again brings it back`() {
        launch(onboarded = true)
        runBlocking { saved.save(SampleData.tashi, folderId = null) }
        val row = "saved_row_${TASHI}"
        tap("nav_saved")
        awaitNode(row)

        databases.activate(DictionaryPack.DZONGKHA)
        compose.waitForIdle()
        tap("nav_saved")
        awaitNode("saved_empty_cta")
        compose.onNodeWithTag(row).assertDoesNotExist()

        databases.activate(DictionaryPack.TIBETAN)
        compose.waitForIdle()
        tap("nav_saved")
        awaitNode(row)
    }

    /** Room delivers its first emission off the main thread. */
    private fun awaitNode(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
