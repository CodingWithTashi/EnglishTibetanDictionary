package com.kharagedition.tibetandictionary.flows

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.install.SwitchState
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Before
import org.junit.Test

/**
 * The two-dictionary loop on the real assets: save a Tibetan word, switch to Dzongkha from Profile (the first switch
 * copies the bundled `dz.db`), find the same query in Dzongkha with an empty Saved tab, then switch back and find
 * the Tibetan word still saved. Prefs start onboarded on the Tibetan pack.
 */
@HiltAndroidTest
class PackSwitchFlowTest : AppFlowTest() {

    @Inject lateinit var switcher: DictionarySwitcher

    @Before
    fun onHome() = waitForHome()

    @Test
    fun switchingToDzongkhaAndBackKeepsEachPacksSavedWordsAndData() {
        // Tibetan: search, open the first result (the exact match) and save it.
        openSearchFromHome()
        typeQuery(QUERY)
        waitFor(hasTestTagPrefix(RESULT_ROW))
        val boRow = tagOf(hasTestTagPrefix(RESULT_ROW))
        val boEntryId = boRow.removePrefix(RESULT_ROW).toLong()
        tapAndWait(hasTestTag(boRow), hasTestTag("entry_screen"))
        compose.onNodeWithTag("entry_save").performClick()
        awaitSavedInDatabase(boEntryId)
        leaveEntry()
        leaveSearch()

        // Profile → Dictionary → Dzongkha: the overlay comes up, then the app lands on Home in the Dzongkha pack.
        switchTo("dz")
        awaitPrefs { it.dictionaryPack == DictionaryPack.DZONGKHA }
        waitFor(hasTestTag("direction_segment_dz-en") and isSelected())
        assertThat(exists(hasTestTag("direction_segment_bo-en"))).isFalse()

        // Saved words are per pack: none here.
        openTab("saved", "saved_screen")
        compose.onAllNodes(hasTestTagPrefix(SAVED_ROW)).assertCountEquals(0)
        openTab("search", "home_screen")

        // The same query now searches the Dzongkha dictionary.
        openSearchFromHome()
        typeQuery(QUERY)
        waitFor(hasTestTag("$RESULT_ROW$DZ_ENTRY_ID"))
        tapAndWait(hasTestTag("$RESULT_ROW$DZ_ENTRY_ID"), hasTestTag("entry_screen"))
        leaveEntry()
        leaveSearch()

        // Back to Tibetan: its saved word is still there.
        switchTo("bo")
        awaitPrefs { it.dictionaryPack == DictionaryPack.TIBETAN }
        waitFor(hasTestTag("direction_segment_bo-en"))
        assertThat(exists(hasTestTag("direction_segment_dz-en"))).isFalse()
        openTab("saved", "saved_screen")
        waitFor(hasTestTag("$SAVED_ROW$boEntryId"))
        assertThat(awaitPrefs { it.dictionaryPack == DictionaryPack.TIBETAN }.dictionaryPack)
            .isEqualTo(DictionaryPack.TIBETAN)
    }

    /**
     * Profile → Dictionary → the card of pack [id]. The full-screen overlay must appear and then go; the first
     * switch to a pack copies tens of MB, so the wait is generous. Home follows the switch.
     */
    private fun switchTo(id: String) {
        openTab("profile", "profile_screen")
        tapAndWait(hasTestTag("profile_row_dictionary"), hasTestTag("dictionary_option_$id"))
        // The overlay shows exactly while the switcher is InProgress, which can be shorter than one poll of the
        // semantics tree once the pack's file is in place; an unconfined collector sees every change of that state,
        // and the overlay tag is checked on the screen as well whenever a poll lands inside the window.
        val pack = DictionaryPack.entries.first { it.id == id }
        val inProgress = AtomicBoolean(false)
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            switcher.state.collect { if (it is SwitchState.InProgress && it.pack == pack) inProgress.set(true) }
        }
        try {
            compose.onNode(hasTestTag("dictionary_option_$id")).performClick()
            compose.waitUntil("pack $id stored", SWITCH_TIMEOUT_MS) { storedPack() == pack }
            compose.waitUntil("overlay gone", SWITCH_TIMEOUT_MS) { !exists(hasTestTag("switching_overlay")) }
            waitFor(hasTestTag("home_screen"), SWITCH_TIMEOUT_MS)
        } finally {
            collector.cancel()
        }
        assertWithMessage("switcher state while switching to $id").that(inProgress.get()).isTrue()
        assertThat(switcher.state.value).isEqualTo(SwitchState.Idle)
    }

    private fun storedPack(): DictionaryPack = runBlocking { prefs.prefs.first().dictionaryPack }

    /** The save reached `user.db`, not only the screen. */
    private fun awaitSavedInDatabase(entryId: Long) = runBlocking {
        withTimeout(UI_TIMEOUT_MS) { userDatabase.savedEntryDao().observeIsSaved(entryId).first { it } }
    }

    private companion object {
        const val QUERY = "ཆུ"
        const val RESULT_ROW = "result_row_"
        const val SAVED_ROW = "saved_row_"
        const val SWITCH_TIMEOUT_MS = 60_000L

        /** "ཆུ་" in the Dzongkha dictionary (dz-en). */
        const val DZ_ENTRY_ID = 4006006L
    }
}
