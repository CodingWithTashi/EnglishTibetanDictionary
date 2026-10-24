# Translation in the App: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship machine translation in the Android app (Translate tab, smart translate in search, saved and recent
translations, Home and Profile usage) with the guest → Google sign-in → Pro fake door ladder, stopping at the free
limit.

**Architecture:** The backend gains a guest plan (from the ID token's sign-in provider), `plan` and `freeLimit` in its
replies, and a `deleteAccount` callable. The app keeps every Firebase type behind interfaces (`AuthRepository`,
`TranslationApi`, `Analytics`) bound in one Hilt module, so ViewModel and Robolectric tests run on fakes. A single
`TranslationRepository` owns quota and availability; the Translate tab, Search, Home, Saved and Profile only read it.

**Tech Stack:**
- Backend: TypeScript on Node 22, firebase-functions v2, Vitest, Firestore emulator.
- App: Kotlin, Compose + M3, Hilt, Room, DataStore, Firebase BoM 34.19.0 (Auth, Functions, App Check), Credential
  Manager and googleid, Robolectric, Turbine, Truth, MockK.

**Spec:** `docs/superpowers/specs/2026-10-22-translation-app-integration-design.md`. Read it with this plan; the UI
source is `docs/tibetan-dictionary-ui-revamp/project/Translate & Dictionary.dc.html`. Screen ids such as 1b and 1t
refer to it. Read the HTML for exact sizes and colours, and don't render it.

## Global Constraints

- **Commits:** none unless the owner asks. When asked, use no AI or Claude attribution in any form (no
  `Co-Authored-By`, no session lines). Never push.
- **Live project:** no deploys, IAM grants or billable calls without the owner's explicit go. Emulator tests use
  project `demo-translation` only.
- **Plans and limits:** guest `{ dailyLimit: 3, maxChars: 100 }`, free `{ dailyLimit: 10, maxChars: 100 }`, pro
  `{ dailyLimit: 300, maxChars: 500 }` (unused).
- **Region:** `asia-south1`. Callables are `translate`, `getTranslationStatus` and `deleteAccount`.
- **No user text** (input or translation) in logs, Crashlytics messages or Analytics params, on either side.
- **Firebase in the app:**
  - no Firebase type crosses a repository or API interface;
  - real implementations must not touch Firebase in constructors;
  - they must behave as "unavailable" when `FirebaseApp.getApps(context)` is empty. That is the case in Robolectric
    tests: `MainActivitySmokeTest` must keep passing.
- **Backend code:** ESM imports end in `.js`; `console` is banned outside `scripts/`; Prettier and typescript-eslint
  strictTypeChecked rules apply (backend/CLAUDE.md).
- **Backend coverage:** thresholds in `vitest.config.ts` (unit: 95 lines, 94 branches) and
  `vitest.emulator.config.ts` (98/92) never go down.
- **Strings:** every new string key lands in both `res/values/strings.xml` and `res/values-bo/strings.xml`, with
  pack-specific copy in `strings_pack_bo.xml` / `strings_pack_dz.xml` and their `values-bo` copies. English copy is
  taken verbatim from the spec. Tibetan is drafted and listed in Task 16 for owner review.
- **Test tags:** the existing `screen_element` convention, for example `translate_button`.
- **App gate:** `./gradlew assembleDebug testDebugUnitTest lintDebug` (CI's command).
- **Backend gate** (from `backend/functions`): `npm run format:check && npm run lint && npm run typecheck &&
  npm run coverage && npm run coverage:emulator && npm run build`.
- **Wylie** is never translated. A bo/dz source needs Tibetan letters (U+0F40–U+0FBC).
- **Pinned dependencies:** `androidx.credentials` (`credentials` and `credentials-play-services-auth`) at the newest
  stable ≥ 1.5.0, and `com.google.android.libraries.identity.googleid:googleid` at the newest stable ≥ 1.1.1. Check
  Google Maven and pin the exact version. Coroutines play-services uses the existing `kotlinx-coroutines` version.

## Review Focus

1. **Process death on the Translate tab.** A ViewModel recreated from the same `SavedStateHandle` shows the same
   input text and direction (Task 9).
2. **Pack switch while Translate is open.** Switching the pack from bo to dz moves the bar to Dzongkha ⇄ English,
   clears the result and shows the dz pack's recents (Task 9).
3. **A 6 000-character paste.** The counter shows 6000 / 100 in red, Translate is disabled and no call is made, so
   the server's 5 000 raw cap is never hit (Task 9).
4. **Offline on first use with no Firebase user.** Anonymous sign-in fails with an IO error. The Translate tab shows
   the offline card ("Not counted"), the app doesn't crash, and Retry tries sign-in again (Task 7, Task 9).
5. **The server says `unauthenticated`** (user deleted or disabled elsewhere). The repository signs out locally once
   and returns `Unauthenticated`. The next tap creates a fresh guest instead of failing forever (Task 7).

---

## File Structure

**Backend (`backend/functions`)**
- `src/service/plan.ts`: `Plan`, `PlanUser`, `isGuest`, `resolvePlan`.
- `src/config/runtimeConfig.ts`: the `guest` limits.
- `src/service/translateHandler.ts`: token-aware auth, guest limits, `plan` and `freeLimit` in replies.
- `src/service/accountHandler.ts` (new): `handleDeleteAccount`.
- `src/store/quotaStore.ts`: `deleteAll`.
- `src/store/userDeleter.ts` (new): wraps Admin Auth.
- `src/service/errors.ts`: reason `guest_account`.
- `src/index.ts`: export `deleteAccount`.
- Tests under `test/unit` and `test/emulator`; `README.md`.

**App (`app/src/main/java/com/kharagedition/tibetandictionary/`)**
- `data/translation/`:
  - `TranslationModels.kt`: Lang, LangPair, Plan, Provider, Quota, replies, refusals;
  - `TranslationApi.kt`, `CallableInvoker.kt`, `CallableTranslationApi.kt`, `FirebaseCallableInvoker.kt`;
  - `TranslationRepository.kt`, `TranslationRepositoryImpl.kt`;
  - `TranslationHistoryRepository(.kt|Impl.kt)`, `SavedTranslationsRepository(.kt|Impl.kt)`.
- `data/auth/`: `AuthRepository.kt`, `AuthBackend.kt`, `FirebaseAuthBackend.kt`, `GoogleCredentialSource.kt`,
  `CredentialManagerGoogleSource.kt`, `FirebaseAuthRepository.kt`, `DeleteAccount.kt`.
- `data/local/`: `entity/RecentTranslationEntity.kt`, `entity/SavedTranslationEntity.kt`, `dao/TranslationDao.kt`,
  `UserDatabase.kt` (v3), `UserDatabaseMigrations.kt` (2→3).
- `domain/`: `TranslationText.kt`, `PhraseSegmenter.kt`.
- `core/`: `firebase/FirebaseAvailability.kt`, `analytics/Analytics.kt`, `analytics/FirebaseAnalyticsLogger.kt`,
  `di/FirebaseModule.kt`.
- App Check providers: `app/src/debug/java/.../core/appcheck/AppCheckProviders.kt` and
  `app/src/release/java/.../core/appcheck/AppCheckProviders.kt`.
- `ui/translate/`: `TranslateUiState.kt`, `TranslateViewModel.kt`, `TranslateScreen.kt`, `TranslateSections.kt`,
  `TranslateCards.kt`, `TodaySheet.kt`.
- `ui/components/ProSheet.kt`, `ui/model/SignInAction.kt`, `ui/search/SmartTranslate.kt`.
- Changed: `navigation/MainTab.kt`, `ui/components/Navigation.kt`, `ui/main/*`, `navigation/AppNavHost.kt`,
  `ui/home/*`, `ui/search/*`, `ui/saved/*`, `ui/profile/*`, `ui/onboarding/*`, `ui/theme/DictColors.kt`,
  `ui/theme/DictIcons.kt`, `Features.kt`, `DictionaryApplication.kt`, `data/prefs/*`,
  `data/repository/DictionaryRepository*.kt`, `data/model/PackResources.kt` plus both packs, and `domain/SavedGrouping.kt`.
- Test fakes (`app/src/test/.../fakes/`): `FakeAuthRepository`, `FakeTranslationApi`, `FakeTranslationRepository`,
  `FakeTranslationHistoryRepository`, `FakeSavedTranslationsRepository`, `RecordingAnalytics`.
- `docs/translation-launch-checklist.md` (new, Task 16).

---

### Task 1: Backend guest plan, `plan` and `freeLimit`

**Files:**
- Modify: `backend/functions/src/service/plan.ts`, `src/config/runtimeConfig.ts`, `src/service/translateHandler.ts`
- Test: `test/unit/plan.test.ts` (new), `test/unit/runtimeConfig.test.ts`, `test/unit/translateHandler.test.ts`,
  `test/emulator/translatePipeline.test.ts`
- Docs: `backend/README.md`

**Interfaces:**
- Produces (used by Task 2 and the app):
```ts
// src/service/plan.ts
export type Plan = "guest" | "free" | "pro";
export interface PlanUser { uid: string; isGuest: boolean }
export type PlanResolver = (user: PlanUser) => Promise<Plan>;
/** Anonymous with no linked identity. */
export function isGuest(auth: CallableAuth): boolean;
export const resolvePlan: PlanResolver; // guest → "guest", else "free"

// src/service/translateHandler.ts
export interface CallableAuth {
  uid: string;
  token?: { firebase?: { sign_in_provider?: string; identities?: Record<string, unknown> } };
}
export interface CallableLike { data: unknown; auth?: CallableAuth | undefined }
export function requireUser(request: CallableLike): PlanUser; // throws unauthenticated
export interface StatusResponse extends QuotaInfo { enabled: boolean; pairs: PairKey[]; freeLimit: number }
// RuntimeConfig gains: readonly guest: PlanLimits  (default { dailyLimit: 3, maxChars: 100 })
// daily_limit details: { limit, resetsAt, plan, freeLimit }
```

`CallableAuth` lives in `translateHandler.ts` next to `CallableLike`. `plan.ts` imports the type only. The
firebase-functions `CallableRequest.auth` (with `DecodedIdToken`) must satisfy it structurally. Don't cast in
`index.ts`.

- [ ] **Step 1: Write the failing tests**

`test/unit/plan.test.ts`:
```ts
test.each([
  ["anonymous, no identities", { sign_in_provider: "anonymous", identities: {} }, true],
  ["anonymous, identities missing", { sign_in_provider: "anonymous" }, true],
  ["anonymous token minted after a Google link", { sign_in_provider: "anonymous", identities: { "google.com": ["1"] } }, false],
  ["google", { sign_in_provider: "google.com", identities: { "google.com": ["1"], email: ["a@b.c"] } }, false],
  ["no firebase claim", undefined, false],
])("isGuest: %s", (_n, firebase, expected) => {
  expect(isGuest({ uid: "u", token: firebase === undefined ? {} : { firebase } })).toBe(expected);
});
test("resolvePlan maps guests to guest and everyone else to free", async () => {
  expect(await resolvePlan({ uid: "u", isGuest: true })).toBe("guest");
  expect(await resolvePlan({ uid: "u", isGuest: false })).toBe("free");
});
```

`test/unit/runtimeConfig.test.ts`, following the file's existing `free` and `pro` cases:
- the default `guest` is `{ dailyLimit: 3, maxChars: 100 }`;
- a doc's `guest: { dailyLimit: 5 }` gives `{ 5, 100 }`;
- `guest: { dailyLimit: "5" }` logs `config: invalid "guest"` at ERROR and keeps the default.

`test/unit/translateHandler.test.ts`. Add a `guestRequest(data)` helper with
`auth: { uid, token: { firebase: { sign_in_provider: "anonymous", identities: {} } } }`, and wire `setup()` to accept
`resolvePlan` (default: the real `resolvePlan`, so existing tests still get "free" from `token: undefined`).
- `"a guest gets 3 a day and the 4th is daily_limit with plan and freeLimit"`: three translations succeed with
  `quota.plan === "guest"`, `limit === 3`. The fourth rejects with `resource-exhausted`, `reasonOf === "daily_limit"`,
  and `details` `toMatchObject({ limit: 3, plan: "guest", freeLimit: 10 })`, with `resetsAt` a string.
- `"a free user's daily_limit carries plan free and freeLimit 10"`.
- `"the plan resolver gets the uid and whether the user is a guest"`: a `vi.fn` resolver is called with
  `{ uid: "user-1", isGuest: true }` for `guestRequest`.
- `"status reports freeLimit and the guest plan"`: `handleStatus(guestRequest(undefined))` resolves
  `toMatchObject({ plan: "guest", limit: 3, remaining: 3, freeLimit: 10 })`.
- `"a guest's text is checked against guest maxChars"`: with `config.guest.maxChars = 5`, six Tibetan letters give
  `text_too_long` with `maxChars: 5`.

`test/emulator/translatePipeline.test.ts`:
- `"usage carries over when a guest links Google"`. The same uid makes 3 guest translations (distinct texts, so
  there are no cache hits; use the file's fake router). The 4th as a guest is `daily_limit`. Then, with a Google
  token for the same uid, status gives `{ plan: "free", used: 3, limit: 10, remaining: 7 }`, and a translation
  succeeds with `quota.used === 4`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend/functions && npx vitest run test/unit/plan.test.ts test/unit/runtimeConfig.test.ts test/unit/translateHandler.test.ts`
Expected: FAIL. `isGuest` isn't exported, `guest` is undefined and `freeLimit` is missing.

- [ ] **Step 3: Implement**

- `plan.ts`: `isGuest` per the table above; `resolvePlan`.
- `runtimeConfig.ts`: add `guest` to `RuntimeConfig`, `DEFAULT_CONFIG` and the per-field parse, mirroring `free`.
- `translateHandler.ts`:
  - `requireUid` becomes `requireUser`, returning `{ uid, isGuest: isGuest(auth) }`;
  - `deps.resolvePlan(user)`;
  - `limitsFor` returns `config[plan]`;
  - the `daily_limit` details get `plan` and `freeLimit: config.free.dailyLimit`;
  - `handleStatus` adds `freeLimit`.
- `README.md`:
  - the contract: the `guest` plan; `freeLimit` in the status reply; `plan` and `freeLimit` in the `daily_limit`
    row;
  - the config JSON example gets `"guest": { "dailyLimit": 3, "maxChars": 100 }`;
  - Known limits: "Signing out, or reinstalling as a guest, starts a new guest uid with its own 3 a day."

- [ ] **Step 4: Run the tests to verify they pass, then the emulator test**

Run: `npx vitest run test/unit` → PASS.
Run: `npx firebase emulators:exec --only firestore --project demo-translation "npx vitest run --config vitest.emulator.config.ts test/emulator/translatePipeline.test.ts test/emulator/callables.test.ts"` → PASS.

- [ ] **Step 5: Run the backend gate (Global Constraints)**

Expected: every command exits 0, coverage at or above the thresholds.

---

### Task 2: Backend `deleteAccount` callable

**Files:**
- Create: `backend/functions/src/service/accountHandler.ts`, `src/store/userDeleter.ts`
- Modify: `src/store/quotaStore.ts`, `src/service/errors.ts`, `src/index.ts`, `test/unit/fakes.ts`
- Test: `test/unit/accountHandler.test.ts` (new), `test/unit/userDeleter.test.ts` (new), `test/unit/index.test.ts`,
  `test/emulator/quotaStore.test.ts`, `test/emulator/callables.test.ts`
- Docs: `backend/README.md`

**Interfaces:**
- Consumes: `requireUser`, `CallableLike` (Task 1).
- Produces:
```ts
// quotaStore.ts — QuotaStore gains:
/** Deletes every dailyUsage doc whose `uid` field is `uid`, in batches of 400; resolves to how many. */
deleteAll(uid: string): Promise<number>;
// userDeleter.ts
export interface UserDeleter { deleteUser(uid: string): Promise<void> }
/** Admin Auth deleteUser; an `auth/user-not-found` error counts as already deleted. */
export function createUserDeleter(auth: Pick<Auth, "deleteUser">): UserDeleter;
// accountHandler.ts
export interface AccountDeps { quota: Pick<QuotaStore, "deleteAll">; users: UserDeleter; log: Logger }
export function handleDeleteAccount(request: CallableLike, deps: AccountDeps): Promise<{ deleted: true }>;
// errors.ts ErrorReason gains "guest_account"
// index.ts
export const deleteAccount; // onCall({ ...common, maxInstances: 2 }, ...)
```

The order is: usage docs first, then the Auth user. A failure after the docs are gone is `internal` and logged.
A retry is safe because both steps are idempotent. The log is `info("account deleted", { uid, usageDocs })`.

- [ ] **Step 1: Write the failing tests**

`test/unit/accountHandler.test.ts`, with a fake quota whose `deleteAll` records calls and returns 3, and a recording
`UserDeleter`:
- `"a signed-in user's usage and account are deleted, usage first"`: resolves `{ deleted: true }`; the call order is
  `["deleteAll:user-1", "deleteUser:user-1"]`; the log has `{ uid: "user-1", usageDocs: 3 }`.
- `"no user is unauthenticated"`: code `unauthenticated`, reason `unauthenticated`.
- `"a guest is refused with guest_account and nothing is deleted"`: code `failed-precondition`, reason
  `guest_account`, no calls.
- `"a failing auth delete is internal and logged"`: code `internal`; an error log is present.

`test/unit/userDeleter.test.ts`:
- `"user-not-found resolves"` (`deleteUser` rejects with `{ code: "auth/user-not-found" }`);
- `"other errors reject"`.

`test/unit/index.test.ts`: `"deleteAccount is a callable in asia-south1 with no secrets"`:
`toMatchObject({ region: ["asia-south1"], maxInstances: 2, serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com", callableTrigger: {} })`,
and `secretEnvironmentVariables ?? []` equals `[]`.

`test/emulator/quotaStore.test.ts`:
- `"deleteAll removes only that user's docs, across days"`: reserve for user-1 on 3 days and user-2 on 1 day.
  `deleteAll("user-1")` gives 3; user-1 reads `{0,0}` on every day; user-2 keeps `{1,…}`.
- `"deleteAll with nothing to delete is 0"`.

`test/emulator/callables.test.ts`:
- `"deleteAccount without a user is unauthenticated"`;
- `"deleteAccount as a guest is guest_account"`.

Build the guest `CallableRequest` with `token: { firebase: { sign_in_provider: "anonymous", identities: {} } } as unknown as DecodedIdToken`.
The signed-in path isn't run here, because it would reach real Admin Auth.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npx vitest run test/unit/accountHandler.test.ts test/unit/userDeleter.test.ts test/unit/index.test.ts`
Expected: FAIL (the modules are missing).

- [ ] **Step 3: Implement** the interfaces above.
  - `index.ts`: `getAccountDeps()` builds lazily, like the existing deps, with `createUserDeleter(getAuth())`
    (`firebase-admin/auth`). Wrap errors with `toHttpsError`.
  - `memoryQuota` in `fakes.ts` gains `deleteAll`.

- [ ] **Step 4: Run the unit tests, then the emulator tests**

Run: `npx vitest run test/unit` → PASS.
Run: `npx firebase emulators:exec --only firestore --project demo-translation "npx vitest run --config vitest.emulator.config.ts"` → PASS.

- [ ] **Step 5: Docs and gate**

- `README.md`:
  - a function-table row for `deleteAccount`, and a section with the request (no data), the response
    `{ deleted: true }`, and the errors (`unauthenticated`, `failed-precondition`/`guest_account`, `internal`);
  - the Data table: `dailyUsage` is also deleted by `deleteAccount`;
  - the Deploy runbook, step 1: add `roles/firebaseauth.admin` to the role loop;
  - step 3: "Google: Authentication → Sign-in method → Google → Enable; add SHA-1/SHA-256 of the debug, upload and
    Play app-signing keys, then re-download `app/google-services.json`";
  - the deploy command is unchanged, and now also deploys `deleteAccount`.
- Run the backend gate. Expected: green.

---

### Task 3: App dependencies, App Check, Firebase availability, Analytics

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `DictionaryApplication.kt`
- Create: `core/firebase/FirebaseAvailability.kt`, `core/analytics/Analytics.kt`,
  `core/analytics/FirebaseAnalyticsLogger.kt`, `core/di/FirebaseModule.kt`,
  `app/src/debug/java/com/kharagedition/tibetandictionary/core/appcheck/AppCheckProviders.kt`,
  `app/src/release/java/com/kharagedition/tibetandictionary/core/appcheck/AppCheckProviders.kt`
- Test: `app/src/test/.../core/analytics/AnalyticsEventTest.kt`, `fakes/RecordingAnalytics.kt`

**Interfaces:**
- Produces:
```kotlin
// core/firebase/FirebaseAvailability.kt
@Singleton class FirebaseAvailability @Inject constructor(@ApplicationContext context: Context) {
    /** False where the default FirebaseApp was not created (Robolectric, missing google-services). */
    val isAvailable: Boolean
}
// core/analytics/Analytics.kt
interface Analytics { fun log(event: AnalyticsEvent) }
enum class TranslateSurface(val value: String) { TRANSLATE_TAB("translate_tab"), SEARCH("search") }
enum class PromptSource(val value: String) {
    TRANSLATE_LIMIT("translate_limit"), SEARCH_LIMIT("search_limit"), TODAY_SHEET("today_sheet"),
    PROFILE_ACCOUNT("profile_account"), PROFILE_PLAN("profile_plan"), TOO_LONG("too_long"),
}
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, String>
    data class Translate(val outcome: String, val cached: Boolean, val pair: String, val plan: String?, val surface: TranslateSurface) : AnalyticsEvent
    data class QuotaLimitHit(val plan: String, val surface: TranslateSurface) : AnalyticsEvent
    /** The user tapped a sign-in button. */
    data class SignInPrompt(val source: PromptSource) : AnalyticsEvent
    data class SignInSuccess(val linked: Boolean) : AnalyticsEvent
    data class ProInterest(val source: PromptSource) : AnalyticsEvent
    data object AccountDeleted : AnalyticsEvent
}
// app/src/{debug,release}/.../core/appcheck/AppCheckProviders.kt
internal fun appCheckProviderFactory(): AppCheckProviderFactory // Debug… in debug, PlayIntegrity… in release
```
- `core/di/FirebaseModule.kt`: `@Module @InstallIn(SingletonComponent) abstract class FirebaseModule` with
  `@Binds @Singleton bindAnalytics(impl: FirebaseAnalyticsLogger): Analytics`. Tasks 5 and 6 add their bindings
  here. Tests that uninstall modules must uninstall `FirebaseModule` too (Task 11).

Event names and param keys, exactly:
- `translate`: `outcome`, `cached` ("true"/"false"), `pair`, `plan` (omitted when null), `surface`;
- `quota_limit_hit`: `plan`, `surface`;
- `sign_in_prompt`: `source`;
- `sign_in_success`: `linked`;
- `pro_interest`: `source`;
- `account_deleted`: no params.

- [ ] **Step 1: Write the failing test**

`AnalyticsEventTest`:
```kotlin
@Test fun `translate event carries only the allowed params`() {
    val e = AnalyticsEvent.Translate("translated", cached = true, pair = "bo-en", plan = "guest", surface = TranslateSurface.SEARCH)
    assertThat(e.name).isEqualTo("translate")
    assertThat(e.params).containsExactly("outcome", "translated", "cached", "true", "pair", "bo-en", "plan", "guest", "surface", "search")
}
@Test fun `a translate event without a plan leaves the key out`() { /* plan = null → no "plan" key */ }
@Test fun `the other events have the documented names and params`() { /* one assertion per event above */ }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*AnalyticsEventTest'`
Expected: FAIL (unresolved reference `AnalyticsEvent`).

- [ ] **Step 3: Implement**

- Version catalogue entries: `firebase-auth`, `firebase-functions`, `firebase-appcheck-playintegrity`,
  `firebase-appcheck-debug` (all BoM-managed, no version), `androidx-credentials`,
  `androidx-credentials-play-services-auth`, `googleid`, and `kotlinx-coroutines-play-services` (version.ref
  `kotlinx-coroutines`).
- `build.gradle.kts`: `implementation` for every one of them, including `firebase-appcheck-playintegrity`, except
  `firebase-appcheck-debug`, which is `debugImplementation`, so release code can't reference it.
- `proguard-rules.pro`:
  ```
  -if class androidx.credentials.CredentialManager
  -keep class androidx.credentials.playservices.** { *; }
  ```
- `FirebaseAnalyticsLogger` uses `FirebaseAnalytics.getInstance(context).logEvent(name, bundleOf(params))`, only when
  `availability.isAvailable`, inside `runCatching`.
- `DictionaryApplication.configureFirebase()`: inside the existing `runCatching`, after the empty-apps return, call
  `FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())`.
- `RecordingAnalytics` (fake): `val events = mutableListOf<AnalyticsEvent>()`.

- [ ] **Step 4: Run the test and the release build**

Run: `./gradlew testDebugUnitTest --tests '*AnalyticsEventTest' assembleDebug assembleRelease`
Expected: PASS. Both builds succeed, so R8 runs clean with the new rules.

- [ ] **Step 5: Run the app gate.** Expected: green, and `MainActivitySmokeTest` still passes.

---

### Task 4: `TranslationText`: client-side normalisation, counting and script rules

**Files:**
- Create: `domain/TranslationText.kt`, `data/translation/TranslationModels.kt` (only `Lang` and `LangPair` here;
  Task 5 adds the rest)
- Test: `app/src/test/.../domain/TranslationTextTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class Lang(val code: String) { BO("bo"), DZ("dz"), EN("en");
    val usesTibetanScript: Boolean get() = this != EN
    companion object { fun fromCode(code: String): Lang? } }
data class LangPair(val source: Lang, val target: Lang) {
    val key: String get() = "${source.code}-${target.code}"
    fun swapped(): LangPair = LangPair(target, source)
    companion object { fun fromKey(key: String): LangPair? } }
enum class ScriptCheck { OK, EMPTY, NOT_TIBETAN_SCRIPT, WRONG_SCRIPT }
data class OutputWord(val text: String, /** what a tap searches; null = not tappable */ val query: String?)
object TranslationText {
    fun normalize(raw: String): String
    /** Unicode code points. */
    fun length(text: String): Int
    fun check(normalized: String, source: Lang): ScriptCheck
    /** bo/dz: ≥ 3 syllables or ends in ། ; en: ≥ 3 words. */
    fun isPhrase(normalized: String, source: Lang): Boolean
    fun words(output: String, target: Lang): List<OutputWord>
}
```

Rules, copied from `backend/functions/src/domain/normalize.ts`:
- **`normalize`, in this order:**
  1. a lone surrogate becomes U+FFFD;
  2. NFC (`java.text.Normalizer`);
  3. remove U+200B, U+200C, U+200D and U+FEFF;
  4. `\r\n` and `\r` become `\n`;
  5. on each line, runs of non-newline whitespace become one space, and the line is trimmed;
  6. drop a blank line that follows another blank line;
  7. trim the whole text.
- **`check`:**
  - a Tibetan letter is U+0F40–U+0FBC; any other letter is `Character.isLetter`;
  - empty → EMPTY;
  - a bo/dz source with no Tibetan letter → NOT_TIBETAN_SCRIPT;
  - an English source with Tibetan letters and no other letters → WRONG_SCRIPT;
  - otherwise OK.
- **Syllables:** split on tsheg U+0F0B, shad U+0F0D, U+0F0E and whitespace, then drop empties.
- **`words`:**
  - en target: split on spaces. Each word's query is the word stripped of `.,?!;:"“”‘’()`. The query is null when
    it has fewer than 4 letters.
  - bo/dz target: split on whitespace, with the trailing tsheg or shad stripped for the query. If there is no
    whitespace, the whole output is one word whose query is the output minus a final shad. This matches the
    prototype's `outBo ? [s.out.text]`. It refines the spec's "tsheg-separated words": single syllables would link
    to meaningless searches.

- [ ] **Step 1: Write the failing tests** (`TranslationTextTest`). Each is one assertion with these exact values:
  - `normalize("  བཀྲ་ཤིས​  ")` == `"བཀྲ་ཤིས"`;
  - `normalize("a\r\n\r\n\r\nb")` == `"a\n\nb"`;
  - `normalize("a\t  b")` == `"a b"`;
  - `normalize("\uD800x")` == `"�x"`;
  - `length(normalize("གྷ"))` == 2 (NFC decomposes གྷ);
  - `length(normalize("ཱི"))` == 2;
  - `length("ཀ".repeat(100))` == 100;
  - `check("bkra shis", Lang.BO)` == NOT_TIBETAN_SCRIPT;
  - `check("བཀྲ་ཤིས། tashi", Lang.BO)` == OK;
  - `check("བཀྲ་ཤིས།", Lang.EN)` == WRONG_SCRIPT;
  - `check("hello བཀྲ", Lang.EN)` == OK;
  - `check("", Lang.EN)` == EMPTY;
  - `check("།་", Lang.BO)` == NOT_TIBETAN_SCRIPT (punctuation only);
  - `isPhrase("ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།", Lang.BO)` is true;
  - `isPhrase("བཀྲ་ཤིས", Lang.BO)` is false (2 syllables);
  - `isPhrase("བཀྲ་ཤིས།", Lang.BO)` is true (ends in shad);
  - `isPhrase("May all beings be happy", Lang.EN)` is true;
  - `isPhrase("thank you", Lang.EN)` is false;
  - `words("How are you?", Lang.EN)` == `[OutputWord("How", null), OutputWord("are", null), OutputWord("you?", null)]`;
  - `words("Meditate on compassion.", Lang.EN).last()` == `OutputWord("compassion.", "compassion")`;
  - `words("སེམས་ཅན་ ཐམས་ཅད་", Lang.BO)` == `[OutputWord("སེམས་ཅན་", "སེམས་ཅན"), OutputWord("ཐམས་ཅད་", "ཐམས་ཅད")]`;
  - `words("དགོན་པ་ག་པར་ཡོད།", Lang.BO)` == `[OutputWord("དགོན་པ་ག་པར་ཡོད།", "དགོན་པ་ག་པར་ཡོད")]`;
  - `LangPair.fromKey("bo-en")` == `LangPair(Lang.BO, Lang.EN)`, and `fromKey("zh-en")` is null.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*TranslationTextTest'` → FAIL.
- [ ] **Step 3: Implement** per the rules above.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS.

---

### Task 5: `TranslationApi`: callables, reply parsing, refusal mapping

**Files:**
- Modify: `data/translation/TranslationModels.kt`, `core/di/FirebaseModule.kt`
- Create: `data/translation/TranslationApi.kt`, `CallableInvoker.kt`, `CallableTranslationApi.kt`,
  `FirebaseCallableInvoker.kt`
- Test: `app/src/test/.../data/translation/CallableTranslationApiTest.kt`, `fakes/FakeTranslationApi.kt`

**Interfaces:**
- Consumes: `Lang` and `LangPair` (Task 4).
- Produces:
```kotlin
enum class Plan(val code: String) { GUEST("guest"), FREE("free"), PRO("pro") }
enum class Provider(val code: String) { AZURE("azure"), GOOGLE("google"), OTHER("") }
data class Quota(val plan: Plan, val used: Int, val limit: Int, val resetsAt: Instant, val maxChars: Int) {
    val remaining: Int get() = (limit - used).coerceAtLeast(0) }
data class TranslatedText(val translation: String, val cached: Boolean, val provider: Provider)
data class TranslateReply(val text: TranslatedText, val quota: Quota?)
data class StatusReply(val enabled: Boolean, val quota: Quota, val freeLimit: Int, val pairs: Set<LangPair>)
sealed interface TranslationRefusal {
    data class DailyLimit(val plan: Plan, val limit: Int, val resetsAt: Instant, val freeLimit: Int?) : TranslationRefusal
    data class TooLong(val maxChars: Int, val length: Int) : TranslationRefusal
    data class RateLimited(val retryAfterSec: Int?) : TranslationRefusal
    data object Offline : TranslationRefusal
    /** provider_unavailable or service_budget. */
    data object Busy : TranslationRefusal
    data object Disabled : TranslationRefusal
    data object UnsupportedPair : TranslationRefusal
    data object NotTibetanScript : TranslationRefusal
    data object WrongScript : TranslationRefusal
    data object EmptyText : TranslationRefusal
    data object Unauthenticated : TranslationRefusal
    data object GuestAccount : TranslationRefusal
    data object Unexpected : TranslationRefusal
}
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Refused(val refusal: TranslationRefusal) : ApiResult<Nothing>
}
interface TranslationApi {
    suspend fun translate(text: String, pair: LangPair, timeZone: String): ApiResult<TranslateReply>
    suspend fun status(timeZone: String): ApiResult<StatusReply>
    suspend fun deleteAccount(): ApiResult<Unit>
}
// CallableInvoker.kt
fun interface CallableInvoker { suspend fun call(name: String, data: Map<String, Any?>?): Any? }
/** A callable failure: [code] is the FirebaseFunctionsException.Code name (e.g. "RESOURCE_EXHAUSTED"). */
class CallableException(val code: String, val details: Map<String, Any?>?, cause: Throwable? = null) : Exception(code, cause)
```
- `CallableTranslationApi @Inject constructor(private val invoker: CallableInvoker, private val errors: ErrorReporter) : TranslationApi`
  maps failures as follows (see the README error table):

| Thrown | → refusal |
|---|---|
| `CallableException("RESOURCE_EXHAUSTED", reason "daily_limit")` | `DailyLimit(plan from details.plan, default FREE; limit; resetsAt parsed with Instant.parse; freeLimit or null)` |
| `RESOURCE_EXHAUSTED` with `rate_limited` | `RateLimited(retryAfterSec)` |
| `RESOURCE_EXHAUSTED` with `service_budget`, or `UNAVAILABLE` with `provider_unavailable` | `Busy` |
| `UNAVAILABLE` with no reason, `java.io.IOException`, or `FirebaseNetworkException` (arrives wrapped as IOException, see below) | `Offline` |
| `FAILED_PRECONDITION` with `translation_disabled` | `Disabled` |
| `FAILED_PRECONDITION` with `guest_account` | `GuestAccount` |
| `INVALID_ARGUMENT` with `text_too_long` | `TooLong(maxChars, length)` |
| `INVALID_ARGUMENT` with `unsupported_pair` / `not_tibetan_script` / `wrong_script` / `empty_text` | the matching object |
| `UNAUTHENTICATED` with any reason | `Unauthenticated` |
| anything else, including `bad_request` and `INTERNAL` | `Unexpected`, plus `errors.record(cause)` |

Reply parsing: numbers arrive as `Int`, `Long` or `Double`, so read them through `(value as Number).toInt()`. A
malformed reply is `Unexpected` and is recorded. `translate` sends
`mapOf("text", "source", "target", "timeZone")`; `status` sends `mapOf("timeZone")`; `deleteAccount` sends `null`.

- `FirebaseCallableInvoker @Inject constructor(availability: FirebaseAvailability)`:
  - uses `Firebase.functions("asia-south1").getHttpsCallable(name).call(data).await().getData()`;
  - wraps `FirebaseFunctionsException` as `CallableException(e.code.name, e.details as? Map<String, Any?>, e)`;
  - turns `FirebaseNetworkException` into `IOException(e)`;
  - throws `IOException("Firebase unavailable")` when `!availability.isAvailable`. That makes the Robolectric smoke
    path offline-like, and the UI handles it.
- Bind `CallableInvoker` to `FirebaseCallableInvoker`, and `TranslationApi` to `CallableTranslationApi`, in
  `FirebaseModule`.
- `FakeTranslationApi`: a queue of scripted `ApiResult`s per method, plus recorded calls.

- [ ] **Step 1: Write the failing tests** (`CallableTranslationApiTest`, with a fake `CallableInvoker`):
- `"translate parses the reply"`: the invoker returns the README's JSON as a map, with `used` as Long and
  `maxChars` as Double. The result is
  `Ok(TranslateReply(TranslatedText("Tashi Delek", cached = false, Provider.AZURE), Quota(Plan.FREE, 1, 10, Instant.parse("2026-10-23T18:00:00.000Z"), 100)))`.
- `"a cached reply without quota parses with quota null"`.
- `"translate sends text, codes and time zone"`: the recorded call is
  `("translate", mapOf("text" to "ཀ", "source" to "bo", "target" to "en", "timeZone" to "Asia/Thimphu"))`.
- `"status parses pairs and freeLimit"`: pairs `["bo-en","en-bo","zz-en"]` give `setOf(bo-en, en-bo)`; an unknown
  pair is dropped.
- A parameterised `"refusals map per the README"` test covering every row of the table above, plus
  `"an unexpected failure is recorded"` (`RecordingErrorReporter` has 1 entry).
- `"daily_limit for a guest carries plan GUEST and freeLimit 10"`.
- `"a malformed reply is Unexpected and recorded"` (`translation` missing).

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*CallableTranslationApiTest'` → FAIL.
- [ ] **Step 3: Implement** the interfaces above.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS. Then `assembleDebug` → OK.

---

### Task 6: `AuthRepository`: anonymous guest, Google link, collision, sign-out

**Files:**
- Create: `data/auth/AuthRepository.kt`, `AuthBackend.kt`, `FirebaseAuthBackend.kt`, `GoogleCredentialSource.kt`,
  `CredentialManagerGoogleSource.kt`, `FirebaseAuthRepository.kt`
- Modify: `core/di/FirebaseModule.kt`
- Test: `app/src/test/.../data/auth/FirebaseAuthRepositoryTest.kt`, `fakes/FakeAuthRepository.kt`,
  `fakes/FakeAuthBackend.kt`

**Interfaces:**
- Produces:
```kotlin
data class Account(val uid: String, val isGuest: Boolean, val name: String?, val email: String?)
sealed interface SignInResult {
    data class Success(val linked: Boolean) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val cause: Throwable) : SignInResult
}
interface AuthRepository {
    /** Null until a Firebase user exists (no anonymous user is created just by reading this). */
    val account: StateFlow<Account?>
    /** The current account, signing in anonymously first if there is none. Throws IOException when offline. */
    suspend fun ensureUser(): Account
    suspend fun signInWithGoogle(activity: Activity): SignInResult
    suspend fun signOut()
}
// AuthBackend.kt — the thin seam over FirebaseAuth
data class AuthUser(val uid: String, val isAnonymous: Boolean, val name: String?, val email: String?)
class AccountCollisionException(cause: Throwable? = null) : Exception(cause)
interface AuthBackend {
    val user: StateFlow<AuthUser?>
    suspend fun signInAnonymously(): AuthUser
    /** Links Google to the current anonymous user; throws AccountCollisionException if the account has a uid. */
    suspend fun linkGoogle(idToken: String): AuthUser
    suspend fun signInWithGoogle(idToken: String): AuthUser
    suspend fun refreshIdToken()
    suspend fun signOut()
}
// GoogleCredentialSource.kt
interface GoogleCredentialSource {
    /** The Google ID token from the "Sign in with Google" sheet; null when the user cancelled. */
    suspend fun requestIdToken(activity: Activity): String?
    suspend fun clearCredentialState()
}
```

`FirebaseAuthRepository @Inject constructor(backend: AuthBackend, credentials: GoogleCredentialSource, errors: ErrorReporter, @ApplicationScope scope: CoroutineScope)`:
- `account` = `backend.user` mapped to `Account(isGuest = isAnonymous)`, using `stateIn(scope, Eagerly, …)`.
- `ensureUser` runs under a `Mutex`, so concurrent callers share one anonymous sign-in.
- `signInWithGoogle`:
  1. `requestIdToken`; null gives `Cancelled`, with no backend call;
  2. the current user is anonymous: `linkGoogle`, then `Success(linked = true)`;
  3. on `AccountCollisionException`: `signInWithGoogle(idToken)`, then `Success(linked = false)`;
  4. no current user: `signInWithGoogle`, then `Success(false)`;
  5. after success, always `refreshIdToken()`;
  6. any other throwable gives `Failed` and `errors.record`. `CancellationException` is rethrown.
- `signOut` calls `backend.signOut()` then `credentials.clearCredentialState()`. A failure in the second is recorded,
  not thrown.

`FirebaseAuthBackend @Inject constructor(availability: FirebaseAvailability)`:
- `FirebaseAuth.getInstance()` is resolved lazily, only when `availability.isAvailable`. When it isn't, `user` stays
  null and every operation throws `IOException("Firebase unavailable")`.
- `user` comes from `addAuthStateListener`, through a `callbackFlow` → `stateIn`, or a `MutableStateFlow` updated by
  the listener.
- It maps `FirebaseAuthUserCollisionException` to `AccountCollisionException`, and `FirebaseNetworkException` to
  `IOException`.
- `refreshIdToken` is `currentUser.getIdToken(true).await()`.

`CredentialManagerGoogleSource @Inject constructor(@ApplicationContext context: Context)`:
- `CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build())))`;
- `GoogleIdTokenCredential.createFrom(result.credential.data).idToken`;
- `GetCredentialCancellationException` gives null;
- `clearCredentialState()` uses `ClearCredentialStateRequest()`.

`default_web_client_id` is generated by the google-services plugin.

Bind `AuthBackend`, `GoogleCredentialSource` and `AuthRepository` in `FirebaseModule`. `FakeAuthRepository`: a
settable `account`; `ensureUser` creates a guest `Account("guest-1", true, null, null)` or throws a scripted error;
`signInResult` is scripted; calls are counted.

- [ ] **Step 1: Write the failing tests** (`FirebaseAuthRepositoryTest`, with `FakeAuthBackend` and a fake credential
  source; the activity is `mockk<Activity>(relaxed = true)`):
- `"ensureUser signs in anonymously once when there is no user"`: two concurrent `ensureUser()` calls (two `async`)
  lead to exactly 1 `signInAnonymously`, and both return the same uid.
- `"ensureUser returns the existing user without signing in"`.
- `"ensureUser offline throws IOException"` (the backend throws IOException).
- `"a guest linking Google keeps the uid"`: the guest is `u1`; `linkGoogle` returns `u1`, non-anonymous. The result is
  `Success(linked = true)`, `account.value` is `Account("u1", false, "Tashi", "t@x.com")`, and `refreshIdToken` was
  called once.
- `"an account that already exists switches uid"`: `linkGoogle` throws `AccountCollisionException`, so
  `signInWithGoogle` is called. The result is `Success(linked = false)`, and the account uid is `u2`.
- `"cancelling changes nothing"`: the token is null, so the result is `Cancelled` and there are no backend calls.
- `"a failure is reported"`: `linkGoogle` throws `IllegalStateException`. The result is `Failed`, and
  `RecordingErrorReporter` has 1 entry.
- `"sign-out clears the credential state"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*FirebaseAuthRepositoryTest'` → FAIL.
- [ ] **Step 3: Implement** the interfaces above.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS. Then the app gate: `MainActivitySmokeTest` must
  pass with no `FirebaseApp`.

---

### Task 7: `TranslationRepository`: quota, availability, translate

**Files:**
- Create: `data/translation/TranslationRepository.kt`, `TranslationRepositoryImpl.kt`
- Modify: `core/di/RepositoryModule.kt` (bind it; the implementation has no direct Firebase dependency)
- Test: `app/src/test/.../data/translation/TranslationRepositoryTest.kt`, `fakes/FakeTranslationRepository.kt`

**Interfaces:**
- Consumes: `TranslationApi` (Task 5), `AuthRepository` (Task 6), `TranslationText` (Task 4), `Analytics` (Task 3),
  `AppClock` (existing).
- Produces:
```kotlin
data class QuotaState(val quota: Quota, val freeLimit: Int?) {
    val remaining: Int get() = quota.remaining
    /** "Sign in to get N more today": freeLimit − used, for guests only. */
    val moreWhenSignedIn: Int? get() = if (quota.plan == Plan.GUEST) freeLimit?.minus(quota.used)?.coerceAtLeast(0) else null
}
data class TranslationAvailability(val enabled: Boolean, val pairs: Set<LangPair>)
sealed interface TranslateResult {
    data class Translated(val text: TranslatedText) : TranslateResult
    data class Refused(val refusal: TranslationRefusal) : TranslateResult
}
interface TranslationRepository {
    val quota: StateFlow<QuotaState?>
    val availability: StateFlow<TranslationAvailability?>
    /** Creates the guest if needed, then refresh(force = false). Called when the Translate tab is shown. */
    suspend fun prepare()
    /** Re-reads status if forced, stale (> 5 min) or past resetsAt; does nothing when there is no Firebase user. */
    suspend fun refresh(force: Boolean = false)
    /** [text] raw; normalised here. */
    suspend fun translate(text: String, pair: LangPair, surface: TranslateSurface): TranslateResult
}
```

Rules for `TranslationRepositoryImpl @Inject constructor(api, auth, analytics, clock, @ApplicationScope scope)`:
- **Time zone:** `TimeZone.getDefault().id` on every call.
- **`translate`:**
  - `ensureUser()` first. An IOException gives `Refused(Offline)`; any other throwable gives `Refused(Unexpected)`
    and is recorded.
  - On `Ok`, set `quota` from `reply.quota` when present, keeping `freeLimit`.
  - On `DailyLimit`, set `quota = QuotaState(Quota(plan, used = limit, limit, resetsAt, maxChars = old?.maxChars ?: 100), freeLimit)`.
  - On `TooLong`, update `quota.maxChars`.
  - On `Disabled`, set `availability.enabled = false`.
  - On `UnsupportedPair`, call `refresh(force = true)`.
  - On `Unauthenticated`, call `auth.signOut()` once (Review Focus 5).
  - Log `Translate(outcome = "translated" | "cached" | "refused:<refusal simple name snake_case>", …)`, plus
    `QuotaLimitHit` on `DailyLimit`.
- **`refresh`:**
  - when `now >= quota.resetsAt`, first set `used = 0`, then fetch;
  - a status failure keeps the old values and is not surfaced.
- **Account changes:** when `auth.account`'s uid changes (sign-in switch, sign-out), clear `quota` and `availability`,
  then `refresh(force = true)` if there is an account. Collect this in `scope`.

- [ ] **Step 1: Write the failing tests** (`TranslationRepositoryTest`, with `FakeTranslationApi`,
  `FakeAuthRepository`, `RecordingAnalytics` and `FakeClock`; virtual time through `runTest`):
- `"translate creates the guest and stores the reply's quota"`: `ensureUser` count 1; `quota.value!!.quota.used == 1`.
- `"a cached reply without quota keeps the last quota"`.
- `"daily limit as a guest sets remaining 0 and moreWhenSignedIn 7"`: the refusal is `DailyLimit(GUEST, 3, t, 10)`
  after a status of `used = 3`. Then `remaining == 0`, `moreWhenSignedIn == 7`, and analytics has
  `QuotaLimitHit("guest", SEARCH)`.
- `"offline on first use is Offline and nothing is recorded as an error"` (Review Focus 4): `ensureUser` throws
  IOException.
- `"unauthenticated signs out once"` (Review Focus 5): the fake auth's `signOut` count is 1, and the result is
  `Refused(Unauthenticated)`.
- `"refresh does nothing without a user"`: no API status calls.
- `"refresh within 5 minutes is skipped; after 5 minutes it reads again"`.
- `"past resetsAt the quota shows used 0 before the new status arrives"`: the clock passes `resetsAt`; `refresh()`
  sets `used == 0` immediately (the API's status is suspended until released).
- `"disabled turns availability off"`.
- `"a uid change clears the quota and re-reads status"`.
- `"no analytics param contains the text"`: translating `"ཀཁག"` gives analytics events whose params values don't
  contain `"ཀཁག"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*TranslationRepositoryTest'` → FAIL.
- [ ] **Step 3: Implement** the rules above. `FakeTranslationRepository` gets settable `quota` and `availability`, a
  scripted result queue, and recorded calls (text, pair, surface), `prepare`, and `refresh(force)`.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS.

---

### Task 8: Local translations (Room v3), the smart-translate pref, generic grouping

**Files:**
- Create: `data/local/entity/RecentTranslationEntity.kt`, `entity/SavedTranslationEntity.kt`,
  `dao/TranslationDao.kt`, `data/repository/TranslationHistoryRepository.kt` + `Impl`,
  `data/repository/SavedTranslationsRepository.kt` + `Impl`
- Modify: `data/local/UserDatabase.kt` (VERSION 3, entities, `translationDao()`), `UserDatabaseMigrations.kt`
  (`MIGRATION_2_3`), `core/di/DatabaseModule.kt` (add the migration, provide the DAO), `core/di/RepositoryModule.kt`,
  `data/prefs/UserPrefs.kt`, `UserPreferencesRepository.kt` + `Impl`, `domain/SavedGrouping.kt`
- Generated: `app/schemas/.../UserDatabase/3.json`
- Test: `data/local/UserDatabaseMigrationTest.kt` (existing; add the 2→3 case), `data/repository/TranslationHistoryRepositoryImplTest.kt`, `SavedTranslationsRepositoryImplTest.kt`,
  `domain/SavedGroupingTest.kt`, `fakes/FakeUserPreferencesRepository.kt`, `fakes/FakeTranslationHistoryRepository.kt`,
  `fakes/FakeSavedTranslationsRepository.kt`

**Interfaces:**
- Produces:
```kotlin
data class TranslationRecord(val id: Long, val pair: LangPair, val source: String, val translation: String, val provider: Provider, val at: Instant)
interface TranslationHistoryRepository {
    /** Active pack's recents, newest first (at most 20 are kept per pack). */
    fun observe(limit: Int = 20): Flow<List<TranslationRecord>>
    /** Upserts by (pack, pair, source); moves it to the top. */
    suspend fun record(pair: LangPair, source: String, text: TranslatedText)
    suspend fun clear()
}
interface SavedTranslationsRepository {
    fun observe(): Flow<List<TranslationRecord>>
    fun observeIsSaved(pair: LangPair, source: String): Flow<Boolean>
    suspend fun save(pair: LangPair, source: String, text: TranslatedText): Long
    suspend fun remove(id: Long): TranslationRecord?
    /** Undo of remove: re-inserts with the same id and time. */
    suspend fun restore(record: TranslationRecord)
}
// UserPrefs gains: val smartTranslate: Boolean = true ; repository gains: suspend fun setSmartTranslate(enabled: Boolean)
// SavedGrouping gains a generic overload used by Task 14:
fun <T> groupBy(items: List<T>, at: (T) -> Instant, today: LocalDate, zone: ZoneId): List<Pair<SavedPeriod, List<T>>>
```

Both tables have these columns:

| Column | Type |
|---|---|
| `id` | INTEGER PK autoincrement |
| `pack_id` | TEXT NOT NULL |
| `source_lang` | TEXT NOT NULL |
| `target_lang` | TEXT NOT NULL |
| `source_text` | TEXT NOT NULL |
| `translation` | TEXT NOT NULL |
| `provider` | TEXT NOT NULL |
| time | `used_at` (recents) or `saved_at` (saved), INTEGER NOT NULL, epoch ms |

Indices: unique `(pack_id, source_lang, target_lang, source_text)`, and `(pack_id, used_at|saved_at)`.

The recents are trimmed to 20 per pack in the same `@Transaction` as the upsert, the way `RecentSearchDao` keeps 50.
The repositories scope to `databases.active` exactly as `RecentSearchesRepositoryImpl` does. `MIGRATION_2_3` creates
both tables and their indices with SQL identical to `3.json`: build once to generate the schema, then copy its
`createSql`. The prefs key is `smart_translate` (boolean).

- [ ] **Step 1: Write the failing tests**
- Migration: `"2 to 3 keeps saved words and creates the translation tables"`. Use `MigrationTestHelper` with
  `MIGRATION_1_2` and `MIGRATION_2_3`: a saved word inserted at v2 survives, and inserting into both new tables at v3
  works.
- History:
  - `"record upserts and moves to the top"`;
  - `"only 20 per pack are kept"` (record 21; the oldest is gone);
  - `"recents are per pack"` (activate dz; the bo rows are hidden);
  - `"clear removes the active pack's only"`.
- Saved:
  - `"save then observeIsSaved is true"`;
  - `"remove returns the record and restore puts back the same id"`;
  - `"saving the same pair+source twice keeps one row"`.
- `SavedGroupingTest`: `"groupBy uses the same periods as group"`. The same instants through `group` and `groupBy`
  give the same periods.
- `UserPreferencesRepositoryImplTest` (existing file): `"smartTranslate defaults to true and persists false"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*Migration*' --tests '*TranslationHistory*' --tests '*SavedTranslations*' --tests '*SavedGroupingTest' --tests '*UserPreferencesRepositoryImplTest'` → FAIL.
- [ ] **Step 3: Implement.** Bump `UserDatabase.VERSION` to 3; register `MIGRATION_2_3` in `DatabaseModule`. Fakes
  mirror the semantics.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS. Check that `app/schemas/.../3.json` exists and
  matches the migration (the migration test validates it).

---

### Task 9: `TranslateViewModel` and `SignInAction`

**Files:**
- Create: `ui/translate/TranslateUiState.kt`, `ui/translate/TranslateViewModel.kt`, `ui/model/SignInAction.kt`
- Modify: `data/model/PackResources.kt`, `TibetanPackResources.kt`, `DzongkhaPackResources.kt` (add
  `translateSamples`), `res/values*/strings_pack_*.xml` (sample arrays), `res/values*/strings.xml` (the toast and label
  strings this VM uses)
- Test: `ui/translate/TranslateViewModelTest.kt`, `ui/model/SignInActionTest.kt`

**Interfaces:**
- Consumes: Tasks 3–8.
- Produces:
```kotlin
// PackResources gains: @param:ArrayRes val translateSamples: Int   (source-language samples for the pack)
//   bo: ["ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།", "ང་བོད་པ་ཡིན།"]; dz: ["སྐུ་གཟུགས་བཟང་པོ།"]  (owner may add more)
//   English samples are shared: R.array.translate_samples_en = ["Where is the monastery?", "May all beings be happy"]
// in data/translation/TranslationModels.kt:
fun DictionaryPack.translationPairs(): List<LangPair> // TIBETAN: [bo-en, en-bo]; DZONGKHA: [dz-en, en-dz]

/** Open-in-Translate handoff (Search, Saved → Translate tab). In ui/translate/TranslateUiState.kt. */
data class TranslateRequest(val pair: LangPair, val text: String, val result: TranslatedText? = null)

class SignInAction @Inject constructor(auth: AuthRepository, translation: TranslationRepository, analytics: Analytics) {
    /** Runs Google sign-in; returns the toast to show, or null when cancelled. Logs sign_in_prompt and sign_in_success. */
    suspend fun run(activity: Activity, source: PromptSource): UiText?
}
enum class CounterLevel { NORMAL, NEAR, OVER }          // NEAR from 85% of maxChars, OVER above maxChars
enum class InlineHint { NOT_TIBETAN_SCRIPT, LOOKS_TIBETAN }
enum class TranslatePhase { IDLE, LOADING, DONE }
sealed interface TranslateBlock {
    data class GuestLimit(val used: Int, val more: Int?) : TranslateBlock
    data object DailyLimit : TranslateBlock
    data object Offline : TranslateBlock
    data object Busy : TranslateBlock
    data object Disabled : TranslateBlock
    data object Unexpected : TranslateBlock
}
data class UsagePillUi(val remaining: Int, val limit: Int, val low: Boolean)   // low = remaining <= 3
data class TranslationResultUi(val text: String, val words: List<OutputWord>, val cached: Boolean, val provider: Provider, val saved: Boolean)
data class RecentTranslationUi(val id: Long, val source: String, val translation: String, val pair: LangPair)
enum class TranslateSheet { TODAY, PRO }
@Immutable data class TranslateUiState(
    val pair: LangPair? = null, val canSwap: Boolean = false,
    val input: TextFieldValue = TextFieldValue(), val length: Int = 0, val maxChars: Int = 100,
    val counter: CounterLevel = CounterLevel.NORMAL, val hint: InlineHint? = null,
    val phase: TranslatePhase = TranslatePhase.IDLE, val result: TranslationResultUi? = null,
    val block: TranslateBlock? = null, val pill: UsagePillUi? = null, val plan: Plan? = null,
    val samples: List<String> = emptyList(), val recents: List<RecentTranslationUi> = emptyList(),
    val sheet: TranslateSheet? = null, val showProHint: Boolean = false, val canTranslate: Boolean = false,
    val inputEnabled: Boolean = true,
)
sealed interface TranslateEvent {
    data object Shown : TranslateEvent
    data class InputChanged(val value: TextFieldValue) : TranslateEvent
    data class Paste(val text: String) : TranslateEvent
    data object Clear : TranslateEvent
    data object Swap : TranslateEvent
    data object Translate : TranslateEvent
    data object Retry : TranslateEvent
    data class SampleTap(val text: String) : TranslateEvent
    data class RecentTap(val id: Long) : TranslateEvent
    data object ClearRecents : TranslateEvent
    data object Copy : TranslateEvent
    data object Share : TranslateEvent
    data object ToggleSave : TranslateEvent
    data class WordTap(val word: OutputWord) : TranslateEvent
    data object OpenToday : TranslateEvent
    data class OpenPro(val source: PromptSource) : TranslateEvent
    data object DismissSheet : TranslateEvent
    data object LookUpWords : TranslateEvent
    /** The LOOKS_TIBETAN hint's [Switch]. */
    data object SwitchDirection : TranslateEvent
    data class Load(val request: TranslateRequest) : TranslateEvent
}
sealed interface TranslateEffect {
    data class Copy(val text: String) : TranslateEffect
    data class Share(val text: String) : TranslateEffect
    data class OpenSearch(val query: String) : TranslateEffect
    data class Toast(val message: UiText) : TranslateEffect
}
@HiltViewModel class TranslateViewModel @Inject constructor(
    savedState: SavedStateHandle, translation: TranslationRepository, history: TranslationHistoryRepository,
    saved: SavedTranslationsRepository, prefs: UserPreferencesRepository, signInAction: SignInAction,
    analytics: Analytics, errors: ErrorReporter,
) : ViewModel() {
    val uiState: StateFlow<TranslateUiState>
    val effects: Flow<TranslateEffect>
    fun onEvent(event: TranslateEvent)
    fun signIn(activity: Activity, source: PromptSource)
}
```

Behaviour, all from the spec's Translate-tab section:
- **Pair:** the first of `prefs.dictionaryPack.translationPairs()` that is in `availability.pairs`. Before availability
  is known, take the pack's first pair. `canSwap` is true when the swapped pair is open.
- **Input text** is never truncated. `length` and `counter` come from `TranslationText` on the normalised text, and
  `maxChars` from the quota (default 100).
- **`hint`** comes from `TranslationText.check`: `NOT_TIBETAN_SCRIPT` → `NOT_TIBETAN_SCRIPT`; `WRONG_SCRIPT` →
  `LOOKS_TIBETAN`. `SwitchDirection` swaps the pair and keeps the text.
- **`canTranslate`** = non-empty, no hint, `length <= maxChars`, `phase != LOADING`, not disabled, and not at the
  limit.
- **`block`:**
  - quota `remaining == 0`: `GuestLimit(used, more = quotaState.moreWhenSignedIn)` for a guest, otherwise
    `DailyLimit`. This is derived from the quota, so it holds while the input is edited (1t).
  - otherwise the last call's failure: Offline, Busy, Disabled or Unexpected (`Unauthenticated` and `GuestAccount`
    count as Unexpected; `RateLimited` gives a toast and no block).
- **Editing the input** after DONE or a failure returns to IDLE and clears the failure block. The prototype does the
  same.
- **Translate:**
  - LOADING; a second `Translate` while LOADING is ignored;
  - on success: DONE; `history.record`; the result's `saved` from `observeIsSaved`;
  - `surface = TRANSLATE_TAB`.
- **Swap** turns the pair around. If a result is shown, the input becomes the result text and the result is cleared.
- **RecentTap** fills the input and the result (`cached = true`) with no API call.
- **ToggleSave:** save shows the toast `R.string.translation_saved` ("Saved to Translations"); remove shows
  `R.string.translation_removed` ("Removed from Saved").
- **Copy** shows the `Copy` effect and the toast `R.string.copied`. **Share** sends `"<source>\n\n<translation>"`.
  **WordTap** gives `OpenSearch(word.query)` when the query is not null. **LookUpWords** gives
  `OpenSearch(normalised input)`; the route's callback opens Search, just as a word tap does.
- **OpenPro** logs `ProInterest(source)` and sets `sheet = PRO`. **OpenToday** sets `sheet = TODAY`.
- **`showProHint`** = plan FREE and `length >= 0.85 * maxChars`.
- **`Shown`** calls `translation.prepare()`.
- **`Load(request)`** sets the pair, the text and the optional result (DONE, with no call).
- **SavedStateHandle keys:** `translate_text`, `translate_pair` (pair key) (Review Focus 1).
- **A pack change** while the tab is shown resets the pair to the new pack's first pair and clears the result and
  the failure block (Review Focus 2).
- **`signIn`** calls `signInAction.run`, then emits its toast if it isn't null.
- **`SignInAction` toasts:**
  - success with a known remaining: `R.string.signed_in_left` "Signed in · %1$d translations left today";
  - success otherwise: `R.string.signed_in` "Signed in";
  - failed: `R.string.sign_in_failed` "Couldn't sign in. Try again.";
  - cancelled: null.
  - It calls `translation.refresh(force = true)` after success, and before building the toast.

- [ ] **Step 1: Write the failing tests** (`TranslateViewModelTest`: `MainDispatcherRule`, Turbine, the fakes from
  Tasks 3–8, `FakeUserPreferencesRepository` with the Tibetan pack):
- `"Shown prepares translation and the pill shows remaining"`: the quota is FREE 3/10, giving
  `pill == UsagePillUi(7, 10, low = false)`.
- `"translate success shows the result, records a recent, updates the pill"`.
- `"a cached result is flagged"`.
- `"a second Translate while loading makes one call"`.
- `"guest at the limit shows GuestLimit with 7 more"`: a guest with 3/3 and `freeLimit` 10 gives
  `block == GuestLimit(3, 7)` and `canTranslate == false`.
- `"free at the limit shows DailyLimit and the input stays editable"`.
- `"offline shows Offline and Retry translates again"`.
- `"busy shows Busy"`, `"disabled disables input"`, `"rate limited toasts without a block"`.
- `"Wylie input shows NOT_TIBETAN_SCRIPT and makes no call"`.
- `"Tibetan typed with English source offers SwitchDirection, which flips the pair and keeps the text"`.
- `"a 6000-character paste is OVER, cannot translate, and makes no call"` (Review Focus 3):
  `length == 6000`, `counter == OVER`, no API calls.
- `"counter is NEAR from 85 and OVER above 100"` (85 → NEAR; 101 → OVER; 100 → NEAR).
- `"swap with a result moves the result into the input"`.
- `"recent tap restores without a call"`.
- `"save and unsave toast and update the heart"`.
- `"word tap opens search with the query"`; `"a word without a query does nothing"`.
- `"OpenPro logs pro_interest with the source"`.
- `"showProHint only for free plan from 85 characters"`.
- `"a recreated view model restores text and direction"` (Review Focus 1): build a second VM with the same
  `SavedStateHandle`.
- `"a pack switch moves to dz-en and clears the result"` (Review Focus 2).
- `"Load fills pair, text and result without a call"`.
- `SignInActionTest`:
  - `"success toasts the remaining after refresh"` (`signed_in_left` with 7);
  - `"cancel returns null and logs only the prompt"`;
  - `"failure toasts sign_in_failed"`;
  - `"logs sign_in_success with linked"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*TranslateViewModelTest' --tests '*SignInActionTest'` → FAIL.
- [ ] **Step 3: Implement** per the behaviour list. Add the string keys used here to both `strings.xml` files:
  `copied`, `translation_saved`, `translation_removed`, `signed_in`, `signed_in_left`, `sign_in_failed`,
  `translate_rate_limited` ("Too many at once. Try again in a few seconds."), plus the sample arrays.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS.

---

### Task 10: Translate screen UI (1b, 1s–1v), Today sheet, Pro sheet, theme tokens, icons

**Files:**
- Create: `ui/translate/TranslateScreen.kt` (the route `TranslateScreen(onOpenSearch: (String) -> Unit, viewModel)`
  plus stateless `TranslateContent(state, onEvent, onSignIn: (PromptSource) -> Unit)`), `TranslateSections.kt`
  (header and pill, language bar, input card, result card, samples, recents), `TranslateCards.kt` (the blocks,
  including `SignInCard`), `TodaySheet.kt`, `ui/components/ProSheet.kt`
- Modify: `ui/theme/DictColors.kt` (`warnContainer`, `onWarnContainer`, `warn` in Light and Dark),
  `ui/theme/DictIcons.kt` (`Translate`, `Paste`, `WifiOff`, each added to `all`), `ui/components/Cards.kt` if
  `SignInCard` reuses its frame, `res/values*/strings.xml`
- Test: `ui/translate/TranslateScreenTest.kt`, `ui/components/ProSheetTest.kt`, the existing icon and colour tests if
  they enumerate `all`

**Interfaces:**
- Consumes: `TranslateUiState`, `TranslateEvent` and `TranslateEffect` (Task 9).
- Produces:
```kotlin
@Composable fun ProSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier)   // 1za fake door; used by Tasks 10, 12, 15
@Composable fun TranslateScreen(onOpenSearch: (String) -> Unit, viewModel: TranslateViewModel = hiltViewModel())
```

Visuals come from the HTML at the cited ids. Use the existing `DictTheme` tokens; the amber tokens are the new
`warn*` ones:
- Light: `warnContainer` #FBE9C8, `onWarnContainer` #7A4E00, `warn` #8A5A00.
- Dark: take them from the dark mockups (1q, 1r, 1v). If no amber appears there, use `warnContainer` #4A3410,
  `onWarnContainer` #F3D08A, `warn` #E0B060, and note it for the owner.

Icon paths:
- `Translate`: `M4 8h13l-3-3M20 16H7l3 3`;
- `Paste`: `roundRect(6,4,12,17,2)` + `M9 3h6v3H9z`;
- `WifiOff`: `M2 8.5a15 15 0 0 1 20 0M5.5 12a10 10 0 0 1 13 0M9 15.5a5 5 0 0 1 6 0M12 19h.01M3 3l18 18`.

The route collects effects:
- `Copy`: `ClipboardManager`;
- `Share`: `ACTION_SEND` chooser (as `HomeScreen.shareText`);
- `OpenSearch`: the callback;
- `Toast`: the app toast host, as other screens do.

`onSignIn` calls `viewModel.signIn(LocalActivity.current!!, source)`.

Copy, verbatim from the spec:
- **Placeholders:** "ཡི་གེ་འདིར་འབྲི། · Type in Tibetan" for bo/dz, "Type English text" for en.
- **Hints:** "Type in Tibetan script — Wylie isn't translated"; "This looks like Tibetan" + [Switch].
- **Guest card:** "You've used today's 3 free translations" (with the plan's limit), then "Sign in with Google to get
  %d more today. It's free." [Continue with Google] [Look up words]. When `more` is null, the second line reads
  "Sign in with Google for more translations today. It's free."
- **Daily limit:** "Daily limit reached · Resets at midnight" / "The dictionary still works for every word." /
  [Upgrade to Pro] [Look up words].
- **Offline:** "You're offline" / "Not counted. Dictionary still works." / [Retry].
- **Busy:** "Translation is busy right now" / "Not counted. Try again in a moment." / [Retry].
- **Disabled:** "Translation is paused" / "The dictionary still works."
- **Unexpected:** "Something went wrong" / "Not counted." / [Retry].
- **Result:** "From cache · not counted"; "Machine translation · Google" / "Machine translation · Microsoft".
- **Pro hint:** "Need longer text? Pro allows 500 characters."
- **Pill:** "7 of 10 left" / "Limit reached".
- **Today sheet:** "%1$d of %2$d used today" · "Resets at midnight" · [Sign in to get 10 a day] (guest, using
  `freeLimit`) or [Upgrade to Pro] (free).
- **Pro sheet:** "Translate without limits" / "The dictionary stays free." / the table (Per day 10 · Unlimited;
  Characters 100 · 500; Dictionary Full · Full) / a disabled "Coming soon" / "Pro is on its way. You'll see it here
  first."
- **Labels:** Recent, Clear, Try, Translate, Translating….

Test tags:
- screen and controls: `translate_screen`, `translate_pill`, `translate_swap`, `translate_input`,
  `translate_counter`, `translate_hint`, `translate_switch_direction`, `translate_button`, `translate_skeleton`;
- result: `translate_result`, `translate_word_<index>`, `translate_copy`, `translate_share`, `translate_save`;
- blocks: `translate_block_guest_limit`, `translate_block_daily_limit`, `translate_block_offline`,
  `translate_block_busy`, `translate_block_disabled`, `translate_block_unexpected`;
- block buttons: `translate_sign_in`, `translate_upgrade`, `translate_look_up`, `translate_retry`;
- lists: `translate_sample_<index>`, `translate_recent_<id>`, `translate_clear_recents`, `translate_pro_hint`;
- sheets: `today_sheet`, `pro_sheet`, `pro_sheet_coming_soon`.

- [ ] **Step 1: Write the failing tests** (`TranslateScreenTest`, Robolectric Compose on `TranslateContent` with fixed
  states; `ProSheetTest`):
- `"idle shows samples, the disabled button and the counter 0 / 100"`.
- `"typing sends InputChanged"`; `"tapping translate sends Translate"`.
- `"loading shows Translating and the skeleton"`.
- `"done shows the result with attribution Google and cache note"`; `"tapping word 2 sends WordTap"`.
- One test per block: its tag shows, and its buttons send `onSignIn(TRANSLATE_LIMIT)`, `OpenPro(TRANSLATE_LIMIT)`,
  `LookUpWords` or `Retry`.
- `"guest card says 7 more"`, with the text containing "7 more today".
- `"a low pill is tagged translate_pill_low"`: when `pill.low` is set, the pill's tag is `translate_pill_low`
  instead of `translate_pill`. The amber colours follow from it.
- `"pro hint opens the pro sheet"` sends `OpenPro(TOO_LONG)`.
- `ProSheetTest`: `"coming soon is disabled"` (`assertIsNotEnabled`).

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*TranslateScreenTest' --tests '*ProSheetTest'` → FAIL.
- [ ] **Step 3: Implement** the composables and previews (`@PreviewLightDark` for idle, done, guest limit and
  offline, using `DictPreview` as the other screens do), the strings (en + bo), the tokens and the icons.
- [ ] **Step 4: Run them to verify they pass.** Same command → PASS, then the app gate.

---

### Task 11: Translate tab in the navigation, and the Open-in-Translate handoff

**Files:**
- Modify: `navigation/MainTab.kt`, `ui/components/Navigation.kt`, `ui/main/MainScaffold.kt`,
  `ui/main/MainViewModel.kt`, `navigation/AppNavHost.kt`, `ui/search/SearchScreen.kt` (the callback only),
  `res/values*/strings.xml`
- Test: `ui/components/BottomNavBarTest.kt`, `ui/main/MainViewModelTest.kt`, `ui/main/MainScaffoldTest.kt`,
  `navigation/AppNavHostTest.kt`

**Interfaces:**
- Consumes: `TranslateScreen` (Task 10), `TranslateRequest` (Task 9).
- Produces:
```kotlin
enum class MainTab { SEARCH, TRANSLATE, SAVED, LEARN, PROFILE }
// MainViewModel gains:
val translateRequest: StateFlow<TranslateRequest?>
fun openTranslate(request: TranslateRequest)   // stores it and selects TRANSLATE
fun consumeTranslateRequest()
// SearchScreen gains parameter onOpenTranslate: (TranslateRequest) -> Unit (Task 12 calls it)
// HomeScreen / SavedScreen gain onOpenTranslate parameters in Tasks 13/14; MainScaffold passes
//   { mainViewModel.selectTab(MainTab.TRANSLATE) } / { mainViewModel.openTranslate(it) }.
```

- `MainScaffold`: `MainTab.TRANSLATE -> TranslateScreen(onOpenSearch = { onOpenSearch(it, false) })`. The scaffold
  collects `viewModel.translateRequest`; when it isn't null on the Translate tab, it sends `TranslateEvent.Load` and
  consumes it.
- `AppNavHost`: the `Search` route's `onOpenTranslate = { req -> entry.ifResumed { mainViewModel.openTranslate(req); navController.popBackStack(Main, inclusive = false) } }`.
- `MainViewModel.translateRequest` is kept in `SavedStateHandle` as the pair key, text and optional result fields.
- Nav icon: `set.Translate`. Strings: `tab_translate` "Translate", `tab_translate_gloss` "ཡིག་སྒྱུར།" (en file).
  In the bo file: `tab_translate` "ཡིག་སྒྱུར།", `tab_translate_gloss` "Translate".
- `AppNavHostTest` adds `FirebaseModule` to `@UninstallModules`. It `@BindValue`s fakes for the interfaces other
  classes consume:
  - `AuthRepository`, `TranslationApi` and `Analytics`, from `FirebaseModule`;
  - `TranslationRepository`, `TranslationHistoryRepository` and `SavedTranslationsRepository`, because
    `RepositoryModule` is uninstalled.

  The Firebase-only seams (`CallableInvoker`, `AuthBackend`, `GoogleCredentialSource`) are needed only by the real
  implementations, so they need no fakes.

- [ ] **Step 1: Write the failing tests**
- `BottomNavBarTest`: `"the bar shows Search, Translate, Saved, Profile in order"`, the Learn feature being off.
- `MainViewModelTest`:
  - `"openTranslate selects the Translate tab and holds the request until consumed"`;
  - `"the request survives a recreated view model"`.
- `MainScaffoldTest`: `"the Translate tab shows the translate screen"` (the `translate_screen` tag).
- `AppNavHostTest`: `"back from Translate returns to Search"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*BottomNavBarTest' --tests '*MainViewModelTest' --tests '*MainScaffoldTest' --tests '*AppNavHostTest'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass, then the app gate** (including `MainActivitySmokeTest`).

---

### Task 12: Smart translate in search (1l–1r) and "In the dictionary" for phrases

**Files:**
- Create: `domain/PhraseSegmenter.kt`, `ui/search/SmartTranslate.kt` (the state model and controller)
- Modify: `data/repository/DictionaryRepository.kt` + `DictionaryRepositoryImpl.kt` (+ the DAO query it needs),
  `ui/search/SearchUiState.kt`, `SearchViewModel.kt`, `SearchContent.kt`, `SearchScreen.kt`,
  `fakes/FakeDictionaryRepository.kt`, `res/values*/strings.xml`
- Test: `domain/PhraseSegmenterTest.kt`, `data/repository/DictionaryRepositoryImplTest.kt` (existing),
  `ui/search/SmartTranslateTest.kt`, `ui/search/SearchViewModelTest.kt` (existing), `ui/search/SearchScreenTest.kt`
  (existing)

**Interfaces:**
- Consumes: `TranslationRepository`, `TranslationHistoryRepository`, `TranslationText`, `SignInAction`,
  `TranslateRequest`, `ProSheet` and `Analytics`.
- Produces:
```kotlin
// DictionaryRepository gains:
/** Entries whose headword is exactly one of [headwords] (with or without a trailing tsheg), keyed by the matched headword. */
suspend fun exactMatches(headwords: List<String>, directionId: String): Map<String, EntrySummary>
// domain/PhraseSegmenter.kt
object PhraseSegmenter {
    /** Every 1–4-syllable run (bo/dz) or every non-stop-word (en), deduplicated, in order. */
    fun candidates(normalized: String, source: Lang): List<String>
    /** Greedy longest match left to right over [known]; at most [max] results, in order, deduplicated. */
    fun segment(normalized: String, source: Lang, known: Set<String>, max: Int = 6): List<String>
}
// ui/search/SmartTranslate.kt
sealed interface SmartTranslateUi {
    val pill: UsagePillUi?
    /** Tap row; [instead] = the no-results "Translate instead" variant. */
    data class Offer(val query: String, val target: Lang, val instead: Boolean, override val pill: UsagePillUi?) : SmartTranslateUi
    data class Running(override val pill: UsagePillUi?) : SmartTranslateUi
    data class Result(val text: TranslatedText, val words: List<OutputWord>, override val pill: UsagePillUi?) : SmartTranslateUi
    data class TooLong(val length: Int, val maxChars: Int, override val pill: UsagePillUi?) : SmartTranslateUi
    data class Blocked(val plan: Plan, override val pill: UsagePillUi?) : SmartTranslateUi
    data class Failed(val offline: Boolean, override val pill: UsagePillUi?) : SmartTranslateUi
}
class SmartTranslateController(
    scope: CoroutineScope, translation: TranslationRepository, history: TranslationHistoryRepository,
    prefs: UserPreferencesRepository, dictionary: DictionaryRepository,
) {
    val state: StateFlow<SmartTranslateUi?>
    val phraseWords: StateFlow<List<EntrySummary>>
    /** Called by SearchViewModel with every settled outcome (null when the query is empty). */
    fun onSettled(settled: Settled?)
    fun translateNow()
    fun requestFor(): TranslateRequest?
}
data class Settled(val query: String, val direction: Direction, val script: InputScript, val hasExact: Boolean, val noResults: Boolean)
fun Direction.translationPair(): LangPair?   // in data/translation/TranslationModels.kt; null when source == target or a code isn't bo/dz/en
// SearchUiState gains: smartTranslate: SmartTranslateUi? = null, phraseWords: List<SearchResultUi> = emptyList()
// SearchEvent gains: TranslateQuery, OpenInTranslate, SmartWordTap(word: OutputWord), SmartSignIn, SmartUpgrade, DismissProSheet
// SearchEffect gains: OpenTranslate(request: TranslateRequest); SearchUiState gains showProSheet: Boolean
```

Controller rules, from the spec's smart translate table. The order matters:
1. No UI (state null) when any of these holds:
   - `direction.translationPair() == null` (1p);
   - `script == InputScript.WYLIE`;
   - `availability?.enabled == false`;
   - the pair isn't in `availability.pairs` (when known);
   - the query has fewer than 2 code points;
   - `TranslationText.check != OK`.
2. Quota `remaining == 0` → `Blocked(plan)`.
3. Normalised length over maxChars (default 100) → `TooLong`.
4. **Auto rule:** `isPhrase`, `!hasExact`, `prefs.smartTranslate`, a known quota with `remaining > 3`, and not
   already auto-translated for (query, pair). Then wait `AUTO_DELAY_MS = 1_000`, cancelled by the next `onSettled`.
   Then `Running` → `translate(…, SEARCH)` → `Result`, `Blocked` or `Failed`. Record the (query, pair) in a set held
   by the controller.
5. Otherwise `Offer(instead = noResults)`.

A result is recorded in history as well. `phraseWords`: when `isPhrase`, run
`dictionary.exactMatches(PhraseSegmenter.candidates(...), direction.id)`, then
`segment(..., known = result.keys)`, mapped to the summaries.

`SearchViewModel` builds the controller with `viewModelScope` and calls `onSettled` from the existing outcome
pipeline. `SearchViewModel` gets `TranslationRepository`, `TranslationHistoryRepository`, `SignInAction` and
`Analytics` injected. Add `fun signIn(activity: Activity)` (source `SEARCH_LIMIT`).

UI in `SearchContent`:
- the smart-translate block sits above the results (1m, 1n) or under did-you-mean (1q);
- the result card has `Open in Translate` and the pill line "%1$d of %2$d left today";
- "From cache · not counted" on a cached result;
- the "In the dictionary" header with `phraseWords` rows uses the existing result row composable;
- tags: `search_translate_offer`, `search_translate_running`, `search_translate_result`,
  `search_translate_too_long`, `search_translate_blocked`, `search_translate_open`.

Offer and result copy, from the spec:
- "Translate “%1$s” → %2$s"; "Tap to translate → %1$s"; "Translate instead";
- "Too long for quick translate" / "%1$d / %2$d characters" / [Open in Translate];
- "Sign in to translate · 0 of %1$d left"; "Daily limit reached · Upgrade".
- The target language name in the UI language: Tibetan, Dzongkha or English, from the pack `language` string or
  "English".

- [ ] **Step 1: Write the failing tests**
- `PhraseSegmenterTest`:
  - `candidates("ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ།", BO)` contains `"ཁྱེད་རང"`, `"སྐུ་གཟུགས"` and `"ཁྱེད"`, and no candidate
    is longer than 4 syllables;
  - `segment(same, BO, known = setOf("ཁྱེད་རང", "ཁྱེད", "སྐུ་གཟུགས", "བདེ་པོ"))` ==
    `["ཁྱེད་རང", "སྐུ་གཟུགས", "བདེ་པོ"]` (longest wins);
  - `segment("May all beings be happy", EN, known = setOf("happy", "beings", "may"))` == `["beings", "happy"]` (the
    stop-words `may`, `all` and `be` are skipped);
  - the cap is 6.
- `DictionaryRepositoryImplTest`: `"exactMatches finds headwords with and without a trailing tsheg"`, using the
  existing in-memory dictionary sample data.
- `SmartTranslateTest` (controller on `TestScope`, with fakes):
  - `"a Tibetan phrase auto-translates after 1 s"`: `onSettled(phrase)`; `advanceTimeBy(999)` makes no call;
    `advanceTimeBy(2)` makes 1 call; the state is `Result`.
  - `"typing on cancels the pending auto"`: a second `onSettled` before 1 s means one call, for the second query
    only.
  - `"the same phrase is auto-translated once"`: settle A, B, A gives 2 calls.
  - `"3 or fewer left gives a tap offer, no call"` (`remaining = 3`).
  - `"guests never auto-translate"` (quota GUEST 0/3 used: `remaining == 3` → Offer).
  - `"unknown quota gives an offer"`.
  - `"setting off gives an offer"`.
  - `"exact match gives an offer, even for a phrase"`.
  - `"no results gives the instead offer"`.
  - `"over maxChars is TooLong"`; `"limit reached is Blocked with the plan"`.
  - `"Wylie, monolingual and disabled show nothing"`.
  - `"translateNow on an offer translates once"`.
  - `"requestFor carries the pair, text and result"`.
- `SearchViewModelTest`: `"OpenInTranslate emits OpenTranslate with the request"`;
  `"SmartUpgrade logs pro_interest search_limit and shows the pro sheet"`.
- `SearchScreenTest`: the offer row shows "7 of 10 left today" and its tap sends `TranslateQuery`; the result shows
  "From cache · not counted" when cached.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*PhraseSegmenterTest' --tests '*DictionaryRepositoryImplTest' --tests '*SmartTranslateTest' --tests '*SearchViewModelTest' --tests '*SearchScreenTest'` → FAIL.
- [ ] **Step 3: Implement.** The `SearchScreen` route collects `OpenTranslate` and calls `onOpenTranslate` (Task 11).
  It shows `ProSheet` when `showProSheet`, and handles `SmartSignIn` through `viewModel.signIn(activity)`.
- [ ] **Step 4: Run them to verify they pass, then the app gate.**

---

### Task 13: Home translate card (1h) and the search placeholder

**Files:**
- Modify: `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `ui/main/MainScaffold.kt` (pass
  `onOpenTranslate`), `res/values*/strings.xml` (`search_placeholder`)
- Test: `ui/home/HomeViewModelTest.kt`, `ui/home/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `TranslationRepository` (quota, availability, refresh), `AuthRepository` (account).
- Produces:
```kotlin
data class TranslateCardUi(val pair: LangPair, val remaining: Int?, val limit: Int?)
// HomeUiState gains: translateCard: TranslateCardUi? = null
// HomeEvent gains: OpenTranslate ; HomeEffect gains: NavigateTranslate
// HomeScreen gains parameter onOpenTranslate: () -> Unit
```

Rules:
- The card is null when `availability?.enabled == false`.
- `remaining` and `limit` are null while `auth.account` is null.
- The pair is the pack's first translation pair, and the label is "%1$s ⇄ English" from the pack's language string.
- `HomeEvent.Shown` also calls `translation.refresh()`.

Copy: "Translate a sentence" / "Tibetan ⇄ English · 7 left today" (`%1$s ⇄ English · %2$d left today`, and without
the count when there is no user). The `search_placeholder` string becomes "Search or translate · ཚིག་འཚོལ།", and the
bo file is updated to match. Tag: `home_translate_card`. The card sits under the word of the day, styled as 1h line
308 (a bordered row with the icon tile).

- [ ] **Step 1: Write the failing tests**
- `HomeViewModelTest`:
  - `"translate card shows remaining once a user exists"` (account set, quota 3/10 → `TranslateCardUi(bo-en, 7, 10)`);
  - `"no user: card without count"`;
  - `"disabled: no card"`;
  - `"Shown refreshes translation"`;
  - `"OpenTranslate navigates"`.
- `HomeScreenTest`: `"tapping the translate card sends OpenTranslate"`; `"the card shows 7 left today"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*HomeViewModelTest' --tests '*HomeScreenTest'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass**, then run the existing `SearchFieldTest` (the placeholder text may be
  asserted there).

---

### Task 14: Saved › Translations (1y)

**Files:**
- Modify: `ui/saved/SavedUiState.kt`, `SavedViewModel.kt`, `SavedScreen.kt`, `SavedSections.kt`,
  `ui/components/Settings.kt` (add `SegmentedControl2` if no two-option control exists; it mirrors
  `SegmentedControl3`), `ui/main/MainScaffold.kt` (pass `onOpenTranslate`), `res/values*/strings.xml`
- Test: `ui/saved/SavedViewModelTest.kt`, `ui/saved/SavedScreenTest.kt`

**Interfaces:**
- Consumes: `SavedTranslationsRepository`, `SavedGrouping.groupBy` (Task 8), `TranslateRequest` (Task 9).
- Produces:
```kotlin
enum class SavedTab { WORDS, TRANSLATIONS }
data class SavedTranslationUi(val id: Long, val source: String, val translation: String, val pair: LangPair, val sourceIsTibetan: Boolean)
data class SavedTranslationGroupUi(val period: SavedPeriod, val items: List<SavedTranslationUi>)
// SavedUiState gains: tab: SavedTab = WORDS, translationCount: Int = 0, translationGroups: List<SavedTranslationGroupUi> = emptyList()
// SavedEvent gains: SelectTab(tab), OpenTranslation(id), RemoveTranslation(id), UndoRemoveTranslation(record: TranslationRecord)
// SavedEffect gains: OpenTranslate(request: TranslateRequest), TranslationRemoved(record: TranslationRecord)
// SavedScreen gains parameter onOpenTranslate: (TranslateRequest) -> Unit
```

Rules:
- The switch reads "Words · %1$d" | "Translations · %2$d".
- `isEmpty` and `showSearch` keep their meaning for the Words tab.
- The Translations tab has its own empty state: "Saved translations appear here" / "Tap ♡ on a translation to keep
  it."
- A row shows the source in up to two lines (Tibetan font when Tibetan), the translation muted, and
  "%1$s → %2$s · %3$s" (pack short labels such as བོད / EN, then the period label).
- Removing shows a toast with Undo, the same as the words toast pattern. `OpenTranslation` emits
  `OpenTranslate(TranslateRequest(pair, source, TranslatedText(translation, cached = true, provider)))`.
- Folders and the search field show only on the Words tab.
- Tags: `saved_tab_words`, `saved_tab_translations`, `saved_translation_<id>`, `saved_translations_empty`.

- [ ] **Step 1: Write the failing tests**
- `SavedViewModelTest`:
  - `"translation count and groups come from the saved translations"`;
  - `"remove then undo restores the row"`;
  - `"open emits the translate request with the saved result"`;
  - `"the words tab is unchanged"` (an existing assertion still holds with tab WORDS).
- `SavedScreenTest`:
  - `"the switch shows both counts and selects translations"`;
  - `"the translations empty state"`;
  - `"tapping a row sends OpenTranslation"`.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*SavedViewModelTest' --tests '*SavedScreenTest'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass.**

---

### Task 15: Profile account, plan card, smart translate switch, account deletion; feature flags

**Files:**
- Create: `data/auth/DeleteAccount.kt`
- Modify: `Features.kt` (`ACCOUNTS = true`; new `ONBOARDING_SIGN_IN = false`), `ui/onboarding/OnboardingUiState.kt`
  and `OnboardingScreen.kt` (use `ONBOARDING_SIGN_IN` where they read `ACCOUNTS`), `ui/profile/ProfileUiState.kt`,
  `ProfileViewModel.kt`, `ProfileScreen.kt`, `ProfileSheets.kt`, `ui/model/UiModels.kt` (`AccountUi`),
  `ui/components/Cards.kt` (`AccountCard` shows the name), `res/values*/strings.xml`
- Test: `data/auth/DeleteAccountTest.kt`, `ui/profile/ProfileViewModelTest.kt`, `ui/profile/ProfileScreenTest.kt`,
  the existing onboarding tests (step count 3 unchanged), `ui/components/CardsTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `TranslationRepository`, `TranslationApi.deleteAccount`, `SignInAction`, `ProSheet`,
  `Analytics`, `UserPreferencesRepository.setSmartTranslate`.
- Produces:
```kotlin
class DeleteAccount @Inject constructor(api: TranslationApi, auth: AuthRepository, analytics: Analytics) {
    /** Server delete, then local sign-out; true on success. On failure the user stays signed in. */
    suspend operator fun invoke(): Boolean
}
data class AccountUi(val name: String?, val email: String, val initials: String)   // syncedText removed
sealed interface PlanCardUi {
    data object NoUser : PlanCardUi
    data class Guest(val used: Int, val limit: Int) : PlanCardUi
    data class Free(val used: Int, val limit: Int, val maxChars: Int) : PlanCardUi
}
// ProfileUiState gains: plan: PlanCardUi = NoUser, smartTranslate: Boolean = true, confirmDelete: Boolean = false, showProSheet: Boolean = false
// ProfileSheet gains ACCOUNT
// ProfileEvent gains: OpenAccount (exists), SignOut, AskDeleteAccount, ConfirmDeleteAccount, DismissDelete, ToggleSmartTranslate, OpenPro, DismissProSheet
// ProfileViewModel gains fun signIn(activity: Activity, source: PromptSource)
```

Rules (spec, Profile section):
- **Account card:** `AccountCard` when the account is non-guest. Otherwise `SignedOutAccountCard` with title
  `profile_signed_out_title` "Not signed in" and body `profile_signed_out_body` "Sign in to get 10 free translations a
  day". Its button signs in with `PROFILE_ACCOUNT`.
- **Account sheet:** [Sign out] (toast "Signed out") and [Delete account].
- **Delete confirm dialog:** "Delete your account?" / "Your usage history is deleted. Saved words and translations stay
  on this phone." / [Delete] [Cancel].
  - Success: the toast "Account deleted" and `AccountDeleted` logged.
  - Failure: the toast "Couldn't delete your account. Try again."
- **Plan card:**
  - Free: "Free plan" + "Upgrade" (OpenPro `PROFILE_PLAN`) and "%1$d of %2$d translations used today · %3$d characters
    each".
  - Guest: "Guest" and "%1$d of %2$d used today" + [Sign in for 10] (sign-in `PROFILE_PLAN`; the label uses
    `freeLimit` when known, otherwise "Sign in for more").
  - NoUser: "Translate sentences free every day. Sign in for more."
- **Search group:** "Search" with the switch "Smart translate in search" / "Auto-translate phrases with no dictionary
  match".
- Tags: `profile_account_card`, `profile_plan_card`, `profile_plan_upgrade`, `profile_plan_sign_in`,
  `profile_row_smart_translate`, `profile_sign_out`, `profile_delete_account`, `profile_delete_confirm`.
- `ProfileEvent.Shown` also calls `translation.refresh()`.
- Rewrite `profile_signed_out_body` to the new copy above, in both languages: its meaning changes from sync to
  translations. The `onboarding_account_*` strings stay as they are, behind `ONBOARDING_SIGN_IN = false`.

- [ ] **Step 1: Write the failing tests**
- `DeleteAccountTest`:
  - `"success deletes on the server then signs out and logs"`: call order api → signOut; returns true; analytics
    `AccountDeleted`;
  - `"a server failure keeps the user signed in"`: returns false; signOut count 0.
- `ProfileViewModelTest`:
  - `"a google account shows the account card"`, `"a guest shows the plan card Guest 1 of 3"`, `"no user shows NoUser"`;
  - `"toggle smart translate persists"`;
  - `"confirm delete calls DeleteAccount and toasts"`; `"sign out toasts"`;
  - `"OpenPro logs profile_plan and shows the sheet"`.
- `ProfileScreenTest`:
  - `"signed out card button sends sign-in"`;
  - `"plan card shows 3 of 10 translations used today · 100 characters each"`;
  - `"delete asks for confirmation"`;
  - `"smart translate switch toggles"`.
- Onboarding: the existing step-count test still expects 3.

- [ ] **Step 2: Run them to verify they fail.** Run: `./gradlew testDebugUnitTest --tests '*DeleteAccountTest' --tests '*ProfileViewModelTest' --tests '*ProfileScreenTest' --tests '*Onboarding*' --tests '*CardsTest'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass, then the app gate.**

---

### Task 16: Launch checklist, Tibetan strings for review, final gates

**Files:**
- Create: `docs/translation-launch-checklist.md`
- Modify: `README.md` (repo root: one line linking the checklist under the backend or release section), and
  `docs/adding-a-dictionary-pack.md` (a new pack needs `translateSamples` and a `translationPairs` entry)

**Interfaces:** none (docs and verification).

- [ ] **Step 1: Write `docs/translation-launch-checklist.md`** with these sections, using exact commands and console
  paths:
  1. **Firebase Auth.** Enable Google. Add the SHA-1/SHA-256 of the debug key (`./gradlew signingReport`), the upload
     key and the Play app-signing key (Play Console → Setup → App signing). Re-download `app/google-services.json`.
  2. **App Check.** Register Play Integrity with the app-signing SHA-256. Add a debug token: run a debug build, copy
     the `DebugAppCheckProvider` token from logcat, then Console → App Check → Manage debug tokens.
  3. **IAM.** A short script for the owner to run with `!`:
     `gcloud projects add-iam-policy-binding englishtibetandictionary --member serviceAccount:translation-fn@englishtibetandictionary.iam.gserviceaccount.com --role roles/firebaseauth.admin`.
  4. **Deploy** (owner's go): `cd backend && firebase deploy --only functions --project englishtibetandictionary`.
     Optionally add `guest` to `config/translation`, as a **map** whose values are **number** fields.
  5. **Device pass.** The spec's nine steps, as a checklist.
  6. **Privacy policy.** The spec's required contents; host it and set `Links.PRIVACY_URL`.
  7. **Account deletion web link** for Play Console → App content → Data deletion.
  8. **Data safety answers:**
     - collected: name, email, user IDs, app interactions;
     - shared with processors: user-entered text, for translation;
     - encrypted in transit: yes;
     - users can request deletion: yes.
  9. **New Tibetan strings for review.** A table of every key added in Tasks 9–15 with its en and bo values.
     Generate it with
     `git diff --unified=0 -- app/src/main/res/values-bo/ | grep '^+ *<string'` (or by comparing the files).
- [ ] **Step 2: Run the app gate plus the release build**
  Run: `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL, all tests
  pass, no lint errors.
- [ ] **Step 3: Run the backend gate.** Expected: every command exits 0.
- [ ] **Step 4: Scan for user text in logs**
  Run: `grep -rn "Log\.\|println" app/src/main/java | grep -v "^.*//"`. Expected: nothing new prints input or
  translations. Then `grep -rn "console\." backend/functions/src` → no matches.
- [ ] **Step 5: Report to the owner.** Say what's done, give the checklist path, and list the items that need them
  (console steps, IAM, deploy, privacy URL, the Tibetan review, the Dark amber values if guessed).
