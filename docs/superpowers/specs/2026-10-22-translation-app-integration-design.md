# Translation in the App: Design

**Status:** draft for owner review, 2026-10-22 · **Backend:** `docs/superpowers/specs/2026-10-22-translation-backend-design.md`
· **UI source:** `docs/tibetan-dictionary-ui-revamp/project/Translate & Dictionary.dc.html`

## Context

The translation backend is live in asia-south1. It has two callables, `translate` and `getTranslationStatus`, which
need Firebase Auth (anonymous allowed) and App Check. The free plan is 10 translations a day at 100 characters
each, reset at the user's local midnight. Cache hits and failures are not counted. The contract is in
`backend/README.md`.

The Android app (v3, Compose + M3) has **no translation code**: no Translate screen, and no Firebase Auth,
Functions or App Check SDKs. Its tabs are Home · Saved · Profile (Learn is hidden behind `Features.LEARN`). Some
sign-in UI is already built and hidden behind `Features.ACCOUNTS`: `AccountCard`, `SignedOutAccountCard`, the
onboarding `AccountStep`, and `ProfileEvent.SignIn`.

This phase ships translation in the app as the design draws it: 1b, 1h, 1l–1v, 1y, 1z and 1za. It stops at the free
limit. Payment and subscriptions are the next phase. The dictionary stays fully usable with no account and no
network.

**Owner decisions (2026-10-22):**
- **Gate:** a guest trial, then sign-in.
  - A silent anonymous user gets **3 translations a day**.
  - Signing in with Google gives **10 a day** and keeps the same uid, so today's usage carries over.
  - Pro is next phase.
- **Navigation:** add a Translate tab, giving Home · Translate · Saved · Profile. Learn stays hidden.
- **"Upgrade to Pro" before billing** is a **fake door**:
  - it opens the 1za comparison sheet, whose button is a disabled "Coming soon";
  - it logs a `pro_interest` Analytics event;
  - no prices are shown and there is no "Restore purchase".
- **Scope:**
  - the Translate tab, sign-in, quota, and account deletion;
  - smart translate in search;
  - saved translations, the recent translations list, the Home card, and Profile plan and usage.
- **Follow the design bundle.** Where it has no screen (sign-in, account, the guest limit), design one in the same
  visual language.
- **Translation input for bo/dz is Tibetan script only**, from the backend ruling. Wylie is not translated.
- Never commit or push unless asked. No AI attribution anywhere. Deploys and billable calls happen only on the
  owner's explicit go.

## Product rules

### The ladder

| State | How you get there | Daily limit | At the limit |
|---|---|---|---|
| **No user** | Fresh install, or after sign-out | n/a | n/a. A Firebase user is created only when the user first translates or opens the Translate tab. |
| **Guest** | Anonymous Firebase user, created silently | 3 (`config/translation.guest`) | Sign-in card: "Sign in with Google to get 7 more today" |
| **Free** | Google sign-in (linked, same uid) | 10 (`config/translation.free`) | Limit card + Pro fake door |
| **Pro** | Next phase | n/a | n/a |

The order follows the hook model: trigger, action, variable reward, investment. Triggers are "N left today" on
Home, a search with no match, and a pasted sentence. The action is one tap, or none when smart translate runs on
its own. The reward is the translation, whose words link into the dictionary. The investments are saving a
translation and signing in.

Sign-in is asked for only **after** the user has had translations, and never before the first one. The dictionary
never asks for an account.

### Counting

The server decides, and the app only displays what it is told.
- Every `translate` reply carries `quota`, and every `daily_limit` refusal carries `limit`, `resetsAt` and (new)
  `plan` and `freeLimit`. Status also carries `freeLimit`, so "N more when you sign in" is never hard-coded.
- A cache hit is free and keeps the shown count.
- A failure is free; the UI says "Not counted".
- The app counts characters exactly as the server does (see `TranslationText`), so the counter never shows text as
  fitting when the server would refuse it.

## Screens

Colours, type and spacing come from `DictTheme`. The design's amber "low" state (#FBE9C8 / #7A4E00 light, counter
#8A5A00) has no token yet. Add `warnContainer`, `onWarnContainer` and `warn` to `DictColors` (Light and Dark).
The Dark values are taken from the dark mockups (1q, 1r, 1v).

### Translate tab (1b, 1s, 1t, 1u, 1v)

`MainTab.TRANSLATE` is second in the bar, with the design's two-arrow icon (`M4 8h13l-3-3M20 16H7l3 3`).
- **Header.** "Translate" with the **usage pill**:
  - it reads "7 of 10 left", turns amber at ≤3 left, and reads "Limit reached" at 0;
  - with no user yet it is hidden;
  - tapping it opens the **Today sheet** (improvised): used / limit, "Resets at midnight", and for a guest "Sign in
    to get 10 a day" or for a free user "Upgrade to Pro".
- **Language bar.** The pack's language ⇄ English (bo pack: Tibetan ⇄ English; dz pack: Dzongkha ⇄ English), with
  a swap button that turns 180°.
  - Swapping with a result moves the result into the input, as the prototype does.
  - Only directions in the status reply's `pairs` are offered. If the swapped direction is closed, the swap button
    is disabled.
- **Input card.**
  - The text field uses the Tibetan font and size for bo/dz input and the Latin ones for English.
  - The placeholder is "ཡི་གེ་འདིར་འབྲི། · Type in Tibetan" or "Type English text".
  - It has Paste and Clear, and the counter `n / maxChars`: muted, then amber from 85%, then red with a red ring at
    or over the limit.
  - Typing is not cut off at the limit. Over the limit, the Translate button is disabled and the counter says why.
- **Inline checks before any call.**
  - With a bo/dz source and no Tibetan letters: "Type in Tibetan script — Wylie isn't translated".
  - With an English source holding only Tibetan: "This looks like Tibetan" plus a one-tap [Switch].
- **Translate button.** "Translate", or "Translating…" while the call runs, with a three-line pulsing skeleton
  below. It is disabled with empty input, over the limit, or while loading.
- **Result card.**
  - The target language label, and "From cache · not counted" on a cache hit.
  - The output is in the target's font, and each word is tappable:
    - English output: words of 4 or more letters, dotted underline;
    - Tibetan output: tsheg-separated words;
    - a tap opens `Search(query = word)`.
  - Actions: Copy (toast "Copied"), Share (source + translation, as plain text), and Save (the heart fills; toast
    "Saved to Translations" or "Removed from Saved").
  - Attribution on the right: "Machine translation · Google" or "Machine translation · Microsoft", from `provider`.
- **Idle.** With empty input, "Try" sample chips for the direction (the prototype's samples).
- **Recent.** The last translations for this pack, newest first, with "Clear".
  - Tapping one restores its source and result with no call.
  - Making the same translation again moves it to the top.
- **Refusal states,** by `details.reason`:

| Reason / condition | UI |
|---|---|
| `daily_limit`, plan `guest` | **Sign-in card** (improvised, same frame as 1t): "You've used today's 3 free translations" / "Sign in with Google to get 7 more today. It's free." / [Continue with Google] [Look up words] |
| `daily_limit`, plan `free` | 1t card: "Daily limit reached · Resets at midnight" / "The dictionary still works for every word." / [Upgrade to Pro] [Look up words]. Input stays editable. |
| no network (`UNAVAILABLE` with no reason, or an IO failure) | 1u card: "You're offline" / "Not counted. Dictionary still works." / [Retry] |
| `provider_unavailable`, `service_budget` | 1u frame: "Translation is busy right now" / "Not counted. Try again in a moment." / [Retry] |
| `rate_limited` | Toast "Too many at once. Try again in a few seconds." |
| `text_too_long` | Counter red, using the server's `maxChars` (it replaces the cached quota's) |
| `not_tibetan_script`, `wrong_script`, `empty_text` | The inline checks above (the server's answer is the fallback) |
| `translation_disabled` | Card "Translation is paused" / "The dictionary still works." Input disabled. Home card and smart translate hidden. |
| `unsupported_pair` | Refresh status, then drop the direction from the bar |
| `unauthenticated`, `internal`, App Check rejected | 1u frame: "Something went wrong" / "Not counted." / [Retry]. The error is recorded through `ErrorReporter`. |

- **1s.** At 85% or more of the limit on the free plan, a centred line "Need longer text? Pro allows 500
  characters." opens the Pro sheet.
- "Look up words" switches to Home and opens Search with the input text.

### Sign-in (improvised)

- **Entry points:**
  - the guest limit card;
  - the Today sheet;
  - the Profile account card;
  - the Profile plan card's "Sign in for 10".
- **Flow:** Credential Manager with `GetSignInWithGoogleOption` (the account picker for a button tap).
  - Cancelling changes nothing and shows nothing.
  - An error shows the toast "Couldn't sign in. Try again."
  - Success shows the toast "Signed in · 7 translations left today", from the refreshed status, or plain "Signed
    in" if that refresh fails.
  - After success, a translation blocked by the guest limit becomes possible; the user taps Translate again. There
    is no automatic retry, because a retry would spend quota.
- **Not in onboarding.** The `AccountStep` moves behind a new `Features.ONBOARDING_SIGN_IN = false`, and
  `Features.ACCOUNTS` turns **on** (the Profile account card).

### Pro fake door (1za)

- A modal sheet: "Translate without limits" / "The dictionary stays free."
- The table: Per day 10 / Unlimited · Characters 100 / 500 · Dictionary Full / Full.
- A disabled filled button "Coming soon", and the note "Pro is on its way. You'll see it here first."
- It opens only from the free limit card, the 1s line, the Today sheet and the Profile plan card. Each open logs
  `pro_interest{source}`.

### Smart translate in search (1l–1r)

It is part of the Search route (`ui/search`). It shows only when all of these hold:
- the selected direction is bilingual (source ≠ target; not 1p);
- translation is enabled and the pair is open;
- the query passes the script check;
- the settled query has at least 2 characters.

Wylie queries get no translate UI, which drops 1l's row.

| Condition | UI |
|---|---|
| Auto rule holds (below) | "Translation · Translating…" card, then the result card (1m, 1n) with "Open in Translate", and the pill text "6 of 10 left today" under it |
| Phrase, but ≤3 left, unknown quota, or the setting off | Tap row "Tap to translate → Tibetan · 2 of 10 left today" (1o) with a Translate button |
| Any other result list (single word, or exact match) | Tap row "Translate “q” → English · 7 of 10 left today" |
| No results | "Translate instead · 7 of 10 left today" under did-you-mean (1q) |
| Normalised length over `maxChars` | "Too long for quick translate · 132 / 100 characters · Open in Translate" (1o) |
| Cache hit | Note "From cache · not counted" (1r) |
| Guest limit, or free limit, reached | The tap row becomes "Sign in to translate · 0 of 3 left" or "Daily limit reached · Upgrade" and opens the matching sheet |

- **Auto rule.** Auto-translate runs once per settled query, 1 s after the last keystroke, only when all of these
  hold:
  - the query is a **phrase**: bo/dz with 3 or more syllables or ending in ། ; English with 3 or more words;
  - it has no exact dictionary match;
  - the "Smart translate in search" setting is on;
  - more than 3 translations are left, as known from the cached quota;
  - the length is within `maxChars`.
- Guests never auto-translate, because their quota is 3. That is deliberate: it keeps the trial for deliberate
  taps.
- **"In the dictionary"** sits under a phrase's translation. It lists exact headword matches for the phrase's
  words, at most 6, through a new `DictionaryRepository.exactMatches(tokens, directionId)`.
  - bo/dz: greedy longest match over up to 4 tsheg syllables.
  - English: whitespace words, with a small stop-word list skipped.
- **"Open in Translate"** switches to the Translate tab with the query, the direction and the result filled in,
  without a new call.
- Smart translate and the Translate tab share the same repository and recents.

### Home (1h)

A card under the word of the day: "Translate a sentence" / "Tibetan ⇄ English · 7 left today". It opens the
Translate tab.
- With no user yet, the count is left out.
- When translation is disabled, the card is hidden.
- The search field's placeholder becomes "Search or translate".

### Saved (1y)

- A two-segment switch at the top: "Words · 24" | "Translations · 6".
- The Translations list:
  - each row shows the source (two lines), the translation (muted) and "བོད → EN · Today";
  - rows are grouped by the existing `SavedGrouping` periods;
  - tapping a row opens it in the Translate tab;
  - removing one (the heart, or a long-press menu) shows the undo toast.
- The empty state: "Saved translations appear here" / "Tap ♡ on a translation to keep it."
- Folders apply to Words only.

### Profile (1z, adapted)

- **Account card,** top:
  - signed out or guest: `SignedOutAccountCard` reading "Not signed in" / "Sign in to get 10 free translations a
    day" / [Sign in];
  - signed in: `AccountCard` with name, email and avatar, which opens the **Account sheet**: [Sign out] and
    [Delete account].
- **Plan card:**
  - free: "Free plan · Upgrade" with "3 of 10 translations used today · 100 characters each";
  - guest: "Guest · 1 of 3 used today" with "Sign in for 10";
  - before any user: "Translate sentences free every day. Sign in for more." (no numbers, because there is no
    status yet).
- **Search group:** a "Smart translate in search" switch, "Auto-translate phrases with no dictionary match".
  Stored in `UserPrefs.smartTranslate`, default on.
- **Delete account:**
  - a confirm dialog: "Delete your account? Your usage history is deleted. Saved words and translations stay on
    this phone.";
  - then `deleteAccount`, sign out and clear the credential state;
  - toast "Account deleted".
  - If it fails: toast "Couldn't delete your account. Try again." and stay signed in.
- **Sign out:** the toast "Signed out". The next translation starts a new guest (a known limit).

## Backend changes (`backend/functions`)

1. **Guest plan.**
   - `Plan = "guest" | "free" | "pro"`.
   - `CallableLike.auth` gains `token?: { firebase?: { sign_in_provider?: string; identities?: Record<string,
     unknown> } }`.
   - A user is a guest when `sign_in_provider === "anonymous"` and `identities` is empty or missing. The second
     check means a token minted just before a link still counts as signed in once it carries the Google identity.
   - `PlanResolver` becomes `(user: { uid: string; isGuest: boolean }) => Promise<Plan>`. It returns `"guest"` for
     guests and `"free"` otherwise; Pro joins in the billing phase.
2. **Config.** `RuntimeConfig.guest: PlanLimits` defaults to `{ dailyLimit: 3, maxChars: 100 }`. It is parsed per
   field like `free` and `pro`, and `limitsFor` covers all three.
3. **`daily_limit` details** become `{ limit, resetsAt, plan, freeLimit }`. `getTranslationStatus` also returns
   `freeLimit`, the free plan's `dailyLimit`. Together they let the app say "Sign in to get 7 more today"
   (`freeLimit − used`) without hard-coding 10.
4. **`deleteAccount` callable.**
   - Options: region asia-south1, App Check enforced, the `translation-fn` service account, `maxInstances: 2`.
   - Request: no data. Response: `{ deleted: true }`.
   - It refuses guests with `failed-precondition` / `guest_account`, and refuses no auth with `unauthenticated`.
   - It deletes every `dailyUsage` doc whose id is in `[uid + "_", uid + "_")`, in batches, then calls
     `getAuth().deleteUser(uid)`. Deleting a user that is already gone counts as success.
   - Logs record the uid and the number of docs deleted.
   - `translations/{sha256}` holds no uid, so it stays.
   - The service account needs **`roles/firebaseauth.admin`**.
5. **Tests.**
   - Unit: guest detection, including the token shapes; guest limits; the config field; `plan` in the details;
     deleteAccount with fakes.
   - Emulator:
     - the pipeline as a guest, then the same uid as signed in, showing usage carried over (3 of 10);
     - deleteAccount removing only that uid's usage docs.
   - `callables.test.ts`: the new callable's refusals.
   - `index.test.ts`: the new endpoint's options.
   - Coverage thresholds stay the same or go up.
6. **README.** The contract (guest, `plan`, `deleteAccount`, `guest_account`) and the config (`guest`).
   - Known limits: signing out starts a new guest with its own 3; a reinstall as a guest does too.
   - Runbook: the IAM role, the Google sign-in provider, and SHA fingerprints.

Deploying and granting the IAM role happen only on the owner's go.

## App architecture

The new code follows the existing patterns:
- interfaces with `*Impl`, bound with `@Binds @Singleton` in `core/di/RepositoryModule.kt`;
- ViewModels exposing `UiState` + `Event` + `Effect`;
- fakes in `app/src/test/.../fakes/`;
- `ErrorReporter` for errors that are swallowed;
- `Features` flags for whole features.

**No Firebase type crosses a repository interface,** so Robolectric and ViewModel tests never touch Firebase.

### Units

| Unit | Responsibility |
|---|---|
| `data/auth/AuthRepository` | `account: StateFlow<Account?>`, where `Account(uid, isGuest, name, email, photoUrl)`. `suspend ensureUser()` signs in anonymously if there is no user. `suspend signInWithGoogle(activity): SignInResult` returns Success, Cancelled or Failed. `suspend signOut()`. `suspend deleteAccount(): Result<Unit>`. |
| `data/auth/FirebaseAuthRepository` | `FirebaseAuth` plus `GoogleCredentialSource` (Credential Manager, `GetSignInWithGoogleOption(default_web_client_id)`). It runs the link flow below. |
| `data/translation/TranslationApi` | `translate(text, source, target, timeZone)`, `status(timeZone)` and `deleteAccount()` over `Firebase.functions("asia-south1")`. It maps `FirebaseFunctionsException` (`code`, `details.reason` and the extra fields) and IO failures to `TranslateOutcome` = `Translated(translation, cached, provider, quota?)` or `Refused(TranslationRefusal)`. |
| `data/translation/TranslationRepository` | It calls `ensureUser()` before every call, and is the single source of `quota: StateFlow<QuotaState?>` and `status: StateFlow<TranslationStatus?>` (enabled, pairs). Quota is updated from every reply and refusal. `refreshIfStale()` re-reads status after 5 minutes, on app foreground, after sign-in and after `resetsAt`. Once `resetsAt` has passed it shows `used = 0` until the refresh lands. It does nothing when there is no user. |
| `domain/TranslationText` | It mirrors the server's `normalize()`: a lone surrogate becomes U+FFFD, then NFC, zero-width characters removed, space runs collapsed, lines trimmed, at most one blank line in a row, then trimmed. Also `length()` in code points, `scriptCheck(text, source)`, `isPhrase(text, source)`, and word tokens for the tappable output. |
| `data/local` | `UserDatabase` v2→v3 adds `recent_translations` and `saved_translations`. Both have `id`, `pack_id`, `source_lang`, `target_lang`, `source_text`, `translation`, `provider`, a timestamp, and a unique key on (pack, source, target, source_text). Recents are capped at 20 per pack, trimmed on insert. Migration in `UserDatabaseMigrations`; the schema is exported. |
| `data/repository/TranslationHistoryRepository`, `SavedTranslationsRepository` | Flows per active pack; record, remove, undo, clear. |
| `data/prefs` | `UserPrefs.smartTranslate` (default true). |
| `core/appcheck/AppCheckInstaller` | Installs `PlayIntegrityAppCheckProviderFactory` (in `src/release`) or `DebugAppCheckProviderFactory` (in `src/debug`). Run once from app start before any callable. |
| `core/analytics/Analytics` | It wraps Firebase Analytics, which stays off in debug as today. Events: `translate{outcome, cached, pair, plan, surface}`, `quota_limit_hit{plan, surface}`, `sign_in_prompt{source}`, `sign_in_success{linked}`, `pro_interest{source}`, `account_deleted`. **No event carries user text.** |
| `ui/translate/` | `TranslateScreen` (stateless content plus route), `TranslateViewModel`, `TranslateUiState` / `Event` / `Effect`, `SignInCard`, `TodaySheet`, `ProSheet`. |
| `ui/search`, `ui/home`, `ui/saved`, `ui/profile`, `ui/main`, `navigation` | The additions above. A pending "open in Translate" request (text, direction, optional result) goes to the Translate tab through the activity-scoped `MainViewModel`. |

### Google sign-in flow

1. Credential Manager returns a `GoogleIdTokenCredential`. `GoogleAuthProvider.getCredential(idToken, null)`
   makes the Firebase credential.
2. Current user anonymous: `linkWithCredential`. The uid stays, so today's guest usage counts toward the 10.
3. `FirebaseAuthUserCollisionException` (this Google account already has a uid from another install or device):
   `signInWithCredential`, which switches uid and leaves the guest's usage behind.
4. No current user: `signInWithCredential`.
5. Then `getIdToken(true)` so the next callable carries the Google identity, then `refreshIfStale(force = true)`.

### Dependencies (pinned in `gradle/libs.versions.toml`, on the Firebase BoM 34.19.0)

- Firebase: `firebase-auth`, `firebase-functions`, `firebase-appcheck-playintegrity`, and `firebase-appcheck-debug`
  (`debugImplementation` only).
- Credentials: `androidx.credentials:credentials`, `credentials-play-services-auth`, and
  `com.google.android.libraries.identity.googleid:googleid`.
- `kotlinx-coroutines-play-services` for `await()`.
- R8 keep rules only where a library needs them.

### Strings

Every new string goes in `values/strings.xml` and `values-bo/strings.xml`, with pack-specific text in
`strings_pack_*.xml`, as today. The Tibetan is drafted and listed for the owner's review, like the open "~10 keys"
item from the rewrite.

## Error handling

- Every callable failure maps to a `TranslationRefusal`, and nothing unmapped reaches the UI. `internal`, unknown
  codes and App Check rejections become `Unexpected` and are recorded via `ErrorReporter`.
- Failures are never counted by the server. The UI says "Not counted" in every failure state.
- Auth failures (anonymous sign-in, refreshing the token) count as offline or unexpected for that attempt. They
  never break the dictionary.
- A double tap is ignored while a call is in flight. Two identical calls at the same moment are counted twice by
  the server (a backend known limit), so the button is disabled while loading.

## Testing

- **Backend:** run format:check, lint, typecheck, `coverage`, `coverage:emulator` and build, all green.
- **App unit (Robolectric / JVM):**
  - `TranslationText` against the README's normalisation and count cases, including NFC growth (གྷ U+0F43,
    ཱི U+0F73), and the phrase and script rules;
  - `TranslationApi` mapping for every reason in the README's error table, plus offline;
  - `TranslationRepository` quota updates, staleness, the `resetsAt` rollover and the no-user path, with a fake API
    and a fake auth;
  - the `FirebaseAuthRepository` link, collision and cancel paths behind a fake credential source and a fake
    Firebase seam;
  - `TranslateViewModel` (Turbine): every state in the tables, swap, recents, save and undo, guest → sign-in →
    free, and the fake door's analytics;
  - `SearchViewModel` smart translate: the auto rule, the 1 s debounce, one auto call per settled query, the ≤3
    rule, too long, no results, monolingual, Wylie;
  - Home, Saved and Profile ViewModels for the new rows;
  - the Room v2→v3 migration (`MigrationTestHelper`) and the DAOs;
  - Compose UI tests for the Translate states, `SignInCard`, `ProSheet`, the Saved switch and the Profile cards,
    using the test tags of the existing convention;
  - `MainActivitySmokeTest` with the Translate tab present.
- **Gate:** `./gradlew testDebugUnitTest lintDebug assembleDebug` and the backend gate, both green.
- **Device pass** (debug build, a registered App Check debug token, against the live backend once it's deployed on
  the owner's go):
  1. a fresh install translates as a guest, and the pill counts 3 → 0;
  2. the 4th try shows the sign-in card; sign-in shows "7 left";
  3. the 11th try shows the Pro sheet;
  4. offline shows "Not counted";
  5. a repeat gives "From cache · not counted";
  6. smart translate auto-runs only above 3 left;
  7. reinstall and sign in: the same uid, and the count holds;
  8. delete the account: gone from Auth, and its usage docs gone;
  9. Logcat and Cloud Logging contain no user text.

## Owner tasks

- Firebase console:
  - enable the **Google** provider;
  - add SHA-1 and SHA-256 for the debug key, the upload key and the Play **app-signing** key;
  - re-download `app/google-services.json`, which supplies `default_web_client_id`.
- App Check:
  - register the Android app with Play Integrity, using the app-signing SHA-256;
  - add a debug token for the test device.
- IAM: grant `roles/firebaseauth.admin` to `translation-fn@englishtibetandictionary.iam.gserviceaccount.com`. I'll
  give you a short script to run with `!`.
- Deploy the backend from `backend/`. Optionally, set `guest` in `config/translation` (the default is 3/day).
- **Before release:**
  - host the privacy policy and set `Links.PRIVACY_URL`. It must cover: text sent to Microsoft and Google;
    translations cached for up to 180 days; Google account name, email and photo; usage counts kept for 7 days;
    and account deletion;
  - give an **account-deletion web link** for Play: a section on the privacy page, or the developer email with
    instructions;
  - update Play **Data safety**: name, email, user IDs, app interactions, and user text shared with processors for
    translation.

## Known limits (accepted)

- Signing out, or reinstalling as a guest, starts a new guest uid with its own 3 a day. Signed-in usage holds across
  reinstalls and devices. The global budget bounds abuse.
- Quota shown before the first call of the day is the last known value, corrected by the next reply.
- Saved and recent translations are local to the device (no sync in this phase), and a backup covers them like the
  rest of `user.db`.
- "Unlimited" on the Pro sheet is product copy. The server's fair-use cap (`pro.dailyLimit` 300) applies once Pro
  exists.

## Out of scope

Play Billing, purchase verification, entitlements, Pro enforcement, real prices, Restore purchase, cloud sync of
saved items, translation notifications, Wylie-to-Tibetan conversion, and the onboarding sign-in step.
