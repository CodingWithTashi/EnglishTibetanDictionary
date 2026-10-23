package com.kharagedition.tibetandictionary.ui.search

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.Provider
import com.kharagedition.tibetandictionary.data.translation.Quota
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslateResult
import com.kharagedition.tibetandictionary.data.translation.TranslatedText
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.data.translation.TranslationRefusal
import com.kharagedition.tibetandictionary.fakes.FakeDictionaryRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationHistoryRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import com.kharagedition.tibetandictionary.ui.translate.TranslateRequest
import com.kharagedition.tibetandictionary.ui.translate.UsagePillUi
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartTranslateTest {

    private val translation = FakeTranslationRepository()
    private val history = FakeTranslationHistoryRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val dictionary = FakeDictionaryRepository()
    private val errors = RecordingErrorReporter()

    private val boEn = LangPair(Lang.BO, Lang.EN)

    private fun TestScope.controller(): SmartTranslateController =
        SmartTranslateController(backgroundScope, translation, history, prefs, dictionary, errors).also { runCurrent() }

    private fun quota(plan: Plan = Plan.FREE, used: Int = 3, limit: Int = 10, maxChars: Int = 100) =
        QuotaState(Quota(plan, used, limit, RESETS_AT, maxChars), freeLimit = 10)

    private fun settled(
        query: String,
        direction: Direction = SampleData.boEn,
        hasExact: Boolean = false,
        noResults: Boolean = false,
    ) = Settled(query, direction, hasExact, noResults)

    private fun translated(text: String, cached: Boolean = false) =
        TranslateResult.Translated(TranslatedText(text, cached, Provider.GOOGLE))

    private fun TestScope.state(smart: SmartTranslateController): SmartTranslateUi? {
        runCurrent()
        return smart.state.value
    }

    @Test
    fun `a Tibetan phrase auto-translates after 1 s`() = runTest {
        translation.quota.value = quota(used = 3)
        translation.results += translated("How are you?")
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        // Shown as translating while the pause runs.
        assertThat(state(smart)).isInstanceOf(SmartTranslateUi.Running::class.java)
        advanceTimeBy(999)
        assertThat(translation.calls).isEmpty()
        advanceTimeBy(2)

        assertThat(translation.calls)
            .containsExactly(FakeTranslationRepository.Call(PHRASE, boEn, TranslateSurface.SEARCH))
        val result = state(smart) as SmartTranslateUi.Result
        assertThat(result.text.translation).isEqualTo("How are you?")
        assertThat(result.words.map { it.text }).containsExactly("How", "are", "you?").inOrder()
        assertThat(result.pill).isEqualTo(UsagePillUi(remaining = 7, limit = 10, low = false))
        assertThat(history.records.value.single().source).isEqualTo(PHRASE)
    }

    @Test
    fun `typing on cancels the pending auto`() = runTest {
        translation.quota.value = quota()
        translation.results += translated("You are well.")
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(500)
        smart.onSettled(settled(OTHER_PHRASE))
        advanceTimeBy(1_001)

        assertThat(translation.calls.map { it.text }).containsExactly(OTHER_PHRASE)
    }

    @Test
    fun `the same phrase is auto-translated once`() = runTest {
        translation.quota.value = quota()
        translation.results += translated("How are you?")
        translation.results += translated("You are well.")
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(1_001)
        smart.onSettled(settled(OTHER_PHRASE))
        advanceTimeBy(1_001)
        smart.onSettled(settled(PHRASE))
        advanceTimeBy(1_001)

        assertThat(translation.calls.map { it.text }).containsExactly(PHRASE, OTHER_PHRASE).inOrder()
        // Its translation is still at hand.
        assertThat((state(smart) as SmartTranslateUi.Result).text.translation).isEqualTo("How are you?")
    }

    @Test
    fun `3 or fewer left gives a tap offer, no call`() = runTest {
        translation.quota.value = quota(used = 7)
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart)).isEqualTo(
            SmartTranslateUi.Offer(PHRASE, Lang.EN, instead = false, UsagePillUi(3, 10, low = true), phrase = true),
        )
    }

    @Test
    fun `guests never auto-translate`() = runTest {
        translation.quota.value = quota(plan = Plan.GUEST, used = 0, limit = 3)
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart)).isInstanceOf(SmartTranslateUi.Offer::class.java)
    }

    @Test
    fun `unknown quota gives an offer`() = runTest {
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart)).isEqualTo(
            SmartTranslateUi.Offer(PHRASE, Lang.EN, instead = false, pill = null, phrase = true),
        )
    }

    @Test
    fun `setting off gives an offer`() = runTest {
        translation.quota.value = quota()
        prefs.prefs.value = UserPrefs(smartTranslate = false)
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart)).isInstanceOf(SmartTranslateUi.Offer::class.java)
    }

    @Test
    fun `exact match gives an offer, even for a phrase`() = runTest {
        translation.quota.value = quota()
        val smart = controller()

        smart.onSettled(settled(PHRASE, hasExact = true))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart)).isInstanceOf(SmartTranslateUi.Offer::class.java)
    }

    @Test
    fun `a single word gets the word offer`() = runTest {
        translation.quota.value = quota()
        val smart = controller()

        smart.onSettled(settled(WORD, hasExact = true))

        assertThat(state(smart)).isEqualTo(
            SmartTranslateUi.Offer(WORD, Lang.EN, instead = false, UsagePillUi(7, 10, low = false), phrase = false),
        )
    }

    @Test
    fun `no results gives the instead offer`() = runTest {
        translation.quota.value = quota()
        val smart = controller()

        smart.onSettled(settled(WORD, noResults = true))

        assertThat((state(smart) as SmartTranslateUi.Offer).instead).isTrue()
    }

    @Test
    fun `over maxChars is TooLong`() = runTest {
        translation.quota.value = quota(maxChars = 20)
        val smart = controller()

        smart.onSettled(settled(PHRASE + PHRASE))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart))
            .isEqualTo(SmartTranslateUi.TooLong(length = 64, maxChars = 20, quota(maxChars = 20).pill()))
    }

    @Test
    fun `limit reached is Blocked with the plan`() = runTest {
        translation.quota.value = quota(used = 10)
        val smart = controller()

        smart.onSettled(settled(PHRASE))
        advanceTimeBy(2_000)

        assertThat(translation.calls).isEmpty()
        assertThat(state(smart)).isEqualTo(SmartTranslateUi.Blocked(Plan.FREE, UsagePillUi(0, 10, low = true)))

        translation.quota.value = quota(plan = Plan.GUEST, used = 3, limit = 3)

        assertThat((state(smart) as SmartTranslateUi.Blocked).plan).isEqualTo(Plan.GUEST)
    }

    @Test
    fun `Wylie, monolingual and disabled show nothing`() = runTest {
        translation.quota.value = quota()
        val smart = controller()

        smart.onSettled(settled("bkra shis bde legs"))
        assertThat(state(smart)).isNull()

        smart.onSettled(settled(PHRASE, direction = SampleData.boBo))
        assertThat(state(smart)).isNull()

        // Latin text in a Tibetan-source direction, and a single letter.
        smart.onSettled(settled("hello there friend"))
        assertThat(state(smart)).isNull()
        smart.onSettled(settled("ཀ"))
        assertThat(state(smart)).isNull()

        smart.onSettled(settled(PHRASE))
        translation.availability.value = TranslationAvailability(enabled = true, pairs = setOf(boEn.swapped()))
        assertThat(state(smart)).isNull()
        translation.availability.value = TranslationAvailability(enabled = false, pairs = setOf(boEn))
        assertThat(state(smart)).isNull()
        advanceTimeBy(2_000)
        assertThat(translation.calls).isEmpty()

        translation.availability.value = TranslationAvailability(enabled = true, pairs = setOf(boEn))
        assertThat(state(smart)).isNotNull()
    }

    @Test
    fun `translateNow on an offer translates once`() = runTest {
        translation.quota.value = quota(used = 7)
        val gate = CompletableDeferred<Unit>()
        translation.translateGate = gate
        translation.results += translated("Good luck", cached = true)
        val smart = controller()
        smart.onSettled(settled(WORD))

        smart.translateNow()
        assertThat(state(smart)).isInstanceOf(SmartTranslateUi.Running::class.java)
        smart.translateNow()
        gate.complete(Unit)
        runCurrent()
        smart.translateNow()

        assertThat(translation.calls).hasSize(1)
        assertThat((state(smart) as SmartTranslateUi.Result).text.cached).isTrue()
    }

    @Test
    fun `a refused translation shows why`() = runTest {
        translation.quota.value = quota(used = 7)
        translation.results += TranslateResult.Refused(TranslationRefusal.Offline)
        translation.results += TranslateResult.Refused(
            TranslationRefusal.DailyLimit(Plan.GUEST, limit = 3, resetsAt = RESETS_AT, freeLimit = 10),
        )
        val smart = controller()
        smart.onSettled(settled(WORD))

        smart.translateNow()
        assertThat(state(smart)).isEqualTo(SmartTranslateUi.Failed(offline = true, UsagePillUi(3, 10, low = true)))

        smart.translateNow()
        assertThat((state(smart) as SmartTranslateUi.Blocked).plan).isEqualTo(Plan.GUEST)
        assertThat(translation.calls).hasSize(2)
    }

    @Test
    fun `requestFor carries the pair, text and result`() = runTest {
        translation.quota.value = quota(used = 7)
        translation.results += translated("Good luck")
        val smart = controller()
        assertThat(smart.requestFor()).isNull()

        smart.onSettled(settled(WORD))
        runCurrent()
        assertThat(smart.requestFor()).isEqualTo(TranslateRequest(boEn, WORD))

        smart.translateNow()
        runCurrent()
        assertThat(smart.requestFor())
            .isEqualTo(TranslateRequest(boEn, WORD, TranslatedText("Good luck", cached = false, Provider.GOOGLE)))

        smart.onSettled(null)
        assertThat(state(smart)).isNull()
        assertThat(smart.requestFor()).isNull()
    }

    @Test
    fun `a phrase lists its words found in the dictionary`() = runTest {
        val smart = controller()

        smart.onSettled(settled("སྙིང་རྗེ་བཀྲ་ཤིས་ཡིན།"))
        runCurrent()

        assertThat(smart.phraseWords.value.map { it.id })
            .containsExactly(SampleData.nyingje.id, SampleData.tashi.id)
            .inOrder()

        smart.onSettled(settled(WORD))
        runCurrent()
        assertThat(smart.phraseWords.value).isEmpty()
    }

    private fun QuotaState.pill() = UsagePillUi(remaining, quota.limit, low = remaining <= 3)

    private companion object {
        val RESETS_AT: Instant = Instant.parse("2026-10-22T18:15:00Z")
        const val PHRASE = "ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།"
        const val OTHER_PHRASE = "ཁྱེད་རང་བདེ་པོ་ཡིན།"
        const val WORD = "བཀྲ་ཤིས"
    }
}
