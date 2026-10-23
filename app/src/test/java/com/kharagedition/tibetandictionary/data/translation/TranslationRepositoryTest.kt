package com.kharagedition.tibetandictionary.data.translation

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.analytics.AnalyticsEvent
import com.kharagedition.tibetandictionary.core.analytics.TranslateSurface
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.fakes.FakeAuthRepository
import com.kharagedition.tibetandictionary.fakes.FakeClock
import com.kharagedition.tibetandictionary.fakes.FakeTranslationApi
import com.kharagedition.tibetandictionary.fakes.RecordingAnalytics
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val BO_EN = LangPair(Lang.BO, Lang.EN)
private val RESETS_AT: Instant = Instant.parse("2026-10-19T18:00:00Z")
private val GUEST = Account("guest-1", isGuest = true, name = null, email = null)
private val GOOGLE = Account("u1", isGuest = false, name = "Tashi", email = "t@x.com")

private fun quota(plan: Plan = Plan.GUEST, used: Int = 0, limit: Int = 3, maxChars: Int = 100, resetsAt: Instant = RESETS_AT) =
    Quota(plan, used, limit, resetsAt, maxChars)

private fun status(quota: Quota = quota(), enabled: Boolean = true, freeLimit: Int = 10) =
    ApiResult.Ok(StatusReply(enabled, quota, freeLimit, setOf(BO_EN, BO_EN.swapped())))

private fun translated(quota: Quota? = quota(used = 1), cached: Boolean = false) =
    ApiResult.Ok(TranslateReply(TranslatedText("Tashi Delek", cached, Provider.AZURE), quota))

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationRepositoryTest {
    private val api = FakeTranslationApi()
    private val analytics = RecordingAnalytics()
    private val clock = FakeClock(zone = ZoneId.of("Asia/Thimphu"))
    private val errors = RecordingErrorReporter()

    private fun TestScope.repository(auth: FakeAuthRepository) =
        TranslationRepositoryImpl(api, auth, analytics, clock, errors, backgroundScope)

    @Test
    fun `translate creates the guest and stores the reply's quota`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository()
        val repository = repository(auth)
        api.translateResults += translated(quota(used = 1))
        val result = repository.translate("  ཀ  ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(result).isEqualTo(TranslateResult.Translated(TranslatedText("Tashi Delek", false, Provider.AZURE)))
        assertThat(auth.ensureCalls).isEqualTo(1)
        assertThat(repository.quota.value!!.quota.used).isEqualTo(1)
        assertThat(api.translateCalls.single()).isEqualTo(FakeTranslationApi.TranslateCall("ཀ", BO_EN, "Asia/Thimphu"))
    }

    @Test
    fun `a cached reply without quota keeps the last quota`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.translateResults += translated(quota(used = 1))
        api.translateResults += translated(quota = null, cached = true)
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(repository.quota.value!!.quota.used).isEqualTo(1)
    }

    @Test
    fun `daily limit as a guest sets remaining 0 and moreWhenSignedIn 7`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.statusResults += status(quota(used = 3))
        repository.refresh(force = true)
        val refusal = TranslationRefusal.DailyLimit(Plan.GUEST, 3, RESETS_AT, freeLimit = 10)
        api.translateResults += ApiResult.Refused(refusal)
        assertThat(repository.translate("ཀ", BO_EN, TranslateSurface.SEARCH)).isEqualTo(TranslateResult.Refused(refusal))
        val state = repository.quota.value!!
        assertThat(state.remaining).isEqualTo(0)
        assertThat(state.moreWhenSignedIn).isEqualTo(7)
        assertThat(analytics.events).contains(AnalyticsEvent.QuotaLimitHit("guest", TranslateSurface.SEARCH))
    }

    @Test
    fun `daily limit with no quota yet still fills one in`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.translateResults += ApiResult.Refused(TranslationRefusal.DailyLimit(Plan.FREE, 10, RESETS_AT, freeLimit = 10))
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(repository.quota.value).isEqualTo(QuotaState(quota(Plan.FREE, used = 10, limit = 10), freeLimit = 10))
        assertThat(repository.quota.value!!.moreWhenSignedIn).isNull()
    }

    @Test
    fun `offline on first use is Offline and nothing is recorded as an error`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository().apply { ensureFailure = IOException("offline") }
        val repository = repository(auth)
        val result = repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(result).isEqualTo(TranslateResult.Refused(TranslationRefusal.Offline))
        assertThat(errors.recorded).isEmpty()
        assertThat(api.translateCalls).isEmpty()
    }

    @Test
    fun `a failure creating the guest is Unexpected and recorded`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository().apply { ensureFailure = IllegalStateException("auth broken") }
        val result = repository(auth).translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(result).isEqualTo(TranslateResult.Refused(TranslationRefusal.Unexpected))
        assertThat(errors.recorded).hasSize(1)
    }

    @Test
    fun `unauthenticated keeps the user signed in`() = runTest(UnconfinedTestDispatcher()) {
        // App Check or the token was rejected: signing out would make a Google user a guest and fix nothing.
        val auth = FakeAuthRepository(GOOGLE)
        api.translateResults += ApiResult.Refused(TranslationRefusal.Unauthenticated)
        val result = repository(auth).translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(result).isEqualTo(TranslateResult.Refused(TranslationRefusal.Unauthenticated))
        assertThat(auth.signOutCalls).isEqualTo(0)
        assertThat(auth.account.value).isEqualTo(GOOGLE)
    }

    @Test
    fun `a deleted account signs out and the next call is a new guest`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(GOOGLE)
        val repository = repository(auth)
        api.translateResults += ApiResult.Refused(TranslationRefusal.AccountGone)
        api.translateResults += translated()
        api.statusResults += status()
        val first = repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(first).isEqualTo(TranslateResult.Refused(TranslationRefusal.AccountGone))
        assertThat(auth.signOutCalls).isEqualTo(1)
        val second = repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(second).isInstanceOf(TranslateResult.Translated::class.java)
        assertThat(auth.account.value?.isGuest).isTrue()
    }

    @Test
    fun `prepare creates the guest and reads status`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository()
        val repository = repository(auth)
        api.statusResults += status(quota(used = 1))
        repository.prepare()
        assertThat(auth.ensureCalls).isEqualTo(1)
        assertThat(api.statusCalls).hasSize(1)
        assertThat(repository.quota.value).isEqualTo(QuotaState(quota(used = 1), freeLimit = 10))
        assertThat(repository.availability.value).isEqualTo(TranslationAvailability(true, setOf(BO_EN, BO_EN.swapped())))
    }

    @Test
    fun `prepare offline is quiet`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository().apply { ensureFailure = IOException("offline") })
        repository.prepare()
        assertThat(api.statusCalls).isEmpty()
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `refresh does nothing without a user`() = runTest(UnconfinedTestDispatcher()) {
        repository(FakeAuthRepository()).refresh(force = true)
        assertThat(api.statusCalls).isEmpty()
    }

    @Test
    fun `refresh within 5 minutes is skipped, after 5 minutes it reads again`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        repeat(2) { api.statusResults += status() }
        repository.refresh()
        clock.instant += Duration.ofMinutes(4)
        repository.refresh()
        assertThat(api.statusCalls).hasSize(1)
        clock.instant += Duration.ofMinutes(2)
        repository.refresh()
        assertThat(api.statusCalls).hasSize(2)
    }

    @Test
    fun `a forced refresh always reads`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        repeat(2) { api.statusResults += status() }
        repository.refresh()
        repository.refresh(force = true)
        assertThat(api.statusCalls).hasSize(2)
    }

    @Test
    fun `a status failure keeps the old values`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.statusResults += status(quota(used = 2))
        api.statusResults += ApiResult.Refused(TranslationRefusal.Offline)
        repository.refresh()
        repository.refresh(force = true)
        assertThat(repository.quota.value!!.quota.used).isEqualTo(2)
    }

    @Test
    fun `past resetsAt the quota shows used 0 before the new status arrives`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.statusResults += status(quota(used = 2))
        repository.refresh()
        clock.instant = RESETS_AT.plusSeconds(1)
        val gate = CompletableDeferred<Unit>().also { api.statusGate = it }
        api.statusResults += status(quota(used = 0, resetsAt = RESETS_AT.plus(Duration.ofDays(1))))
        val refreshing = launch { repository.refresh() }
        assertThat(repository.quota.value!!.quota.used).isEqualTo(0)
        assertThat(repository.quota.value!!.quota.resetsAt).isEqualTo(RESETS_AT)
        gate.complete(Unit)
        refreshing.join()
        assertThat(repository.quota.value!!.quota.resetsAt).isEqualTo(RESETS_AT.plus(Duration.ofDays(1)))
    }

    @Test
    fun `disabled turns availability off`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.statusResults += status()
        repository.refresh()
        api.translateResults += ApiResult.Refused(TranslationRefusal.Disabled)
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(repository.availability.value!!.enabled).isFalse()
    }

    @Test
    fun `too long updates maxChars`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.statusResults += status(quota(maxChars = 100))
        repository.refresh()
        api.translateResults += ApiResult.Refused(TranslationRefusal.TooLong(maxChars = 80, length = 90))
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(repository.quota.value!!.quota.maxChars).isEqualTo(80)
    }

    @Test
    fun `too long before any quota re-reads status for the limit`() = runTest(UnconfinedTestDispatcher()) {
        // Without a quota there is no maxChars to update, so the counter couldn't say why nothing happened.
        val repository = repository(FakeAuthRepository(GUEST))
        api.translateResults += ApiResult.Refused(TranslationRefusal.TooLong(maxChars = 80, length = 90))
        api.statusResults += status(quota(maxChars = 80))
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(repository.quota.value!!.quota.maxChars).isEqualTo(80)
    }

    @Test
    fun `at resetsAt the quota shows a new day without a call`() = runTest(UnconfinedTestDispatcher()) {
        // Someone who stays on a screen past midnight must not stay at "Limit reached".
        val repository = repository(FakeAuthRepository(GUEST))
        api.statusResults += status(quota(used = 3, limit = 3))
        repository.refresh()
        val wait = Duration.between(clock.instant, RESETS_AT)
        clock.instant = RESETS_AT
        advanceTimeBy(wait.toMillis() + 1)
        assertThat(repository.quota.value!!.remaining).isEqualTo(3)
        assertThat(api.statusCalls).hasSize(1)
    }

    @Test
    fun `a status for a deleted account signs out`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(GOOGLE)
        api.statusResults += ApiResult.Refused(TranslationRefusal.AccountGone)
        repository(auth).refresh()
        assertThat(auth.signOutCalls).isEqualTo(1)
    }

    @Test
    fun `an unsupported pair re-reads status`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        repeat(2) { api.statusResults += status() }
        repository.refresh()
        api.translateResults += ApiResult.Refused(TranslationRefusal.UnsupportedPair)
        repository.translate("ཀ", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(api.statusCalls).hasSize(2)
    }

    @Test
    fun `a uid change clears the quota and re-reads status`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(Account("u1", isGuest = false, name = "Tashi", email = "t@x.com"))
        val repository = repository(auth)
        api.statusResults += status(quota(Plan.FREE, used = 2, limit = 10))
        repository.refresh()
        val gate = CompletableDeferred<Unit>().also { api.statusGate = it }
        api.statusResults += status(quota(Plan.FREE, used = 5, limit = 10))
        auth.account.value = Account("u2", isGuest = false, name = "Tashi", email = "t@x.com")
        assertThat(repository.quota.value).isNull()
        assertThat(repository.availability.value).isNull()
        gate.complete(Unit)
        assertThat(repository.quota.value!!.quota.used).isEqualTo(5)
    }

    @Test
    fun `signing out clears the quota without a status call`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(GUEST)
        val repository = repository(auth)
        api.statusResults += status()
        repository.refresh()
        auth.account.value = null
        assertThat(repository.quota.value).isNull()
        assertThat(api.statusCalls).hasSize(1)
    }

    @Test
    fun `translate events carry outcome, pair, plan and surface`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.translateResults += translated(quota(used = 1), cached = true)
        api.translateResults += ApiResult.Refused(TranslationRefusal.NotTibetanScript)
        repository.translate("ཀ", BO_EN, TranslateSurface.SEARCH)
        repository.translate("bkra", BO_EN, TranslateSurface.TRANSLATE_TAB)
        assertThat(analytics.events).containsExactly(
            AnalyticsEvent.Translate("cached", cached = true, pair = "bo-en", plan = "guest", surface = TranslateSurface.SEARCH),
            AnalyticsEvent.Translate(
                "refused:not_tibetan_script",
                cached = false,
                pair = "bo-en",
                plan = "guest",
                surface = TranslateSurface.TRANSLATE_TAB,
            ),
        ).inOrder()
    }

    @Test
    fun `no analytics param contains the text`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(FakeAuthRepository(GUEST))
        api.translateResults += translated()
        api.translateResults += ApiResult.Refused(TranslationRefusal.DailyLimit(Plan.GUEST, 3, RESETS_AT, 10))
        repository.translate("ཀཁག", BO_EN, TranslateSurface.TRANSLATE_TAB)
        repository.translate("ཀཁག", BO_EN, TranslateSurface.SEARCH)
        val values = analytics.events.flatMap { it.params.values }
        assertThat(values).isNotEmpty()
        assertThat(values.none { "ཀཁག" in it || "Tashi" in it }).isTrue()
    }
}
