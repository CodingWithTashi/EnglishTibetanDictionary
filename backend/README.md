# Translation backend

Three Firebase callable functions behind the app's Translate tab, smart translate in search, and account
deletion:

| Function | Purpose |
|---|---|
| `translate` | Translates Tibetan or Dzongkha ⇄ English. Uses the cache first, enforces the daily limit, then calls Azure Translator or Google Cloud Translation. |
| `getTranslationStatus` | Today's usage, limits and the open directions, for Home ("7 left today") and Settings. |
| `deleteAccount` | Deletes a signed-in user's usage docs and Firebase Auth account, from Profile › Account. |

All run in **asia-south1** (Mumbai), next to the project's Firestore database. Design and decisions:
`docs/superpowers/specs/2026-10-22-translation-backend-design.md`.

```
app ── callable (Auth + App Check) ──▶ translate
                                       │ normalise, validate, rate limit
                                       ├─▶ cache: instance memory ─▶ Firestore translations/{sha256}
                                       │      hit → reply, not counted
                                       ├─▶ global budget (budget/{utcDay}/shards)
                                       ├─▶ reserve quota (transaction on dailyUsage/{uid}_{day})
                                       ├─▶ router: config/translation.routes[pair] in order
                                       │      Azure Translator v3 │ Google Cloud Translation v3
                                       │      failure → refund, `provider_unavailable`
                                       └─▶ write cache + budget, reply
```

## Calling it from Android

```kotlin
val functions = Firebase.functions("asia-south1")
val result = functions.getHttpsCallable("translate")
    .call(mapOf("text" to text, "source" to "bo", "target" to "en", "timeZone" to TimeZone.getDefault().id))
    .await()
```

The app must be signed in (Firebase Auth, anonymous or Google) and attach App Check (Play Integrity) before
calling. Without them the call fails with `unauthenticated`.

### Plans

| Plan | Who | Default limit |
|---|---|---|
| `guest` | Signed in anonymously, with no linked identity (the ID token's `firebase.sign_in_provider` is `anonymous` and `firebase.identities` is empty) | 3 a day, 100 characters (`config/translation.guest`) |
| `free` | Signed in with Google. Linking Google to the anonymous user keeps the uid, so today's usage carries over. | 10 a day, 100 characters (`free`) |
| `pro` | Not used until the billing phase | 300 a day, 500 characters (`pro`) |

### `translate`

Request: `{ text: string, source: "bo" | "dz" | "en", target: "bo" | "dz" | "en", timeZone?: string }`
(`timeZone` is an IANA id. An unknown or missing zone counts the day in UTC.)

Response:

```json
{
  "translation": "Tashi Delek",
  "source": "bo",
  "target": "en",
  "cached": false,
  "provider": "azure",
  "quota": { "plan": "free", "used": 1, "limit": 10, "remaining": 9, "resetsAt": "2026-10-23T18:00:00.000Z", "maxChars": 100 }
}
```

- `cached: true` means the result came from cache and was **not counted**. Failures are not counted either.
- `provider` is who translated it. Show machine-translation attribution as each provider requires: Google
  requires that users can see the text was machine translated by Google.
- `resetsAt` is the user's next local midnight.
- `quota` is missing only on a cached reply when the server couldn't read today's usage. A cached reply never
  changes the count, so keep showing the last quota you had.

### `getTranslationStatus`

Request: `{ timeZone?: string }` (or no data).
Response: `{ enabled, plan, used, limit, remaining, resetsAt, maxChars, pairs, freeLimit }`. `pairs` lists the
open directions, such as `["bo-en", "en-bo", "dz-en", "en-dz"]`. `freeLimit` is the free plan's daily limit, so
the app can tell a guest how many more translations signing in gives ("Sign in to get 7 more today").

### `deleteAccount`

Request: no data. Response: `{ deleted: true }`.
Deletes every `dailyUsage` doc of the caller, then the caller's Firebase Auth user. A user that is already gone
counts as deleted, so a retry after a partial failure succeeds. Guests are refused with `guest_account`: an
anonymous user has no account to delete, and signing out discards it. Saved words and translations live only on
the device, so the app keeps them.

### Errors

Every refusal is an `HttpsError` with `details.reason`:

| code | `details.reason` | extra `details` | app behaviour |
|---|---|---|---|
| `unauthenticated` | `unauthenticated` | | sign in anonymously first |
| `unauthenticated` | *(none)* | | App Check token missing or invalid; the framework rejects it before the handler runs |
| `failed-precondition` | `translation_disabled` | | translation is switched off; the dictionary still works |
| `failed-precondition` | `guest_account` | | `deleteAccount` from a guest; there is no account to delete |
| `invalid-argument` | `bad_request` | | malformed request (app bug) |
| `invalid-argument` | `unsupported_pair` | | direction not offered (see `pairs`) |
| `invalid-argument` | `empty_text` | | nothing to translate |
| `invalid-argument` | `not_tibetan_script` | | a Tibetan/Dzongkha source must be in Tibetan script; Wylie is not translated |
| `invalid-argument` | `wrong_script` | | English source contains only Tibetan |
| `invalid-argument` | `text_too_long` | `maxChars`, `length` | "132 / 100 characters" |
| `resource-exhausted` | `daily_limit` | `limit`, `resetsAt`, `plan`, `freeLimit` | guest: "Sign in to get 7 more today"; free: "Daily limit reached · Resets at midnight" |
| `resource-exhausted` | `rate_limited` | `retryAfterSec` | too many requests a minute |
| `resource-exhausted` | `service_budget` | | the service-wide daily budget is spent; try later |
| `unavailable` | `provider_unavailable` | | every provider failed; not counted, offer retry |
| `internal` | *(none)* | | unexpected server error, logged |

### Character counting

Limits count **Unicode code points of the normalised text**. Normalisation: a lone surrogate (broken UTF-16)
becomes U+FFFD, NFC, zero-width characters removed, runs of spaces collapsed, lines trimmed, at most one blank
line in a row, then trimmed. Kotlin: `text.codePointCount(0, text.length)` on text normalised the same way
(`Normalizer.normalize(text, Normalizer.Form.NFC)` first). Tibetan is all in the BMP, so for Tibetan text this
equals `String.length`.

**Count after NFC, not before.** NFC can make Tibetan text longer: precomposed letters such as གྷ (U+0F43)
and ཱི (U+0F73) decompose to two code points. A client counter that skips NFC can show "100 / 100" for text
the server measures as 104 and rejects with `text_too_long`.

## Providers and switching

Each direction has an ordered provider list in the Firestore doc **`config/translation`**. The router tries
them in order. A provider that is unconfigured, failing, or not supporting the language is skipped.
**Switching provider means editing the doc in the Firestore console.** It takes effect within a minute, with
no deploy.

Each provider has a circuit breaker per function instance. It opens after 5 failed attempts in a row (a retry
is an attempt) and stays open 30 s, then lets one trial call through. Only failures on the provider's side
count: timeouts, 5xx, network errors, rate limits and rejected credentials. An unsupported language or a
reply the router can't use (for example an empty translation) moves on to the next provider without counting,
so odd input from one user can't switch a provider off for everyone.

Default routes (checked 2026-10-22 with real calls via `npm run check-providers`):

| Direction | Default route | Notes |
|---|---|---|
| `bo-en`, `en-bo` | `azure`, `google` | Both translate Tibetan. Google accepts `bo` although its published language list omits it. Azure's English→Tibetan was cleaner in the check (Google stacked two vowels, བཀའ་དྲིུན་ཆེ), so Azure goes first. |
| `dz-en`, `en-dz` | `google` | Only Google handles Dzongkha (`dz`); Azure replies "unsupported language". |

`npm run check-providers` settles what each provider really handles. It makes one real call per provider
and direction and prints suggested routes.

- **Azure**: Translator Text v3.0 with a key in Secret Manager (`AZURE_TRANSLATOR_KEY`). The key value
  `unset` leaves Azure out of every route.
- **Google**: Cloud Translation v3 (NMT) using the function's service account. **No API key.** The
  `translate.googleapis.com` API is already enabled on the project.

## Runtime config: `config/translation`

Every field is optional and the defaults are in code (`src/config/runtimeConfig.ts`). Fields are checked one
by one: an invalid field is logged (`config: invalid "<field>"…`, at ERROR) and replaced by its default, and the
rest of the doc still applies. A route a doc sets replaces that direction's default route.

**Types matter.** A field added in the Firestore console is a string unless you pick another type. `enabled`
must be a *boolean*; the numbers must be *number* (whole numbers). If `enabled` is present but not a boolean
(for example the string `"true"`), translation is switched **off**, because the kill switch never fails open.

```json
{
  "enabled": true,
  "routes": { "bo-en": ["azure", "google"], "en-bo": ["azure", "google"], "dz-en": ["google"], "en-dz": ["google"] },
  "guest": { "dailyLimit": 3, "maxChars": 100 },
  "free": { "dailyLimit": 10, "maxChars": 100 },
  "pro": { "dailyLimit": 300, "maxChars": 500 },
  "cache": { "generation": 1, "ttlDays": 180 },
  "globalDailyCharBudget": 300000,
  "rateLimitPerMinute": 20
}
```

- `enabled: false` is the kill switch.
- `routes["en-dz"] = []` closes a direction.
- Bump `cache.generation` to invalidate every cached translation, for example after switching provider for
  quality.
- `globalDailyCharBudget` caps characters sent to providers per UTC day across all users. 300,000 is about
  $3/day on Azure ($10 per million) or $6/day on Google ($20 per million). `0` blocks every uncached
  translation.
- `guest` applies to anonymous users and `free` to everyone signed in with Google. `pro` is not used yet: no one
  is on it until the billing phase verifies Google Play subscriptions server-side (`src/service/plan.ts` is the
  seam).

## Data, retention and privacy

| Collection | Content | Deleted |
|---|---|---|
| `translations/{sha256}` | normalised source text, translation, provider, generation | TTL on `expireAt`: 180 days, renewed when a hit has under 30 days left |
| `dailyUsage/{uid}_{day}` | count and characters per user and local day | TTL, 7 days; at once by `deleteAccount` |
| `budget/{utcDay}/shards/{0-9}` | characters and requests per UTC day | TTL, 7 days |
| `config/translation` | runtime config | |
| `Message/{id}` | feedback from app versions before 3.0 | |

`firestore.rules` gives clients no access to anything except creating `Message` docs, which the old app
still does while signed out. The functions use the Admin SDK. The indexes file turns on the TTL policies and
exempts the large text fields from indexing.

**Don't turn on App Check enforcement for Cloud Firestore** (Firebase console → App Check → APIs). The
functions enforce App Check themselves. Firestore enforcement would block the old app's signed-out `Message`
writes, because app versions before 3.0 send no App Check token.

Before release, the privacy policy must say that text typed for translation is sent to Microsoft
(Azure) or Google and cached for up to 180 days. The cache is keyed by the text, not the user, so deleting an
account leaves cached translations in place: they name no user. Logs never contain user text: they record uid, direction,
character count, a 12-character key prefix, provider, outcome and latency.

## Known limits

Deliberate behaviour, pinned by tests so that a change is a decision, not an accident:

- **A double tap counts twice.** Two identical requests sent at the same moment both miss the cache, so both
  are translated and both are counted (`test/emulator/translatePipeline.test.ts`). A retry after a reply is a
  cache hit and is free.
- **The global budget is approximate.** Each instance re-reads the day's total every 30 s, so busy instances
  can overshoot it by about 30 s of traffic.
- **The rate limit is per instance.** A user spread over several instances gets more than
  `rateLimitPerMinute`; the daily quota still holds.
- **The day follows the device.** Changing the device's time zone can move the day key. The global budget bounds
  it.
- **A new guest starts fresh.** Signing out, or reinstalling as a guest, starts a new guest uid with its own 3 a
  day. Signed-in usage holds across reinstalls and devices. The global budget bounds both. The old guest stays
  in Firebase Auth, as does a guest whose sign-in switched to an account that already existed. Nothing deletes
  them unless automatic clean-up of anonymous users is on (`docs/translation-launch-checklist.md`, step 1).
- **Pro is not enforced** until the billing phase (`src/service/plan.ts`).

## Development

```bash
cd backend/functions
npm ci
npm run format        # Prettier
npm run lint          # ESLint, typescript-eslint strict + type-checked
npm run typecheck
npm test              # unit tests (Vitest)
npm run test:emulator # Firestore emulator: stores, the handler on real stores, the callables, rules (Java 21+)
npm run coverage            # unit tests with coverage and thresholds (src/ without src/store/)
npm run coverage:emulator   # emulator tests with coverage and thresholds (src/store/)
npm run build         # → lib/
```

CI runs the two coverage commands, so a change that drops coverage below the thresholds in
`vitest.config.ts` or `vitest.emulator.config.ts` fails the build. Reports land in `coverage/` (git-ignored).

The emulator tests run as project `demo-translation`, so they never touch the real project. CI runs all of
the above in the `translation-backend` job of `.github/workflows/main.yml`.

To run the functions locally, use `firebase emulators:start --only functions,firestore --project
demo-translation` from `backend/`. Put `AZURE_TRANSLATOR_KEY=unset` (or a real key) in
`functions/.secret.local`, which is git-ignored. App Check is enforced in the emulator too.

## Deploy runbook

Run each step only when you mean to. Deploying replaces the live Firestore rules (today anyone can read and
write everything).

1. **Service account** the functions run as:
   ```bash
   gcloud iam service-accounts create translation-fn --project englishtibetandictionary \
     --display-name "Translation functions"
   for role in roles/datastore.user roles/cloudtranslate.user roles/firebaseauth.admin; do
     gcloud projects add-iam-policy-binding englishtibetandictionary \
       --member serviceAccount:translation-fn@englishtibetandictionary.iam.gserviceaccount.com --role $role
   done
   ```
   `roles/firebaseauth.admin` is for `deleteAccount`. The account created before that function existed needs
   only that one grant.
2. **Azure key and parameters**:
   - `firebase functions:secrets:set AZURE_TRANSLATOR_KEY` (enter `unset` until you have the key).
   - In `functions/.env` (committed; no secrets in it), set `AZURE_TRANSLATOR_REGION=<region>` to the
     Translator resource's region, such as `centralindia`, or leave it empty for a global resource.
     `AZURE_TRANSLATOR_ENDPOINT` stays at `https://api.cognitive.microsofttranslator.com` unless the resource
     uses a custom endpoint.
   - Keep both lines in `.env` even when empty. `firebase deploy` prompts for any parameter without a value
     there, and fails in CI.
3. **Sign-in providers**: Firebase console → Authentication → Sign-in method:
   - Anonymous → Enable (guests).
   - Google → Enable. Then, under Project settings → Your apps → Android, add the SHA-1 and SHA-256 of the debug
     key, the upload key and the Play app-signing key (Play Console → Test and release → App signing).
   - Re-download `app/google-services.json` afterwards: it then carries the OAuth web client the app's Google
     sign-in needs (`default_web_client_id`).
4. **Deploy** from `backend/`:
   `firebase deploy --only functions,firestore:rules,firestore:indexes --project englishtibetandictionary`.
   A first deploy run with `--non-interactive` deploys the functions but exits with an error because it can't
   set the container-image cleanup policy. Set it once with
   `firebase functions:artifacts:setpolicy --location asia-south1 --days 1`. This was done 2026-10-22.
5. **Providers**: `AZURE_TRANSLATOR_KEY=… AZURE_TRANSLATOR_REGION=… npm run check-providers`, then put its
   suggested routes in `config/translation` if they differ from the defaults.
6. **Smoke test**: register an App Check debug token for the Android app (Firebase console → App Check →
   Apps → Manage debug tokens), then
   `FIREBASE_API_KEY=… FIREBASE_APP_ID=… APP_CHECK_DEBUG_TOKEN=… npm run smoke`.
   Expect every line to be PASS. The repeat call must show `cached: true` with `quota.used` unchanged.
7. **App Check for release**: register the Android app with Play Integrity, using the app-signing SHA-256
   from Play Console.

## Operations

- **Logs** (Cloud Logging, `jsonPayload`): `translate` events with `outcome` = `cached`, `translated` or
  `refused` (+ `reason`). `provider attempt failed` and `provider rejected our credentials` come from the
  router.
- **Suggested alerts** (log-based metrics):
  - any `provider rejected our credentials`: a bad or expired key;
  - a rising rate of `all providers failed`;
  - any `reason = service_budget`;
  - any `config: invalid`: a mistyped field in `config/translation` (if it was `enabled`, translation is off).
- Add a GCP billing budget alert as well. The in-app budget is approximate: each instance re-reads it every
  30 s.
- **Runtime**: Node.js 22, which Cloud Functions deprecates on 2027-04-30. Move `firebase.json` `runtime` and
  `package.json` `engines` to Node 24 before then.
