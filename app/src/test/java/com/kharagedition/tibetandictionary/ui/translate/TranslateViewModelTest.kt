package com.kharagedition.tibetandictionary.ui.translate

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.MainDispatcherRule
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.repository.TranslationRecord
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
import com.kharagedition.tibetandictionary.domain.OutputWord
import com.kharagedition.tibetandictionary.domain.TranslationText
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeSavedTranslationsRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationHistoryRepository
import com.kharagedition.tibetandictionary.fakes.FakeTranslationRepository
import com.kharagedition.tibetandictionary.fakes.FakeUserPreferencesRepository
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.SignInAction
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val BO_EN = LangPair(Lang.BO, Lang.EN)
private val EN_BO = LangPair(Lang.EN, Lang.BO)
private val RESETS_AT: Instant = Instant.parse("2026-10-19T18:00:00Z")
private val TASHI = TranslatedText("Tashi Delek", cached = false, provider = Provider.AZURE)

private fun quotaState(plan: Plan = Plan.FREE, used: Int = 3, limit: Int = 10, maxChars: Int = 100, freeLimit: Int = 10) =
    QuotaState(Quota(plan, used, limit, RESETS_AT, maxChars), freeLimit)

class TranslateViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val translation = FakeTranslationRepository()
    private val history = FakeTranslationHistoryRepository()
    private val saved = FakeSavedTranslationsRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val auth = FakeAuthRepository(Account("guest-1", isGuest = true, name = null, email = null))
    private val analytics = RecordingAnalytics()
    private val errors = RecordingErrorReporter()
    private val savedState = SavedStateHandle()

    /** A view model with its state collected, and every effect it sends. */
    private class Subject(val vm: TranslateViewModel, val effects: List<TranslateEffect>) {
        val state: TranslateUiState get() = vm.uiState.value
        fun on(event: TranslateEvent) = vm.onEvent(event)
        fun type(text: String) = on(TranslateEvent.InputChanged(TextFieldValue(text, TextRange(text.length))))
    }

    private fun TestScope.subject(state: SavedStateHandle = savedState): Subject {
        val vm = TranslateViewModel(
            state,
            translation,
            history,
            saved,
            prefs,
            SignInAction(auth, translation, analytics),
            errors,
        )
        val effects = mutableListOf<TranslateEffect>()
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        backgroundScope.launch(main.dispatcher) { vm.effects.toList(effects) }
        return Subject(vm, effects)
    }

    @Test
    fun `Shown prepares translation and the pill shows remaining`() = runTest {
        translation.quota.value = quotaState(used = 3)
        val subject = subject()
        subject.on(TranslateEvent.Shown)
        assertThat(translation.prepareCalls).isEqualTo(1)
        assertThat(subject.state.pill).isEqualTo(UsagePillUi(remaining = 7, limit = 10, low = false))
        assertThat(subject.state.plan).isEqualTo(Plan.FREE)
        assertThat(subject.state.freeLimit).isEqualTo(10)
    }

    @Test
    fun `the Pro pill is unlimited and low only once the fair-use limit is reached`() = runTest {
        translation.quota.value = quotaState(plan = Plan.PRO, used = 298, limit = 300, maxChars = 500)
        val subject = subject()
        assertThat(subject.state.pill).isEqualTo(UsagePillUi(remaining = 2, limit = 300, low = false, unlimited = true))

        translation.quota.value = quotaState(plan = Plan.PRO, used = 300, limit = 300, maxChars = 500)
        assertThat(subject.state.pill).isEqualTo(UsagePillUi(remaining = 0, limit = 300, low = true, unlimited = true))
    }

    @Test
    fun `Pro at the fair-use limit shows FairUseLimit, not the upgrade card`() = runTest {
        translation.quota.value = quotaState(plan = Plan.PRO, used = 300, limit = 300, maxChars = 500)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        assertThat(subject.state.block).isEqualTo(TranslateBlock.FairUseLimit)
        assertThat(subject.state.canTranslate).isFalse()
    }

    @Test
    fun `Pro never gets the longer-text hint`() = runTest {
        translation.quota.value = quotaState(plan = Plan.PRO, maxChars = 500)
        val subject = subject()
        subject.type("ཀ".repeat(480))
        assertThat(subject.state.counter).isNotEqualTo(CounterLevel.NORMAL)
        assertThat(subject.state.showProHint).isFalse()
    }

    @Test
    fun `the pill is low from 3 left`() = runTest {
        translation.quota.value = quotaState(plan = Plan.GUEST, used = 0, limit = 3)
        assertThat(subject().state.pill).isEqualTo(UsagePillUi(remaining = 3, limit = 3, low = true))
    }

    @Test
    fun `the pair defaults to the pack's first open pair`() = runTest {
        val subject = subject()
        assertThat(subject.state.pair).isEqualTo(BO_EN)
        assertThat(subject.state.canSwap).isTrue()
        translation.availability.value = TranslationAvailability(enabled = true, pairs = setOf(EN_BO))
        assertThat(subject.state.pair).isEqualTo(EN_BO)
        assertThat(subject.state.canSwap).isFalse()
    }

    @Test
    fun `translate success shows the result, records a recent, updates the pill`() = runTest {
        translation.quota.value = quotaState(used = 3)
        translation.quotaAfterTranslate = quotaState(used = 4)
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("  བཀྲ་ཤིས་བདེ་ལེགས།  ")
        assertThat(subject.state.canTranslate).isTrue()

        subject.on(TranslateEvent.Translate)

        assertThat(translation.calls.single())
            .isEqualTo(FakeTranslationRepository.Call("  བཀྲ་ཤིས་བདེ་ལེགས།  ", BO_EN, TranslateSurface.TRANSLATE_TAB))
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.DONE)
        assertThat(subject.state.result).isEqualTo(
            TranslationResultUi(
                text = "Tashi Delek",
                words = TranslationText.words("Tashi Delek", Lang.EN),
                cached = false,
                provider = Provider.AZURE,
                saved = false,
            ),
        )
        assertThat(history.records.value.single().source).isEqualTo("བཀྲ་ཤིས་བདེ་ལེགས།")
        assertThat(subject.state.pill).isEqualTo(UsagePillUi(remaining = 6, limit = 10, low = false))
    }

    @Test
    fun `a cached result is flagged`() = runTest {
        translation.results += TranslateResult.Translated(TASHI.copy(cached = true))
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.result!!.cached).isTrue()
    }

    @Test
    fun `a result for text edited meanwhile is not shown but is kept in Recent`() = runTest {
        val gate = CompletableDeferred<Unit>().also { translation.translateGate = it }
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        subject.type("ང་བོད་པ་ཡིན།")
        gate.complete(Unit)
        assertThat(subject.state.result).isNull()
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.IDLE)
        assertThat(subject.state.canTranslate).isTrue()
        assertThat(history.records.value.single().source).isEqualTo("བཀྲ་ཤིས།")
    }

    @Test
    fun `a server script refusal shows Unexpected`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.NotTibetanScript)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.block).isEqualTo(TranslateBlock.Unexpected)
    }

    @Test
    fun `a second Translate while loading makes one call`() = runTest {
        val gate = CompletableDeferred<Unit>().also { translation.translateGate = it }
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.LOADING)
        assertThat(subject.state.canTranslate).isFalse()
        subject.on(TranslateEvent.Translate)
        gate.complete(Unit)
        assertThat(translation.calls).hasSize(1)
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.DONE)
    }

    @Test
    fun `guest at the limit shows GuestLimit with 7 more`() = runTest {
        translation.quota.value = quotaState(plan = Plan.GUEST, used = 3, limit = 3, freeLimit = 10)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        assertThat(subject.state.block).isEqualTo(TranslateBlock.GuestLimit(used = 3, more = 7))
        assertThat(subject.state.canTranslate).isFalse()
    }

    @Test
    fun `free at the limit shows DailyLimit and the input stays editable`() = runTest {
        translation.quota.value = quotaState(used = 10, limit = 10)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        assertThat(subject.state.block).isEqualTo(TranslateBlock.DailyLimit)
        assertThat(subject.state.inputEnabled).isTrue()
        subject.type("བཀྲ་ཤིས་བདེ་ལེགས།")
        assertThat(subject.state.input.text).isEqualTo("བཀྲ་ཤིས་བདེ་ལེགས།")
        assertThat(subject.state.block).isEqualTo(TranslateBlock.DailyLimit)
    }

    @Test
    fun `offline shows Offline and Retry translates again`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.Offline)
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.block).isEqualTo(TranslateBlock.Offline)
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.IDLE)

        subject.on(TranslateEvent.Retry)

        assertThat(translation.calls).hasSize(2)
        assertThat(subject.state.block).isNull()
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.DONE)
    }

    @Test
    fun `editing after a failure clears the block`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.Busy)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        subject.type("བཀྲ་ཤིས་")
        assertThat(subject.state.block).isNull()
    }

    @Test
    fun `busy shows Busy`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.Busy)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.block).isEqualTo(TranslateBlock.Busy)
    }

    @Test
    fun `an unexpected refusal shows Unexpected`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.Unauthenticated)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.block).isEqualTo(TranslateBlock.Unexpected)
    }

    @Test
    fun `a deleted account shows Unexpected`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.AccountGone)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.block).isEqualTo(TranslateBlock.Unexpected)
    }

    @Test
    fun `disabled disables input`() = runTest {
        translation.availability.value = TranslationAvailability(enabled = false, pairs = setOf(BO_EN, EN_BO))
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        assertThat(subject.state.inputEnabled).isFalse()
        assertThat(subject.state.block).isEqualTo(TranslateBlock.Disabled)
        assertThat(subject.state.canTranslate).isFalse()
    }

    @Test
    fun `rate limited toasts without a block`() = runTest {
        translation.results += TranslateResult.Refused(TranslationRefusal.RateLimited(retryAfterSec = 10))
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        assertThat(subject.state.block).isNull()
        assertThat(subject.effects).containsExactly(TranslateEffect.Toast(UiText(R.string.translate_rate_limited)))
    }

    @Test
    fun `Wylie input shows NOT_TIBETAN_SCRIPT and makes no call`() = runTest {
        val subject = subject()
        subject.type("bkra shis")
        assertThat(subject.state.hint).isEqualTo(InlineHint.NOT_TIBETAN_SCRIPT)
        assertThat(subject.state.canTranslate).isFalse()
        subject.on(TranslateEvent.Translate)
        assertThat(translation.calls).isEmpty()
    }

    @Test
    fun `Tibetan typed with English source offers SwitchDirection, which flips the pair and keeps the text`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.Swap)
        subject.type("བཀྲ་ཤིས།")
        assertThat(subject.state.pair).isEqualTo(EN_BO)
        assertThat(subject.state.hint).isEqualTo(InlineHint.LOOKS_TIBETAN)

        subject.on(TranslateEvent.SwitchDirection)

        assertThat(subject.state.pair).isEqualTo(BO_EN)
        assertThat(subject.state.input.text).isEqualTo("བཀྲ་ཤིས།")
        assertThat(subject.state.hint).isNull()
    }

    @Test
    fun `a 6000-character paste is OVER, cannot translate, and makes no call`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.Paste("ཀ".repeat(6000)))
        assertThat(subject.state.input.text).hasLength(6000)
        assertThat(subject.state.length).isEqualTo(6000)
        assertThat(subject.state.counter).isEqualTo(CounterLevel.OVER)
        assertThat(subject.state.canTranslate).isFalse()
        subject.on(TranslateEvent.Translate)
        assertThat(translation.calls).isEmpty()
    }

    @Test
    fun `paste inserts at the cursor`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.InputChanged(TextFieldValue("བཀྲ་ཤིས།", TextRange(4))))
        subject.on(TranslateEvent.Paste("ཀ"))
        assertThat(subject.state.input).isEqualTo(TextFieldValue("བཀྲ་ཀཤིས།", TextRange(5)))
    }

    @Test
    fun `counter is NEAR from 85 and OVER above 100`() = runTest {
        val subject = subject()
        subject.type("ཀ".repeat(84))
        assertThat(subject.state.counter).isEqualTo(CounterLevel.NORMAL)
        subject.type("ཀ".repeat(85))
        assertThat(subject.state.counter).isEqualTo(CounterLevel.NEAR)
        subject.type("ཀ".repeat(100))
        assertThat(subject.state.counter).isEqualTo(CounterLevel.NEAR)
        assertThat(subject.state.canTranslate).isTrue()
        subject.type("ཀ".repeat(101))
        assertThat(subject.state.counter).isEqualTo(CounterLevel.OVER)
        assertThat(subject.state.canTranslate).isFalse()
    }

    @Test
    fun `swap with a result moves the result into the input`() = runTest {
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)

        subject.on(TranslateEvent.Swap)

        assertThat(subject.state.pair).isEqualTo(EN_BO)
        assertThat(subject.state.input.text).isEqualTo("Tashi Delek")
        assertThat(subject.state.result).isNull()
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.IDLE)
    }

    @Test
    fun `recent tap restores without a call`() = runTest {
        history.records.value = listOf(
            TranslationRecord(1, EN_BO, "Thank you", "ཐུགས་རྗེ་ཆེ།", Provider.GOOGLE, RESETS_AT),
        )
        val subject = subject()
        assertThat(subject.state.recents).containsExactly(RecentTranslationUi(1, "Thank you", "ཐུགས་རྗེ་ཆེ།", EN_BO))

        subject.on(TranslateEvent.RecentTap(1))

        assertThat(translation.calls).isEmpty()
        assertThat(subject.state.pair).isEqualTo(EN_BO)
        assertThat(subject.state.input.text).isEqualTo("Thank you")
        assertThat(subject.state.result!!.text).isEqualTo("ཐུགས་རྗེ་ཆེ།")
        // No call was made, so there is no "From cache" note either.
        assertThat(subject.state.result!!.cached).isFalse()
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.DONE)
    }

    @Test
    fun `clear recents clears the history`() = runTest {
        history.records.value = listOf(TranslationRecord(1, EN_BO, "Thank you", "ཐུགས་རྗེ་ཆེ།", Provider.GOOGLE, RESETS_AT))
        val subject = subject()
        subject.on(TranslateEvent.ClearRecents)
        assertThat(subject.state.recents).isEmpty()
    }

    @Test
    fun `save and unsave toast and update the heart`() = runTest {
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)

        subject.on(TranslateEvent.ToggleSave)
        assertThat(saved.records.value.map { it.source }).containsExactly("བཀྲ་ཤིས།")
        assertThat(subject.state.result!!.saved).isTrue()

        subject.on(TranslateEvent.ToggleSave)
        assertThat(saved.records.value).isEmpty()
        assertThat(subject.state.result!!.saved).isFalse()
        assertThat(subject.effects).containsExactly(
            TranslateEffect.Toast(UiText(R.string.translation_saved)),
            TranslateEffect.Toast(UiText(R.string.translation_removed)),
        ).inOrder()
    }

    @Test
    fun `copy and share send the texts`() = runTest {
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        subject.on(TranslateEvent.Copy)
        subject.on(TranslateEvent.Share)
        // The route confirms the copy (below Android 13, as Entry does), so no toast comes from here.
        assertThat(subject.effects).containsExactly(
            TranslateEffect.Copy("Tashi Delek"),
            TranslateEffect.Share("བཀྲ་ཤིས།\n\nTashi Delek"),
        ).inOrder()
    }

    @Test
    fun `word tap opens search with the query`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.WordTap(OutputWord("compassion.", "compassion")))
        assertThat(subject.effects).containsExactly(TranslateEffect.OpenSearch("compassion"))
    }

    @Test
    fun `a word without a query does nothing`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.WordTap(OutputWord("on", null)))
        assertThat(subject.effects).isEmpty()
    }

    @Test
    fun `look up words searches the input`() = runTest {
        val subject = subject()
        subject.type(" བཀྲ་ཤིས། ")
        subject.on(TranslateEvent.LookUpWords)
        assertThat(subject.effects).containsExactly(TranslateEffect.OpenSearch("བཀྲ་ཤིས།"))
    }

    @Test
    fun `OpenPro opens the paywall with its source and leaves logging to it`() = runTest {
        val subject = subject()
        analytics.events.clear()
        subject.on(TranslateEvent.OpenPro(PromptSource.TOO_LONG))
        assertThat(analytics.events).isEmpty()
        assertThat(subject.state.sheet).isEqualTo(TranslateSheet.PRO)
        assertThat(subject.state.proSource).isEqualTo(PromptSource.TOO_LONG)
        subject.on(TranslateEvent.DismissSheet)
        assertThat(subject.state.sheet).isNull()
        assertThat(subject.state.proSource).isNull()
        subject.on(TranslateEvent.OpenToday)
        assertThat(subject.state.sheet).isEqualTo(TranslateSheet.TODAY)
    }

    @Test
    fun `showProHint only for free plan from 85 characters`() = runTest {
        translation.quota.value = quotaState(plan = Plan.FREE, used = 0)
        val subject = subject()
        subject.type("ཀ".repeat(84))
        assertThat(subject.state.showProHint).isFalse()
        subject.type("ཀ".repeat(85))
        assertThat(subject.state.showProHint).isTrue()
        translation.quota.value = quotaState(plan = Plan.GUEST, used = 0, limit = 3)
        assertThat(subject.state.showProHint).isFalse()
    }

    @Test
    fun `a recreated view model restores text and direction`() = runTest {
        val first = subject()
        first.on(TranslateEvent.Swap)
        first.type("Thank you")

        val second = subject(savedState)

        assertThat(second.state.pair).isEqualTo(EN_BO)
        assertThat(second.state.input.text).isEqualTo("Thank you")
    }

    @Test
    fun `a huge paste is kept whole but saved for process death only up to a cap`() = runTest {
        // A saved state over about 1 MB crashes the app when it goes to the background.
        val subject = subject()
        subject.type("ཀ".repeat(300_000))
        assertThat(subject.state.length).isEqualTo(300_000)
        assertThat(savedState.get<String>("translate_text")).hasLength(20_000)

        // The cut never splits a surrogate pair.
        subject.type("a" + "\uD83D\uDE00".repeat(15_000))
        val stored = savedState.get<String>("translate_text")!!
        assertThat(stored).hasLength(19_999)
        assertThat(stored.last().isHighSurrogate()).isFalse()
    }

    @Test
    fun `a pack switch moves to dz-en and clears the result`() = runTest {
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)

        prefs.setDictionaryPack(DictionaryPack.DZONGKHA)

        assertThat(subject.state.pair).isEqualTo(LangPair(Lang.DZ, Lang.EN))
        assertThat(subject.state.result).isNull()
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.IDLE)
        assertThat(subject.state.samples).isEqualTo(DictionaryPack.DZONGKHA.translationSamplesFor(Lang.DZ))
    }

    @Test
    fun `samples follow the source language`() = runTest {
        val subject = subject()
        assertThat(subject.state.samples).isEqualTo(DictionaryPack.TIBETAN.translationSamplesFor(Lang.BO))
        subject.on(TranslateEvent.Swap)
        assertThat(subject.state.samples).isEqualTo(DictionaryPack.TIBETAN.translationSamplesFor(Lang.EN))
        subject.on(TranslateEvent.SampleTap("May all beings be happy"))
        assertThat(subject.state.input.text).isEqualTo("May all beings be happy")
        assertThat(translation.calls).isEmpty()
    }

    @Test
    fun `Load fills pair, text and result without a call`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.Load(TranslateRequest(EN_BO, "Thank you", TranslatedText("ཐུགས་རྗེ་ཆེ།", true, Provider.GOOGLE))))
        assertThat(translation.calls).isEmpty()
        assertThat(subject.state.pair).isEqualTo(EN_BO)
        assertThat(subject.state.input.text).isEqualTo("Thank you")
        assertThat(subject.state.result!!.text).isEqualTo("ཐུགས་རྗེ་ཆེ།")
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.DONE)
    }

    @Test
    fun `Load without a result only fills the input`() = runTest {
        val subject = subject()
        subject.on(TranslateEvent.Load(TranslateRequest(EN_BO, "Thank you")))
        assertThat(subject.state.result).isNull()
        assertThat(subject.state.phase).isEqualTo(TranslatePhase.IDLE)
        assertThat(subject.state.canTranslate).isTrue()
    }

    @Test
    fun `clear empties the input and the result`() = runTest {
        translation.results += TranslateResult.Translated(TASHI)
        val subject = subject()
        subject.type("བཀྲ་ཤིས།")
        subject.on(TranslateEvent.Translate)
        subject.on(TranslateEvent.Clear)
        assertThat(subject.state.input.text).isEmpty()
        assertThat(subject.state.result).isNull()
        assertThat(subject.state.canTranslate).isFalse()
    }
}
