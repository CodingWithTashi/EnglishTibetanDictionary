package com.kharagedition.tibetandictionary.ui.translate

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.ui.pixelsOf
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

private val BO_EN = LangPair(Lang.BO, Lang.EN)
private val EN_BO = LangPair(Lang.EN, Lang.BO)

/** Halfway through a pulse. */
private const val PULSE_SAMPLE_MS = 500L

@RunWith(AndroidJUnit4::class)
class TranslateScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<TranslateEvent>()
    private val signIns = mutableListOf<PromptSource>()
    private var state by mutableStateOf(TranslateUiState())

    private val idle = TranslateUiState(
        pair = BO_EN,
        canSwap = true,
        samples = listOf("ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།", "ང་བོད་པ་ཡིན།"),
        pill = UsagePillUi(remaining = 7, limit = 10, low = false),
        plan = Plan.FREE,
        freeLimit = 10,
    )
    private val typed = idle.copy(input = TextFieldValue("ང་བོད་པ་ཡིན།"), length = 12, canTranslate = true)
    private val done = typed.copy(
        phase = TranslatePhase.DONE,
        result = TranslationResultUi(
            text = "I am Tibetan.",
            words = TranslationText.words("I am Tibetan.", Lang.EN),
            cached = true,
            provider = Provider.GOOGLE,
            saved = false,
        ),
    )

    private lateinit var root: View

    private fun show(initial: TranslateUiState) {
        state = initial
        compose.setContent {
            root = LocalView.current
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                TranslateContent(state, onEvent = { events += it }, onSignIn = { signIns += it })
            }
        }
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun `idle shows samples, the disabled button and the counter 0 over 100`() {
        show(idle)

        compose.onNodeWithTag("translate_screen").assertExists()
        compose.onNodeWithTag("translate_sample_0").assertExists()
        compose.onNodeWithTag("translate_sample_1").assertExists()
        compose.onNodeWithTag("translate_button").assertIsNotEnabled()
        compose.onNodeWithTag("translate_counter").assertTextEquals("0 / 100")
        compose.onNodeWithTag("translate_pill").assertTextContains("7 of 10 left")
        compose.onNodeWithText("Tibetan").assertExists()
        compose.onNodeWithText("English").assertExists()
    }

    @Test
    fun `Translate takes the focus off the input, which closes the keyboard`() {
        show(typed)
        val input = compose.onNodeWithTag("translate_input")
        input.performClick().assertIsFocused()

        tap("translate_button")

        input.assertIsNotFocused()
        assertThat(events).contains(TranslateEvent.Translate)
    }

    @Test
    fun `typing sends InputChanged`() {
        show(idle)
        compose.onNodeWithTag("translate_input").performTextInput("ང")
        assertThat((events.last() as TranslateEvent.InputChanged).value.text).isEqualTo("ང")
    }

    @Test
    fun `tapping translate sends Translate`() {
        show(typed)
        compose.onNodeWithTag("translate_button").assertIsEnabled()
        tap("translate_button")
        assertThat(events).containsExactly(TranslateEvent.Translate)
    }

    @Test
    fun `sample, swap and clear send their events`() {
        show(idle)
        tap("translate_sample_1")
        tap("translate_swap")
        assertThat(events).containsExactly(TranslateEvent.SampleTap("ང་བོད་པ་ཡིན།"), TranslateEvent.Swap).inOrder()

        state = typed
        tap("translate_clear")
        assertThat(events.last()).isEqualTo(TranslateEvent.Clear)
    }

    @Test
    fun `samples hide once there is text`() {
        show(typed)
        compose.onNodeWithTag("translate_sample_0").assertDoesNotExist()
    }

    @Test
    fun `the swap button turns half a circle with each tap`() {
        show(idle)
        val swap = compose.onNodeWithTag("translate_swap")
        swap.assert(SemanticsMatcher.expectValue(SwapTurns, 0))

        swap.performClick()

        swap.assert(SemanticsMatcher.expectValue(SwapTurns, 1))
        assertThat(events).containsExactly(TranslateEvent.Swap)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixels
    fun `the skeleton is three bars that pulse`() {
        compose.mainClock.autoAdvance = false
        show(typed.copy(phase = TranslatePhase.LOADING, canTranslate = false))
        compose.mainClock.advanceTimeByFrame()
        val first = compose.pixelsOf(root, "translate_skeleton")
        val x = first.width / 4
        val background = first[x, 2]
        val inBar = (0 until first.height).map { first[x, it] != background }
        assertThat(inBar.zipWithNext().count { (a, b) -> !a && b }).isEqualTo(3)

        compose.mainClock.advanceTimeBy(PULSE_SAMPLE_MS)
        val later = compose.pixelsOf(root, "translate_skeleton")
        val barRow = inBar.indexOf(true) + 2
        assertThat(later[x, barRow]).isNotEqualTo(first[x, barRow])
    }

    @Test
    fun `loading shows Translating and the skeleton`() {
        show(typed.copy(phase = TranslatePhase.LOADING, canTranslate = false))
        compose.onNodeWithTag("translate_button").assertTextContains("Translating…").assertIsNotEnabled()
        compose.onNodeWithTag("translate_skeleton").assertExists()
    }

    @Test
    fun `done shows the result with attribution Google and cache note`() {
        show(done)
        compose.onNodeWithTag("translate_result").assertExists()
        compose.onNodeWithText("Machine translation · Google").assertExists()
        compose.onNodeWithText("From cache · not counted").assertExists()
        compose.onNodeWithText("I").assertExists()
    }

    @Test
    fun `a Microsoft result names Microsoft and has no cache note`() {
        show(done.copy(result = done.result!!.copy(cached = false, provider = Provider.AZURE)))
        compose.onNodeWithText("Machine translation · Microsoft").assertExists()
        compose.onNodeWithText("From cache · not counted").assertDoesNotExist()
    }

    @Test
    fun `tapping word 2 sends WordTap`() {
        show(done)
        tap("translate_word_2")
        assertThat(events).containsExactly(TranslateEvent.WordTap(OutputWord("Tibetan.", "Tibetan")))
    }

    @Test
    fun `a multi-line result keeps every word`() {
        val text = "Where is the monastery?\nIt is far."
        show(done.copy(result = done.result!!.copy(text = text, words = TranslationText.words(text, Lang.EN))))
        for (index in 0..6) compose.onNodeWithTag("translate_word_$index").assertExists()
        compose.onNodeWithTag("translate_word_7").assertDoesNotExist()
    }

    @Test
    fun `copy, share and save send their events`() {
        show(done)
        tap("translate_copy")
        tap("translate_share")
        tap("translate_save")
        assertThat(events).containsExactly(TranslateEvent.Copy, TranslateEvent.Share, TranslateEvent.ToggleSave).inOrder()
    }

    @Test
    fun `guest card says 7 more and its buttons sign in or look up`() {
        show(typed.copy(block = TranslateBlock.GuestLimit(used = 3, more = 7), canTranslate = false))
        compose.onNodeWithTag("translate_block_guest_limit").assertTextContains("7 more today", substring = true)
        compose.onNodeWithTag("translate_block_guest_limit")
            .assertTextContains("You've used today's 3 free translations", substring = true)
        compose.onNodeWithTag("translate_button").assertDoesNotExist()
        tap("translate_sign_in")
        tap("translate_look_up")
        assertThat(signIns).containsExactly(PromptSource.TRANSLATE_LIMIT)
        assertThat(events).containsExactly(TranslateEvent.LookUpWords)
    }

    @Test
    fun `the guest card puts Continue with Google on its own line, over Look up words`() {
        // Side by side, the label wrapped onto two lines.
        show(typed.copy(block = TranslateBlock.GuestLimit(used = 3, more = 7), canTranslate = false))

        val signIn = compose.onNodeWithTag("translate_sign_in").getUnclippedBoundsInRoot()
        val lookUp = compose.onNodeWithTag("translate_look_up").getUnclippedBoundsInRoot()
        assertThat(lookUp.top).isAtLeast(signIn.bottom)
    }

    @Test
    fun `guest card without a number still offers sign in`() {
        show(typed.copy(block = TranslateBlock.GuestLimit(used = 3, more = null), canTranslate = false))
        compose.onNodeWithTag("translate_block_guest_limit")
            .assertTextContains("Sign in with Google for more translations today", substring = true)
    }

    @Test
    fun `daily limit card upgrades or looks up`() {
        show(typed.copy(block = TranslateBlock.DailyLimit, canTranslate = false))
        compose.onNodeWithTag("translate_block_daily_limit")
            .assertTextContains("Daily limit reached · Resets at midnight", substring = true)
        tap("translate_upgrade")
        tap("translate_look_up")
        assertThat(events).containsExactly(TranslateEvent.OpenPro(PromptSource.TRANSLATE_LIMIT), TranslateEvent.LookUpWords)
            .inOrder()
    }

    @Test
    fun `offline card retries`() {
        show(typed.copy(block = TranslateBlock.Offline))
        compose.onNodeWithTag("translate_block_offline").assertTextContains("You're offline", substring = true)
        tap("translate_retry")
        assertThat(events).containsExactly(TranslateEvent.Retry)
    }

    @Test
    fun `busy card retries`() {
        show(typed.copy(block = TranslateBlock.Busy))
        compose.onNodeWithTag("translate_block_busy")
            .assertTextContains("Translation is busy right now", substring = true)
        tap("translate_retry")
        assertThat(events).containsExactly(TranslateEvent.Retry)
    }

    @Test
    fun `unexpected card retries`() {
        show(typed.copy(block = TranslateBlock.Unexpected))
        compose.onNodeWithTag("translate_block_unexpected").assertTextContains("Something went wrong", substring = true)
        tap("translate_retry")
        assertThat(events).containsExactly(TranslateEvent.Retry)
    }

    @Test
    fun `disabled card has no retry and the input is off`() {
        show(typed.copy(block = TranslateBlock.Disabled, inputEnabled = false, canTranslate = false))
        compose.onNodeWithTag("translate_block_disabled").assertTextContains("Translation is paused", substring = true)
        compose.onNodeWithTag("translate_retry").assertDoesNotExist()
        compose.onNodeWithTag("translate_input").assertIsNotEnabled()
    }

    @Test
    fun `a low pill is tagged translate_pill_low`() {
        show(idle.copy(pill = UsagePillUi(remaining = 2, limit = 10, low = true)))
        compose.onNodeWithTag("translate_pill_low").assertTextContains("2 of 10 left")
        compose.onNodeWithTag("translate_pill").assertDoesNotExist()
    }

    @Test
    fun `an empty pill reads Limit reached and opens Today`() {
        show(idle.copy(pill = UsagePillUi(remaining = 0, limit = 10, low = true)))
        compose.onNodeWithTag("translate_pill_low").assertTextContains("Limit reached")
        tap("translate_pill_low")
        assertThat(events).containsExactly(TranslateEvent.OpenToday)
    }

    @Test
    fun `pro hint opens the pro sheet`() {
        show(typed.copy(showProHint = true, counter = CounterLevel.NEAR, length = 92))
        tap("translate_pro_hint")
        assertThat(events).containsExactly(TranslateEvent.OpenPro(PromptSource.TOO_LONG))
    }

    @Test
    fun `wylie input shows the hint`() {
        show(typed.copy(input = TextFieldValue("bkra shis"), hint = InlineHint.NOT_TIBETAN_SCRIPT, canTranslate = false))
        compose.onNodeWithTag("translate_hint").assertTextContains("Wylie isn't translated", substring = true)
        compose.onNodeWithTag("translate_switch_direction").assertDoesNotExist()
    }

    @Test
    fun `Tibetan with English source offers Switch`() {
        show(typed.copy(pair = EN_BO, hint = InlineHint.LOOKS_TIBETAN, canTranslate = false))
        compose.onNodeWithTag("translate_hint").assertTextContains("This looks like Tibetan", substring = true)
        tap("translate_switch_direction")
        assertThat(events).containsExactly(TranslateEvent.SwitchDirection)
    }

    @Test
    fun `recents restore on tap and clear`() {
        show(idle.copy(recents = listOf(RecentTranslationUi(4, "Thank you", "ཐུགས་རྗེ་ཆེ།", EN_BO))))
        tap("translate_recent_4")
        tap("translate_clear_recents")
        assertThat(events).containsExactly(TranslateEvent.RecentTap(4), TranslateEvent.ClearRecents).inOrder()
    }

    @Test
    fun `the today sheet offers sign in to a guest`() {
        show(
            idle.copy(
                plan = Plan.GUEST,
                pill = UsagePillUi(remaining = 1, limit = 3, low = true),
                sheet = TranslateSheet.TODAY,
            ),
        )
        compose.onNodeWithTag("today_sheet").assertExists()
        compose.onNodeWithText("2 of 3 used today").assertExists()
        compose.onNodeWithText("Sign in to get 10 a day").performClick()
        assertThat(signIns).containsExactly(PromptSource.TODAY_SHEET)
    }

    @Test
    fun `the today sheet offers Pro on the free plan`() {
        show(idle.copy(sheet = TranslateSheet.TODAY))
        compose.onNodeWithText("3 of 10 used today").assertExists()
        compose.onNodeWithText("Upgrade to Pro").performClick()
        assertThat(events).containsExactly(TranslateEvent.OpenPro(PromptSource.TODAY_SHEET))
    }

    @Test
    fun `the pro sheet shows when asked`() {
        show(idle.copy(sheet = TranslateSheet.PRO))
        compose.onNodeWithTag("pro_sheet").assertExists()
    }
}
