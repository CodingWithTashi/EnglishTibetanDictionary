package com.kharagedition.tibetandictionary.flows

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

/**
 * First run (dictionary → direction → text size → Home) on fresh prefs: the pack step comes first with the stored
 * pack preselected, the direction picked in step 2 becomes the stored default, and Skip reaches Home onboarded too.
 */
@HiltAndroidTest
class OnboardingFlowTest : AppFlowTest() {

    /** Fresh prefs: not onboarded, default direction `bo-en`. */
    override suspend fun preparePrefs(prefs: UserPreferencesRepository) = Unit

    @Before
    fun onFirstStep() = waitFor(hasTestTag("onboarding_step_1"))

    @Test
    fun pickingEnglishToTibetanAndContinuingTwiceReachesHomeWithThatDefault() {
        // Step 1: the dictionary. Without a Bhutanese SIM the stored (default) pack, Tibetan, is preselected.
        PACKS.forEach { waitFor(hasTestTag("onboarding_pack_$it")) }
        compose.onNodeWithTag("onboarding_pack_bo").assertIsSelected()
        compose.onNodeWithTag("onboarding_continue").performClick()

        // Step 2: the direction.
        waitFor(hasTestTag("onboarding_step_2"))
        DIRECTIONS.forEach { waitFor(hasTestTag("onboarding_direction_$it")) }
        // The stored default is preselected until the user picks another.
        compose.onNodeWithTag("onboarding_direction_bo-en").assertIsSelected()

        compose.onNodeWithTag("onboarding_direction_en-bo").performScrollTo().performClick()
        waitFor(hasTestTag("onboarding_direction_en-bo") and isSelected())
        compose.onNodeWithTag("onboarding_continue").performClick()

        waitFor(hasTestTag("onboarding_step_3"))
        // Skip is offered on every step but the last.
        compose.onNodeWithTag("onboarding_skip").assertDoesNotExist()
        compose.onNodeWithTag("onboarding_continue").performClick()

        waitForHome()
        val stored = awaitPrefs { it.onboarded && it.defaultDirectionId == "en-bo" }
        assertThat(stored.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(stored.defaultDirectionId).isEqualTo("en-bo")
        // Home's switcher starts on the new default, which takes the first segment.
        waitFor(hasTestTag("direction_segment_en-bo") and isSelected())
        val first = compose.onNodeWithTag("direction_segment_en-bo").getBoundsInRoot().left
        listOf("bo-en", "bo-bo").forEach {
            assertThat(first).isLessThan(compose.onNodeWithTag("direction_segment_$it").getBoundsInRoot().left)
        }
    }

    @Test
    fun skipOnTheFirstStepReachesHomeOnboarded() {
        compose.onNodeWithTag("onboarding_skip").performClick()

        waitForHome()
        val stored = awaitPrefs { it.onboarded }
        // Skip stores what the steps show: the preselected default.
        assertThat(stored.defaultDirectionId).isEqualTo("bo-en")
        assertThat(stored.dictionaryPack).isEqualTo(DictionaryPack.TIBETAN)
        waitForGone(hasTestTag("onboarding_screen"))
    }

    private companion object {
        val PACKS = listOf("bo", "dz")

        /** The three directions the release data ships, all with entries. */
        val DIRECTIONS = listOf("bo-en", "bo-bo", "en-bo")
    }
}
