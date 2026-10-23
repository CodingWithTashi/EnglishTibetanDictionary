# Translation Backend: Design

**Status:** approved 2026-10-22 · **Plan:** `docs/superpowers/plans/2026-10-22-translation-backend.md`

## Context

The revamp design (`docs/tibetan-dictionary-ui-revamp/project/Translate & Dictionary.dc.html`) adds machine translation: a Translate tab plus "smart translate" in search, for both packs (Tibetan ⇄ English, Dzongkha ⇄ English). It fixes these product rules:
- Free plan: 10 translations a day, 100 characters each. The limit resets at midnight.
- Pro plan: 500 characters, unlimited translations.
- A result served **from cache is not counted**. A **failure is not counted**.
- The UI labels results as machine translation.

The owner wants the backend first: the app calls a function and gets the data back. It must switch easily between Azure and Google, cache results to save API cost, and be production ready and scalable. The frontend comes later.

**Facts verified on 2026-10-22 (live, read-only):**
- Firebase project `englishtibetandictionary`: Blaze billing is on. The `(default)` Firestore is **asia-south1**. `translate.googleapis.com` and App Check are already enabled. Functions are not deployed yet. Firebase Auth is not initialised (`CONFIGURATION_NOT_FOUND`).
- **Live Firestore rules are wide open:** `match /{document=**} { allow read, write: if request.auth == null; }`. Anyone can read and write the whole database. The only collection is `Message`, the feedback written by the old app on `master`, `{message: string}` with length > 3. A shared cache under these rules could be poisoned, so the rules must be locked down while keeping legacy `Message` creates.
- **Language support:**
  - Azure (`/languages`): Tibetan `bo` ✅, Dzongkha `dz` ❌.
  - Google Cloud Translation v3 and v2, queried for this project (196 and 195 languages): `dz` ✅, `bo` **not listed**. The consumer site translate.google.com does have Tibetan.
  - **Consequence:** routing is **per language pair**, set in a config doc with no hard-coding. T9's check script makes a real call per pair per provider to settle whether the Google API accepts `bo`. If it does, the owner adds `google` to the Tibetan routes with no redeploy.

**Owner decisions:**
- **Pro is deferred to the billing phase.** The server treats everyone as Free, and the plan resolver is the seam for later.
- Translation input for `bo`/`dz` is **Tibetan script only (no Wylie)**.
- Never commit or push unless asked. No AI attribution anywhere.
- Deploys and real (billable) API calls happen only with the owner's explicit go.

## Design

### API contract (Android calls `Firebase.functions("asia-south1")`)
`translate` request: `{ text: string, source: "bo"|"dz"|"en", target: "bo"|"dz"|"en", timeZone?: string /* IANA */ }`

Response:
```
{ translation, source, target, cached: boolean, provider: "azure"|"google",
  quota: { plan: "free"|"pro", used, limit, remaining, resetsAt /* ISO */, maxChars } }
```
- `provider` lets the app meet each provider's attribution rule (Google requires users be told the text is machine translated).
- **Both functions require Firebase Auth (anonymous on the client) and enforce App Check.**

`getTranslationStatus` request: `{ timeZone?: string }`. Response: `{ enabled, plan, used, limit, remaining, resetsAt, maxChars, pairs: string[] }`. It feeds "7 left today" on Home and "3 of 10 used" in Settings.

Errors are `HttpsError` with `details.reason`:

| code | reason | details |
|---|---|---|
| unauthenticated | (no auth / App Check) | |
| invalid-argument | `bad_request`, `unsupported_pair`, `empty_text`, `not_tibetan_script` (bo/dz source with no U+0F00–0FFF letter), `wrong_script` (en source with Tibetan only), `text_too_long` | `{ maxChars, length }` |
| resource-exhausted | `daily_limit` | `{ resetsAt, limit }` |
| resource-exhausted | `rate_limited` | `{ retryAfterSec }` |
| resource-exhausted | `service_budget` | |
| unavailable | `provider_unavailable` (not counted) | |
| failed-precondition | `translation_disabled` (kill switch) | |

**Character count:** Unicode code points of the normalised text. Kotlin: `codePointCount`. Tibetan is all BMP, so this matches `String.length`.

### Request pipeline (`translateHandler`)
1. Require `auth.uid`.
2. Load the runtime config (cached 60 s). If `!enabled`, reject with `translation_disabled`.
3. Validate the request with zod. Raw text is capped at 5,000 UTF-16 units before any processing.
4. The pair must be a key of `config.routes`, otherwise `unsupported_pair`.
5. `normalize()`: NFC; CRLF → LF; strip U+200B, U+200C, U+200D and U+FEFF; collapse runs of spaces and tabs; trim each line; at most one blank line in a row; trim. If the result is empty, `empty_text`. Run the script check.
6. `plan = resolvePlan(uid)`, which is always `"free"` for now. If `length > config[plan].maxChars`, `text_too_long`.
7. Per-uid rate limit: in-memory sliding window, `rateLimitPerMinute` (default 20), best-effort per instance. Paid calls are bounded by the transactional quota anyway.
8. Work out `dayKey`/`resetsAt` with luxon in the client's `timeZone`. An invalid zone falls back to UTC.
9. **Cache lookup** (L1 LRU, then L2 Firestore), in parallel with reading the usage doc. **A hit returns `cached: true`, uses no quota, and works even when the user is at the limit.**
10. Global budget check: sum of today's UTC shards, cached 30 s. If over `globalDailyCharBudget`, `service_budget`.
11. **Reserve** quota in a Firestore transaction on `dailyUsage/{uid}_{dayKey}`. If `count >= limit`, `daily_limit`. Otherwise `count+1, chars+n`.
12. Run the router for the pair. **On failure, refund** (`increment(-1)` and `-n`) and return `provider_unavailable`.
13. On success, write L2 and L1 and increment a random budget shard. A failed cache or budget write is logged, never fatal.
14. Log one structured event and return.

The cache gives natural idempotency: a client that retries after a success gets a cache hit, so it isn't charged twice.

### Providers and routing
- `TranslationProvider { id; isConfigured(): boolean; translate({text, from, to, signal}): Promise<string> }`
- **Azure:** REST `POST {endpoint}/translate?api-version=3.0&from&to&textType=plain` with headers `Ocp-Apim-Subscription-Key`, `Ocp-Apim-Subscription-Region` (when set) and `X-ClientTraceId`.
  - Configured only if the key is non-empty and not `unset`.
- **Google:** REST v3 `POST https://translation.googleapis.com/v3/projects/{GCLOUD_PROJECT}/locations/global:translateText` with `mimeType: "text/plain"`, which avoids HTML-escaped output.
  - Auth is **ADC through google-auth-library**: the function's service account, no API key. The API is already enabled.
- `ProviderError.kind`:
  - `unsupported_language`: Azure 400036/400019/400023; Google 400 mentioning language.
  - `auth`: 401/403, logged at ERROR.
  - `rate_limited`: 429, with Retry-After.
  - `transient`: 5xx, network, timeout.
  - `bad_response`: an unexpected shape or an **empty translation**, which is never cached.
- **Router:**
  - Walk `config.routes[pair]` in order.
  - Skip a provider that is unconfigured or whose breaker is open (5 consecutive failures opens it for 30 s; then half-open with one trial).
  - Per-attempt timeout is 8 s. One retry on `transient`/`rate_limited`, waiting jittered 250 ms or Retry-After, capped at 2 s.
  - Then fall through to the next provider. `unsupported_language` doesn't trip the breaker.
  - The overall deadline is 20 s.
- **Switching providers** means editing `config/translation.routes` in the Firestore console. It takes effect within 60 s, with no deploy.

### Runtime config `config/translation` (all optional; defaults in code; an invalid doc logs an error and falls back to defaults)
```
enabled: true
routes: { "bo-en": ["azure","google"], "en-bo": ["azure","google"], "dz-en": ["google"], "en-dz": ["google"] }   # Google = Tibetan fallback (owner: Google API supports bo; unlisted in its language list)
free: { dailyLimit: 10, maxChars: 100 }
pro:  { dailyLimit: 300, maxChars: 500 }   # fair-use cap; unused until billing phase
cache: { generation: 1, ttlDays: 180 }
globalDailyCharBudget: 300000             # ≈ $3/day Azure ($10/M), $6/day Google ($20/M)
rateLimitPerMinute: 20
```
Unknown provider ids in routes are dropped with a warning. Bumping `cache.generation` invalidates the whole cache, for example after switching provider for quality.

### Firestore data (Admin SDK only)
| Path | Fields | TTL |
|---|---|---|
| `translations/{sha256(gen␀src␀tgt␀text)}` | source, target, sourceText, translation, provider, generation, createdAt, expireAt | expireAt (+ttlDays). On a hit, extended only when under 30 days remain. |
| `dailyUsage/{uid}_{dayKey}` | uid, day, count, chars, updatedAt, expireAt | +7 days |
| `budget/{utcDay}/shards/{0..9}` | chars, requests, expireAt | +7 days |
| `config/translation` | see above | — |
| `Message/{id}` | legacy feedback, create-only from old app versions | — |

**Index exemptions** apply to `translations.{sourceText,translation,expireAt,createdAt}` and the `expireAt` fields. Large strings make wasted index writes, and TTL timestamps cause write hotspots.

**Rules:** deny everything except `Message` create, where `keys().hasOnly(['message'])` and `message` is a string of 4–5000 characters.

### Function options
- `translate`: `region asia-south1, enforceAppCheck true, cpu 1, memory 512MiB, concurrency 80, maxInstances 10, timeoutSeconds 30, secrets [AZURE_TRANSLATOR_KEY], serviceAccount translation-fn@…`.
- `getTranslationStatus`: the same, but `maxInstances 5` and no secrets.
- Params:
  - `AZURE_TRANSLATOR_KEY` (Secret Manager; a placeholder `unset` is allowed until the key arrives).
  - `AZURE_TRANSLATOR_REGION` (string, default empty; e.g. `centralindia`).
  - `AZURE_TRANSLATOR_ENDPOINT` (default `https://api.cognitive.microsofttranslator.com`).

### Security and privacy
- App Check, plus Auth, plus a transactional quota, plus a global budget, plus `maxInstances`, gives defence in depth against cost abuse.
- Logs never contain user text: they carry the pair, char count, key prefix (12 chars), provider, cache tier, latency, outcome and uid.
- **Release blockers for the client phase (documented, not done here):**
  - The privacy policy must say that entered text is sent to Microsoft/Google and cached for up to 180 days.
  - Enable anonymous Auth.
  - Register the Android app for App Check with Play Integrity (app-signing SHA-256).

### Known limits (accepted)
- Changing the device timezone can move the day key. Reinstalling gives a new anonymous uid. Both are bounded by the global budget.
- Pro is not enforced until the billing phase adds `verifyPurchase` + RTDN + `entitlements/{uid}` behind `resolvePlan`.
