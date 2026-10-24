package com.kharagedition.tibetandictionary.ui.profile

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.BuildConfig
import com.kharagedition.tibetandictionary.Features
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.analytics.PromptSource
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.locale.LocaleApplier
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.auth.DeleteAccount
import com.kharagedition.tibetandictionary.data.billing.BillingRepository
import com.kharagedition.tibetandictionary.data.billing.ProEntitlement
import com.kharagedition.tibetandictionary.data.billing.ProPlan
import com.kharagedition.tibetandictionary.data.billing.manageSubscriptionUrl
import com.kharagedition.tibetandictionary.data.install.DictionarySwitcher
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.prefs.UserPrefs
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.translation.Plan
import com.kharagedition.tibetandictionary.data.translation.QuotaState
import com.kharagedition.tibetandictionary.data.translation.TranslationAvailability
import com.kharagedition.tibetandictionary.data.translation.TranslationRepository
import com.kharagedition.tibetandictionary.ui.model.AccountUi
import com.kharagedition.tibetandictionary.ui.model.SignInAction
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.paywall.PaywallMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Profile tab: reading settings, theme and default direction from [prefs], the visible directions and entry count
 * from the dictionary's capabilities, and the footer links to [linkTargets]. Settings persist as they change. The
 * language shown is the one the app is in ([localeApplier]), falling back to the stored one while no per-app language
 * is set; choosing another persists it and switches the app to it. [uiState] is null until prefs and capabilities load.
 * A setting that cannot be stored (DataStore's IOException on a full or damaged disk) is recorded through [errors]
 * and shown as a generic error toast; the screen keeps showing the stored value.
 *
 * Accounts (1z): a signed-in Google account shows its card, which opens Sign out / Delete account; a guest or no
 * user sees the signed-out card, whose button signs in through [signInAction]. The plan card shows today's
 * translations from [translation] once there is a user; Upgrade opens the Pro sheet (a fake door, logged).
 * [deleteAccount] runs only after the confirm dialog; Profile never creates a user.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    dictionary: DictionaryRepository,
    private val switcher: DictionarySwitcher,
    private val localeApplier: LocaleApplier,
    private val linkTargets: ProfileLinkTargets,
    private val errors: ErrorReporter,
    private val auth: AuthRepository,
    private val translation: TranslationRepository,
    private val signInAction: SignInAction,
    private val deleteAccount: DeleteAccount,
    private val billing: BillingRepository,
    private val clock: AppClock,
) : ViewModel() {

    /** What Profile shows over itself: a sheet, the delete dialog (and whether it is deleting), the Pro sheet. */
    private data class Overlays(
        val sheet: ProfileSheet?,
        val confirmDelete: Boolean,
        val deleting: Boolean,
        val paywall: PaywallMode?,
    )

    private val appLanguage = MutableStateFlow(localeApplier.currentLanguage())
    private val sheet = MutableStateFlow<ProfileSheet?>(null)
    private val confirmDelete = MutableStateFlow(false)
    private val paywall = MutableStateFlow<PaywallMode?>(null)

    /** True while an account deletion runs: the dialog stays open with progress, and a second confirm does nothing. */
    private val deleting = MutableStateFlow(false)
    private val overlays = combine(sheet, confirmDelete, deleting, paywall, ::Overlays)
    /** Who is signed in, today's quota, and what the store and the paywall switch say about Pro. */
    private data class User(
        val account: Account?,
        val quota: QuotaState?,
        val entitlement: ProEntitlement?,
        val availability: TranslationAvailability?,
    )

    private val user = combine(auth.account, translation.quota, billing.entitlement, translation.availability, ::User)

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState?> =
        combine(
            prefs.prefs,
            dictionary.capabilities,
            appLanguage,
            overlays,
            user,
        ) { prefs, capabilities, appLanguage, overlays, user ->
            val entitlement = user.entitlement?.takeIf { it.active }
            state(prefs, capabilities, appLanguage ?: prefs.appLanguage, overlays).copy(
                account = user.account?.toUi(),
                plan = planCard(
                    user.account,
                    user.quota,
                    entitlement,
                    onSale = user.availability?.billingEnabled == true && billing.available,
                    zone = clock.zone(),
                ),
                proNoteOnDelete = entitlement?.willRenew == true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.Shown -> {
                appLanguage.value = localeApplier.currentLanguage()
                viewModelScope.launch { recorded { translation.refresh() } }
            }
            is ProfileEvent.OpenSheet -> sheet.value = event.sheet
            ProfileEvent.DismissSheet -> sheet.value = null
            is ProfileEvent.SetTextSize -> persist { setTextSize(event.size) }
            is ProfileEvent.SetLineSpacing -> persist { setLineSpacing(event.spacing) }
            is ProfileEvent.SetAppearance -> persist { setAppearance(event.appearance) }
            is ProfileEvent.SetDictionary -> setDictionary(event.pack)
            is ProfileEvent.SetDefaultDirection -> {
                sheet.value = null
                persist { setDefaultDirectionId(event.direction.id) }
            }
            is ProfileEvent.SetLanguage -> setLanguage(event.language)
            ProfileEvent.ToggleWotdNotification -> persist {
                val current = snapshot()
                setWotdNotification(!current.wotdNotificationEnabled, current.wotdNotificationMinute)
            }
            is ProfileEvent.OpenLink -> openLink(event.link)
            is ProfileEvent.OpenOtherApp -> {
                sheet.value = null
                send(ProfileEffect.OpenUrl(event.app.url))
            }
            // Sign-in needs the activity: the screen calls signIn instead.
            is ProfileEvent.SignIn -> Unit
            ProfileEvent.OpenAccount -> sheet.value = ProfileSheet.ACCOUNT
            ProfileEvent.SignOut -> signOut()
            ProfileEvent.AskDeleteAccount -> {
                sheet.value = null
                confirmDelete.value = true
            }
            ProfileEvent.ConfirmDeleteAccount -> confirmDeleteAccount()
            ProfileEvent.DismissDelete -> if (!deleting.value) confirmDelete.value = false
            ProfileEvent.ToggleSmartTranslate -> persist { setSmartTranslate(!snapshot().smartTranslate) }
            // The paywall logs its own view (or pro_interest for the fake door).
            ProfileEvent.OpenPro -> paywall.value = PaywallMode.PURCHASE
            ProfileEvent.DismissProSheet -> paywall.value = null
            ProfileEvent.SwitchToYearly -> paywall.value = PaywallMode.SWITCH
            ProfileEvent.ManageSubscription -> send(
                ProfileEffect.OpenUrl(manageSubscriptionUrl(billing.entitlement.value?.takeIf { it.active }?.plan)),
            )
        }
    }

    /** Google sign-in from the account card or the plan card ([source]); toasts the result unless cancelled. */
    fun signIn(activity: Activity, source: PromptSource) {
        viewModelScope.launch {
            guarded { signInAction.run(activity, source)?.let { effectChannel.send(ProfileEffect.Toast(it)) } }
        }
    }

    private fun signOut() {
        sheet.value = null
        viewModelScope.launch {
            guarded {
                auth.signOut()
                effectChannel.send(ProfileEffect.Toast(UiText(R.string.signed_out)))
            }
        }
    }

    /** Deletes the account the dialog asked about; the toast says whether it worked. */
    private fun confirmDeleteAccount() {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            val deleted = try {
                recorded { deleteAccount() } == true
            } finally {
                confirmDelete.value = false
                deleting.value = false
            }
            val message = if (deleted) R.string.account_deleted else R.string.account_delete_failed
            effectChannel.send(ProfileEffect.Toast(UiText(message)))
        }
    }

    private fun setDictionary(pack: DictionaryPack) {
        // Close the sheet first: the switch shows a full-screen overlay, and the sheet is a window above it.
        sheet.value = null
        // The switch runs on the application scope, so leaving this screen cannot cancel it.
        if (pack != uiState.value?.dictionary) switcher.switchTo(pack)
    }

    private fun setLanguage(language: AppLanguage) {
        // Close the sheet first: switching the locale recreates the activity, and this state outlives it.
        sheet.value = null
        viewModelScope.launch {
            val shown = appLanguage.value
            guarded(onFailure = { appLanguage.value = shown }) {
                // Choosing the language in use again would only recreate the activity.
                if (language == (shown ?: prefs.snapshot().appLanguage)) return@guarded
                appLanguage.value = language
                prefs.setAppLanguage(language)
                localeApplier.setLanguage(language)
            }
        }
    }

    private fun openLink(link: ProfileLink) {
        val shown = uiState.value ?: return
        if (link !in shown.links) return
        when (link) {
            ProfileLink.ABOUT -> send(ProfileEffect.NavigateAbout)
            ProfileLink.RATE -> send(ProfileEffect.OpenUrl(linkTargets.playStore))
            ProfileLink.SHARE -> send(ProfileEffect.ShareApp(linkTargets.playStore, shown.dictionary))
            ProfileLink.MORE_APPS -> sheet.value = ProfileSheet.MORE_APPS
            ProfileLink.SUPPORT -> send(ProfileEffect.OpenUrl(linkTargets.support))
            ProfileLink.PRIVACY -> send(ProfileEffect.OpenUrl(linkTargets.privacy))
        }
    }

    private fun state(
        prefs: UserPrefs,
        capabilities: DictionaryCapabilities,
        language: AppLanguage,
        overlays: Overlays,
    ): ProfileUiState {
        val visible = capabilities.directions.filter { it.entryCount > 0 }.sortedBy(Direction::position)
        return ProfileUiState(
            textSize = prefs.textSize,
            lineSpacing = prefs.lineSpacing,
            appearance = prefs.appearance,
            dictionary = capabilities.pack,
            defaultDirection = defaultDirection(visible, prefs.defaultDirectionId, capabilities.pack),
            directions = visible,
            language = language,
            wotdNotification = if (Features.WOTD_NOTIFICATION) {
                WotdNotifUi(prefs.wotdNotificationEnabled, prefs.wotdNotificationMinute)
            } else {
                null
            },
            versionName = BuildConfig.VERSION_NAME,
            entryCount = capabilities.totalEntries.takeIf { it > 0 },
            links = linkTargets.visibleFor(capabilities.pack),
            otherApps = linkTargets.otherAppsFor(capabilities.pack),
            sheet = overlays.sheet,
            smartTranslate = prefs.smartTranslate,
            confirmDelete = overlays.confirmDelete,
            deleting = overlays.deleting,
            paywall = overlays.paywall,
        )
    }

    private fun persist(block: suspend UserPreferencesRepository.() -> Unit) {
        viewModelScope.launch { guarded { prefs.block() } }
    }

    /**
     * Runs [block]; a failure is recorded, [onFailure] undoes what the screen already shows, and a generic error
     * toast says the change was not saved. Cancellation still propagates.
     */
    private suspend fun guarded(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.record(t)
            onFailure()
            effectChannel.send(ProfileEffect.Toast(UiText(R.string.entry_action_failed)))
        }
    }

    /** What [block] returns, or null when it fails (recorded); cancellation still propagates. */
    private inline fun <T> recorded(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        errors.record(t)
        null
    }

    private fun send(effect: ProfileEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** A Google account's card; a guest has none (the signed-out card shows). */
        fun Account.toUi(): AccountUi? {
            if (isGuest) return null
            val name = name?.trim()?.takeIf { it.isNotEmpty() }
            val email = email.orEmpty()
            return AccountUi(name = name, email = email, initials = initials(name, email))
        }

        /** "TD" for "Tenzin Dolma", "T" for "tenzin", else the e-mail's first letter. */
        fun initials(name: String?, email: String): String {
            val words = name?.split(Regex("\\s+")).orEmpty().filter { it.isNotEmpty() }
            val letters = listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull()).map { it.first() }
                .ifEmpty { listOfNotNull(email.firstOrNull()) }
            return letters.joinToString("").uppercase(Locale.ROOT)
        }

        /**
         * The plan card for [account] and today's [quota]: the pitch with no user; a guest's count (the pitch until
         * it is known); a Google account's free plan, or no card until its usage is known. A quota of the other kind
         * (a refresh that has not caught up with a sign-in or sign-out) is not shown.
         */
        /**
         * The plan card: Pro whenever the server's quota says so, with the store's active [entitlement] filling in the
         * plan and dates (in [zone]); switching to yearly needs a monthly subscription while Pro is [onSale].
         */
        fun planCard(
            account: Account?,
            quota: QuotaState?,
            entitlement: ProEntitlement?,
            onSale: Boolean,
            zone: ZoneId,
        ): PlanCardUi? = when {
            account == null -> PlanCardUi.NoUser
            account.isGuest -> quota?.takeIf { it.quota.plan == Plan.GUEST }
                ?.let { PlanCardUi.Guest(it.quota.used, it.quota.limit, it.freeLimit) }
                ?: PlanCardUi.NoUser
            else -> quota?.quota?.let { q ->
                when (q.plan) {
                    Plan.GUEST -> null
                    Plan.FREE -> PlanCardUi.Free(q.used, q.limit, q.maxChars)
                    Plan.PRO -> PlanCardUi.Pro(
                        plan = entitlement?.plan,
                        renewsOn = entitlement?.expiresAt?.atZone(zone)?.toLocalDate(),
                        willRenew = entitlement?.willRenew == true,
                        billingIssue = entitlement?.billingIssue == true,
                        used = q.used,
                        limit = q.limit,
                        maxChars = q.maxChars,
                        canSwitch = onSale && entitlement?.plan == ProPlan.MONTHLY,
                    )
                }
            }
        }

        /**
         * The stored default when it is visible, else the first visible direction (a data release removed or emptied
         * it); a stored id of the other pack (right after a switch) falls back to [pack]'s own default first.
         * Display only: the stored preference is left as it is.
         */
        fun defaultDirection(visible: List<Direction>, storedId: String, pack: DictionaryPack): Direction? =
            visible.firstOrNull { it.id == storedId }
                ?: visible.firstOrNull { it.id == pack.defaultDirectionId }
                ?: visible.firstOrNull()
    }
}
