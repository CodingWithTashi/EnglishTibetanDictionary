package com.kharagedition.tibetandictionary.flows

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.MainActivity
import com.kharagedition.tibetandictionary.core.init.AppInitializer
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.install.DictionaryInstaller
import com.kharagedition.tibetandictionary.data.local.UserDatabase
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.ui.app.APP_ROOT_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Base of the end-to-end flows: the real app (real Hilt bindings, the installed `dictionary.db`) on a device.
 *
 * The test application is `HiltTestApplication`, so nothing starts [AppInitializer] on its own; [freshApp] does,
 * after it has reset the user state, and before [compose] launches [MainActivity]. Each test therefore starts like
 * a fresh install whose dictionary is already copied:
 * - prefs: the `user_prefs` DataStore is cleared through the process-wide instance (deleting its file would not
 *   reach the copy that instance keeps in memory), then [preparePrefs] sets what the flow needs;
 * - `user.db`: every table is emptied through this test's Room instance (earlier tests' instances keep the file
 *   open, so it is not deleted);
 * - night mode, which outlives an activity, goes back to "follow the system" before the launch and again once the
 *   activity has closed; a per-app language other than English (a Tibetan UI left by a failed run) is dropped at the
 *   same points. The flows read English labels, so the device's own language must be English.
 * `dictionary.db` is never touched: the first test of a fresh install copies it (up to [INSTALL_TIMEOUT_MS]) and
 * every later test finds it in place.
 */
abstract class AppFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** Resets the user state and starts the app's initializer before the activity launches (see the class doc). */
    @get:Rule(order = 1)
    val freshApp: TestRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                hilt.inject()
                resetSystemUiState()
                resetUserState()
                initializer.start()
                try {
                    base.evaluate()
                } finally {
                    // The activity is closed by now; a Tibetan UI or night mode set by the test must not leak.
                    resetSystemUiState()
                }
            }
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var prefs: UserPreferencesRepository

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var userDatabase: UserDatabase

    @Inject lateinit var initializer: AppInitializer

    @Inject lateinit var installer: DictionaryInstaller

    /** The prefs this flow starts from, after they were cleared to the defaults. Default: past onboarding. */
    protected open suspend fun preparePrefs(prefs: UserPreferencesRepository) {
        prefs.setOnboarded(true)
    }

    /** Waits out the splash and, on the first run after an install, the dictionary copy. */
    @Before
    fun waitForApp() {
        waitFor(hasTestTag(APP_ROOT_TAG), INSTALL_TIMEOUT_MS) { "installer state: ${installer.state.value}" }
    }

    private fun resetUserState() {
        runBlocking {
            dataStore.edit { it.clear() }
            prefs.setLegacyCleanupDone(true)
            preparePrefs(prefs)
        }
        userDatabase.clearAllTables()
    }

    private fun resetSystemUiState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        dropNonEnglishAppLanguage()
    }

    /**
     * Drops a per-app language other than English (a Tibetan UI left by a failed [BoLocaleSmokeTest]); "follow the
     * system" and English are left alone, so a passing run changes no configuration between tests. From Android 13
     * this goes straight to [LocaleManager] (AppCompat only reaches it through a live activity, and here there is
     * none). The system pushes the new configuration to this process asynchronously, so this then waits until the
     * app's resources have left that language; otherwise the next activity could start in it and be recreated in the
     * middle of its test.
     */
    private fun dropNonEnglishAppLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            val language = manager.applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
            if (language == null || language == AppLanguage.EN.tag) return
            manager.applicationLocales = LocaleList.getEmptyLocaleList()
            val deadline = System.currentTimeMillis() + LOCALE_SETTLE_MS
            while (context.resources.configuration.locales.get(0).language == language &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(POLL_MS)
            }
        } else {
            // Below Android 13 AppCompat applies the language to each activity it creates; none is running.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val language = AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)?.language
                if (language != null && language != AppLanguage.EN.tag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
            }
        }
    }

    // ---- Waiting and finding ----

    /** Whether any node matches [matcher]; false while the activity is being recreated and has no root yet. */
    protected fun exists(matcher: SemanticsMatcher, unmerged: Boolean = false): Boolean = count(matcher, unmerged) > 0

    private fun count(matcher: SemanticsMatcher, unmerged: Boolean): Int = runCatching {
        compose.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes(atLeastOneRootRequired = false).size
    }.getOrDefault(0)

    /**
     * Waits until at least one node matches [matcher]. On timeout the semantics trees go to logcat (tag
     * [LOG_TAG]) and the failure names the matcher plus [detail].
     */
    protected fun waitFor(
        matcher: SemanticsMatcher,
        timeoutMillis: Long = UI_TIMEOUT_MS,
        unmerged: Boolean = false,
        detail: () -> String = { "" },
    ) {
        try {
            compose.waitUntil(matcher.description, timeoutMillis) { exists(matcher, unmerged) }
        } catch (e: ComposeTimeoutException) {
            dumpTrees()
            throw AssertionError("Timed out after $timeoutMillis ms waiting for ${matcher.description}. ${detail()}", e)
        }
    }

    /** Waits until exactly one node matches [matcher] (a screen mid-transition can show a tag twice). */
    protected fun waitForOne(matcher: SemanticsMatcher, timeoutMillis: Long = UI_TIMEOUT_MS, unmerged: Boolean = false) {
        try {
            compose.waitUntil("exactly one: ${matcher.description}", timeoutMillis) { count(matcher, unmerged) == 1 }
        } catch (e: ComposeTimeoutException) {
            dumpTrees()
            throw AssertionError("Timed out after $timeoutMillis ms waiting for one ${matcher.description}", e)
        }
    }

    /** Waits until no node matches [matcher]. */
    protected fun waitForGone(matcher: SemanticsMatcher, timeoutMillis: Long = UI_TIMEOUT_MS) {
        compose.waitUntil("no ${matcher.description}", timeoutMillis) { !exists(matcher) }
    }

    /**
     * Taps [target] and waits for [arrived]. Navigation callbacks run only while their destination is resumed, so a
     * tap that lands while a transition is still settling is dropped by design; the tap is then repeated. Only for
     * taps that navigate (repeating them cannot do anything twice).
     */
    protected fun tapAndWait(target: SemanticsMatcher, arrived: SemanticsMatcher, attempts: Int = 3) {
        repeat(attempts) {
            compose.waitUntil("${target.description} or ${arrived.description}", UI_TIMEOUT_MS) {
                exists(arrived) || count(target, unmerged = false) == 1
            }
            if (exists(arrived)) return
            // The target can leave between the check and the tap when an earlier tap is still landing.
            runCatching { compose.onNode(target).performClick() }
            val reached = runCatching {
                compose.waitUntil(arrived.description, RETRY_WAIT_MS) { exists(arrived) }
            }.isSuccess
            if (reached) return
        }
        waitFor(arrived)
    }

    private fun dumpTrees() {
        runCatching { compose.onAllNodes(isRoot()).printToLog(LOG_TAG) }
        runCatching { compose.onAllNodes(isRoot(), useUnmergedTree = true).printToLog(LOG_TAG) }
    }

    /** The test tag of the first node matching [matcher], in semantics-tree order. */
    protected fun tagOf(matcher: SemanticsMatcher): String =
        compose.onAllNodes(matcher).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]

    /** Polls the stored prefs until [predicate] holds (writes land after the tap that made them). */
    protected fun awaitPrefs(predicate: (UserPrefs) -> Boolean): UserPrefs = runBlocking {
        withTimeout(UI_TIMEOUT_MS) { prefs.prefs.first(predicate) }
    }

    /** The resumed activity, read on the main thread; null while it is being recreated. */
    protected fun resumedActivity(): Activity? {
        var resumed: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return resumed
    }

    // ---- App steps ----

    protected fun waitForHome() = waitFor(hasTestTag("home_screen"))

    /** Bottom-bar tab [name] (`search`, `saved` or `profile`), waiting for its screen tag [screenTag]. */
    protected fun openTab(name: String, screenTag: String) = tapAndWait(hasTestTag("nav_$name"), hasTestTag(screenTag))

    /** Home's search field opens the Search route with its editable field. */
    protected fun openSearchFromHome() = tapAndWait(hasTestTag("home_search_field"), editableSearchField)

    protected fun typeQuery(text: String) {
        waitForOne(editableSearchField)
        compose.onNode(editableSearchField).performTextInput(text)
    }

    /** The Search route's back button, which returns to the tabs. */
    protected fun leaveSearch() = tapAndWait(searchBack, hasTestTag("home_screen"))

    protected fun leaveEntry() = tapAndWait(hasTestTag("entry_back"), hasTestTag("search_screen"))

    /** Fails unless the app root, captured from the window, is [expected] at a few points of its left margin. */
    @RequiresApi(Build.VERSION_CODES.O)
    protected fun assertBackground(expected: Color, screen: String) {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(APP_ROOT_TAG).captureToImage().toPixelMap()
        // A few pixels in from the left edge, inside every screen's side padding, at several heights.
        for (fraction in listOf(0.3f, 0.45f, 0.6f)) {
            val y = (pixels.height * fraction).toInt()
            val actual = pixels[MARGIN_SAMPLE_X, y]
            assertWithMessage(
                "$screen background at ($MARGIN_SAMPLE_X, $y) is ${actual.hex()}, expected ${expected.hex()}",
            ).that(actual.isCloseTo(expected)).isTrue()
        }
    }

    private fun Color.isCloseTo(other: Color): Boolean =
        abs(red - other.red) <= COLOR_TOLERANCE && abs(green - other.green) <= COLOR_TOLERANCE &&
            abs(blue - other.blue) <= COLOR_TOLERANCE

    private fun Color.hex(): String = "#%08X".format(toArgb())

    companion object {
        /** The first launch after an install copies the 87 MB dictionary before the app shows. */
        const val INSTALL_TIMEOUT_MS = 180_000L
        const val UI_TIMEOUT_MS = 10_000L
        private const val RETRY_WAIT_MS = 3_000L
        private const val LOCALE_SETTLE_MS = 5_000L
        private const val POLL_MS = 50L
        private const val LOG_TAG = "AppFlowTest"

        /** Two pixel steps at 8 bits per channel: room for colour-space rounding, not for another token. */
        private const val COLOR_TOLERANCE = 2.5f / 255f
        private const val MARGIN_SAMPLE_X = 4

        val editableSearchField: SemanticsMatcher = hasTestTag("search_field") and hasSetTextAction()

        private val searchBack: SemanticsMatcher =
            hasContentDescription("Back") and hasClickAction() and hasAnyAncestor(hasTestTag("search_screen"))

        /** Nodes whose test tag starts with [prefix], such as `result_row_`. */
        fun hasTestTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher("TestTag starts with '$prefix'") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

        /** Nodes with a text (not an editable one) that starts with [prefix]. */
        fun hasTextStartingWith(prefix: String): SemanticsMatcher = SemanticsMatcher("Text starts with '$prefix'") {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith(prefix) } == true
        }
    }
}
