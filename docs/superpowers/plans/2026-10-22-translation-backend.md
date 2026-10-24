# Translation Backend (Firebase Functions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (Native, recommended below) or superpowers:subagent-driven-development. Steps use `- [ ]` checkboxes.

**Goal:** Two production-ready Firebase callable functions (`translate`, `getTranslationStatus`). Each language pair is routed to Azure Translator or Google Cloud Translation through a config document, with a two-tier translation cache and server-enforced free quotas.

**Architecture:** TypeScript Cloud Functions v2 in `asia-south1`, next to the existing Firestore (`asia-south1`). A pure core (normalize → validate → cache → quota → provider router) sits behind thin `onCall` wrappers. Firestore holds the shared translation cache, per-user daily usage, a sharded global budget counter and a runtime config doc. Clients have no direct access to any of these.

**Tech stack:** Node 22, TypeScript (ESM/NodeNext), firebase-functions 7.x, firebase-admin 14.x, zod, luxon, lru-cache 11, google-auth-library, vitest, Firebase Emulator Suite, @firebase/rules-unit-testing. Use the latest versions at T1 and check them with `npm view`.

**Spec:** `docs/superpowers/specs/2026-10-22-translation-backend-design.md`

## Global Constraints
- All code lives under `backend/` (`backend/firebase.json`, `backend/functions/`). The Android project is untouched except for `.gitignore` and the CI workflow.
- Functions region is `asia-south1`, on the Node 22 runtime. Firestore is the existing `(default)` database.
- Tests run on emulators with project id **`demo-translation`**, never the real project.
- No user text in logs. No secrets in the repo. `.gitignore` covers `backend/functions/{node_modules,lib}/`, `*.local`, and `firebase-debug.log`, `firestore-debug.log` and `ui-debug.log`.
- Never commit or push unless the owner asks. A task ends at a green checkpoint and leaves the work uncommitted.
- `firebase deploy`, secret creation, IAM changes and real provider calls happen **only in T10 and only after the owner's explicit go**.

## Review Focus
1. **Concurrent requests from one user at the edge:** with 11 parallel misses at count 0 and limit 10, exactly 10 succeed and 1 gets `daily_limit`. Test in T6.
2. **Odd timezones:** `Asia/Thimphu`, `Etc/GMT+12`, `Not/AZone` (falls back to UTC), and the DST change day in `America/Toronto`. `dayKey` and `resetsAt` must be correct (next local midnight). Test in T2.
3. **Text edge cases:**
   - CRLF, U+200B inside Tibetan, leading and trailing spaces.
   - 100 Tibetan code points pass and 101 get `text_too_long`.
   - `"bkra shis"` with source `bo` gets `not_tibetan_script`.
   - Tibetan text with source `en` gets `wrong_script`.
   - Test in T2 and T7.
4. **Bad config doc:** wrong types, an unknown provider `"deepl"` in routes, or a missing doc. The function keeps serving with defaults. Test in T3.
5. **Partial failure after a paid call:** the provider succeeds but the cache write throws. The user still gets the translation, quota counts once, and an error is logged. A provider failure refunds. Test in T7.

---

## File Structure
```
backend/
  firebase.json            functions(source: functions, runtime nodejs22), firestore rules+indexes, emulators(firestore 8080)
  .firebaserc              default: englishtibetandictionary
  firestore.rules          lock-down + legacy Message create
  firestore.indexes.json   fieldOverrides: index exemptions + ttl
  README.md                contract, config, switching, runbook, costs, privacy, alerts
  functions/
    package.json tsconfig.json eslint.config.js vitest.config.ts vitest.emulator.config.ts .env (AZURE_TRANSLATOR_REGION=)
    src/index.ts                     onCall wiring only
    src/config/params.ts             defineSecret/defineString
    src/config/runtimeConfig.ts      zod schema, defaults, merge, 60 s cache
    src/domain/languages.ts          LangCode, PairKey, pairKey(), isTibetanLetter
    src/domain/normalize.ts          normalize(), codePointLength(), checkScript()
    src/domain/day.ts                dayWindow(tz, now)
    src/domain/errors.ts             TranslationError → HttpsError mapping
    src/providers/types.ts           TranslationProvider, ProviderError, ProviderId
    src/providers/azure.ts           createAzureProvider()
    src/providers/google.ts          createGoogleProvider()
    src/providers/router.ts          createRouter(), CircuitBreaker
    src/store/cacheKey.ts            cacheKey()
    src/store/translationCache.ts    createTranslationCache() (L1 lru + L2 Firestore)
    src/store/quotaStore.ts          createQuotaStore() reserve/refund/read
    src/store/budgetStore.ts         createBudgetStore() sharded add/isOver
    src/service/rateLimiter.ts       createRateLimiter()
    src/service/plan.ts              resolvePlan()
    src/service/translateHandler.ts  handleTranslate(), handleStatus()
    scripts/check-providers.ts       real one-sample call per pair × provider (manual, billable)
    scripts/smoke-callable.ts        anonymous sign-in + App Check debug token → call deployed functions
    test/unit/*.test.ts  test/emulator/*.test.ts
```

---

## Tasks

### Task 0: Record the approved design
- [ ] Copy this file's Context and Design to `docs/superpowers/specs/2026-10-22-translation-backend-design.md` and the Tasks to `docs/superpowers/plans/2026-10-22-translation-backend.md`. The repo's existing convention is the place for these. Don't commit.

### Task 1: Scaffold `backend/` with a green toolchain
**Files:** everything under `backend/` except `src/**` logic; root `.gitignore`.
- [ ] Check current versions (`npm view firebase-functions firebase-admin zod luxon lru-cache google-auth-library vitest typescript firebase-tools @firebase/rules-unit-testing version`). Pin exact versions. Make `firebase-tools` a devDependency so CI and local use the same version.
- [ ] Write the `package.json` scripts:
  - `build` (tsc → lib)
  - `lint` (eslint)
  - `typecheck` (tsc --noEmit)
  - `test` (vitest run test/unit)
  - `test:emulator` (`firebase emulators:exec --only firestore --project demo-translation "vitest run --config vitest.emulator.config.ts"`)
  - `check-providers` (tsx scripts/check-providers.ts)
  - `smoke` (tsx scripts/smoke-callable.ts)
- [ ] Set `engines.node "22"`, `main lib/index.js` and `type module`.
- [ ] Add `test/unit/sanity.test.ts` (`expect(1).toBe(1)`). Run `npm ci && npm run lint && npm run typecheck && npm test`. Expected: all pass.
- [ ] Run `java -version`. Expected: 21+ for the Firestore emulator. If it's lower, note it and use the Android Studio JBR.

### Task 2: Domain: languages, normalisation, script check, day window
**Produces:**
- `type LangCode = 'bo'|'dz'|'en'`, `pairKey(s,t): string` (`"bo-en"`)
- `normalize(text): string`, `codePointLength(s): number`
- `checkScript(text, source): 'ok'|'not_tibetan_script'|'wrong_script'`
- `dayWindow(timeZone: string|undefined, now: Date): { zone: string; dayKey: string; resetsAt: Date }`
- [ ] Failing tests, `test/unit/normalize.test.ts`:
  - `normalize("  a\r\n\r\n\r\nb\t\t c ") === "a\n\nb c"`
  - `normalize("བཀྲ​་ཤིས") === "བཀྲ་ཤིས"`
  - NFC: `normalize("é") === "é"`
  - `codePointLength("བཀྲ་ཤིས") === 7`
  - `checkScript("bkra shis","bo") === "not_tibetan_script"`
  - `checkScript("ང་ Tashi","bo") === "ok"`
  - `checkScript("བཀྲ་ཤིས","en") === "wrong_script"`
  - `checkScript("Hello 123","en") === "ok"`
- [ ] Failing tests, `test/unit/day.test.ts`:
  - `Asia/Thimphu` at `2026-10-22T19:00Z` gives dayKey `2026-10-23` and resetsAt `2026-10-23T18:00Z`
  - `Not/AZone` and `undefined` give zone `UTC`
  - `America/Toronto` on `2026-11-01T12:00Z` (DST ends) gives resetsAt `2026-11-02T05:00Z`
  - `Etc/GMT+12` is valid
- [ ] Implement with luxon `IANAZone.isValidZone` and `DateTime.setZone().startOf('day').plus({days:1})`. Run `npm test`. Expected: PASS.

### Task 3: Runtime config
**Produces:** `type RuntimeConfig` (the shape in Design), `DEFAULT_CONFIG`, `parseConfig(raw: unknown, log): RuntimeConfig`, `createConfigSource(db, {ttlMs=60000, now}) → { get(): Promise<RuntimeConfig> }`.
- [ ] Failing tests:
  - `parseConfig(undefined)` deep-equals `DEFAULT_CONFIG`.
  - A partial `{free:{dailyLimit:5}}` keeps `free.maxChars === 100`.
  - `{routes:{"bo-en":["deepl","azure"]}}` gives `["azure"]` plus a warning.
  - `{free:{dailyLimit:"ten"}}` gives the defaults plus an error logged.
  - `get()` reads Firestore once within 60 s and again after (fake clock and a fake doc reader).
  - A thrown read error returns the last good config, or defaults if there is none.
- [ ] Implement with zod (`.partial()` per section, then merge onto the defaults). Run `npm test`. Expected: PASS.

### Task 4: Providers (Azure, Google)
**Produces:**
- `type ProviderId = 'azure'|'google'`
- `class ProviderError extends Error { provider; kind: 'unsupported_language'|'auth'|'rate_limited'|'transient'|'bad_response'; status?; retryAfterMs? }`
- `createAzureProvider({ key, region, endpoint, fetch })`
- `createGoogleProvider({ projectId, getAccessToken: () => Promise<string>, fetch })`
- Both return `TranslationProvider`.
- [ ] Failing tests with an injected fake `fetch`:
  - Azure sends `[{"Text":"བཀྲ་ཤིས"}]` with `from=bo&to=en`, `Ocp-Apim-Subscription-Key` and `Ocp-Apim-Subscription-Region: centralindia` headers; returns `"Tashi Delek"` from `[{"translations":[{"text":"Tashi Delek","to":"en"}]}]`.
  - Azure region header is omitted when the region is empty.
  - Azure `isConfigured()` is false for `""` and `"unset"`.
  - Azure 401 → `auth`; 400 `{error:{code:400036}}` → `unsupported_language`; 429 with `Retry-After: 1` → `rate_limited` and `retryAfterMs 1000`; 503 → `transient`; `[]` or empty text → `bad_response`.
  - Google posts `{contents:[text], sourceLanguageCode:'dz', targetLanguageCode:'en', mimeType:'text/plain'}` to `…/projects/demo/locations/global:translateText` with `Bearer`; parses `translations[0].translatedText`.
  - Google 403 → `auth`; 400 "Source language: bo is not supported" → `unsupported_language`; a network error or abort → `transient`.
- [ ] Implement. Google's `getAccessToken` comes from `new GoogleAuth({scopes:'https://www.googleapis.com/auth/cloud-translation'})` in `index.ts` only. Run `npm test`. Expected: PASS.

### Task 5: Router with timeout, retry, breaker, fallback
**Consumes:** T4.
**Produces:** `createRouter({ providers: Record<ProviderId, TranslationProvider>, attemptTimeoutMs=8000, deadlineMs=20000, sleep, now, random, log }) → { translate(route: ProviderId[], text, from, to): Promise<{ translation: string; provider: ProviderId }> }`, which throws `AllProvidersFailedError { attempts: {provider, kind}[] }`.
- [ ] Failing tests:
  - The first provider succeeds; the second is never called.
  - The first is `transient` twice, so one retry is made, then it falls back to the second.
  - `unsupported_language` gets no retry, falls back immediately, and the breaker is not tripped.
  - An unconfigured provider is skipped without being called.
  - 5 consecutive failures open the breaker; the next call skips it; after 30 s (fake clock) one trial is allowed.
  - A hung provider is aborted at 8 s.
  - All providers failing throws `AllProvidersFailedError` listing the attempts.
  - `rate_limited` with `retryAfterMs: 5000` sleeps at most 2000 ms.
- [ ] Implement. Run `npm test`. Expected: PASS.

### Task 6: Firestore stores (emulator integration)
**Produces:**
- `cacheKey(gen, src, tgt, text): string`, a sha256 hex
- `createTranslationCache(db, { now, l1Max=5000, l1TtlMs=3600000 })` → `{ get(key): Promise<{translation, provider, tier:'l1'|'l2'}|null>; put(key, entry, ttlDays): Promise<void> }`
- `createQuotaStore(db, now)` → `{ read(uid, dayKey): Promise<{count, chars}>; reserve(uid, dayKey, limit, chars): Promise<{count}> /* throws DailyLimitError */; refund(uid, dayKey, chars): Promise<void> }`
- `createBudgetStore(db, { now, shards=10, cacheMs=30000, random })` → `{ add(chars): Promise<void>; isOver(limit): Promise<boolean> }`
- [ ] `vitest.emulator.config.ts` includes `test/emulator/**`. A setup file clears the Firestore emulator between tests through its REST endpoint `DELETE /emulator/v1/projects/demo-translation/databases/(default)/documents`.
- [ ] Failing emulator tests:
  - `put` then `get` returns `tier:'l2'` from a fresh cache instance, and `tier:'l1'` from the same instance.
  - The stored doc has `expireAt ≈ now + 180 d` and `sourceText`.
  - When `expireAt` is within 30 days, a hit extends it; otherwise there is no write (spy on the write count).
  - `cacheKey` differs by generation, by direction and by one character.
  - **11 concurrent `reserve(uid, d, 10, 5)` calls give exactly 10 resolves and 1 `DailyLimitError`, and the doc ends at `count === 10`.**
  - `refund` brings `count` and `chars` back down.
  - `dailyUsage` docs carry `expireAt = now + 7 d`.
  - `budget.add` ×3 then `isOver(limit)` is correct after the cache window.
- [ ] Implement. Run `npm run test:emulator`. Expected: PASS.

### Task 7: Handlers, errors, rate limit, plan, wiring
**Consumes:** T2–T6.
**Produces:**
- `handleTranslate(req: { data: unknown; auth?: { uid: string } }, deps: Deps): Promise<TranslateResponse>`
- `handleStatus(req, deps): Promise<StatusResponse>`
- `resolvePlan(uid): Promise<'free'|'pro'>`, which returns `'free'`
- `createRateLimiter({ perMinute, now })` → `{ take(uid): { ok: true } | { ok: false; retryAfterSec: number } }`
- `toHttpsError(e)`
- `index.ts`: `export const translate` and `getTranslationStatus` via `onCall` with the Design options.
- [ ] Failing unit tests (fakes for all deps; assert `HttpsError.code` and `details.reason`):
  - No auth → `unauthenticated`.
  - Disabled → `failed-precondition/translation_disabled`.
  - `{source:'bo',target:'dz'}` → `unsupported_pair`.
  - Whitespace only → `empty_text`.
  - 101 Tibetan code points → `text_too_long {maxChars:100,length:101}`; 100 pass.
  - `"bkra shis"` with source bo → `not_tibetan_script`.
  - A cache hit gives `cached:true`, no `reserve` call, and works with usage already at 10/10.
  - A miss calls `reserve` once and the router once, then `put` and `budget.add`; the response has `quota.used`, `remaining` and `resetsAt` (ISO).
  - Usage at the limit with a miss → `resource-exhausted/daily_limit` with `resetsAt`, and the router is not called.
  - The budget is over → `service_budget`, with no reserve.
  - The router throws → `refund` is called and `unavailable/provider_unavailable` is returned.
  - The router succeeds but `put` throws → the response succeeds and the error is logged.
  - The 21st call in a minute → `rate_limited`.
  - The logger never receives the input text (assert over all logged payloads).
  - `handleStatus` returns `{enabled, plan:'free', used, limit:10, remaining, resetsAt, maxChars:100, pairs}`.
- [ ] Implement. Run `npm test && npm run typecheck && npm run lint && npm run build`. Expected: all pass, and `lib/index.js` exports both functions.

### Task 8: Rules, indexes and TTL
**Files:** `backend/firestore.rules`, `backend/firestore.indexes.json`, `test/emulator/rules.test.ts`.
- [ ] Failing rules tests with `@firebase/rules-unit-testing` against `../firestore.rules`:
  - An unauthenticated create on `Message` `{message:"great app"}` is allowed.
  - `{message:"hi"}`, `{message:"x", extra:1}`, and a read, update or delete on `Message` are denied.
  - Any client, authenticated or not, is denied read and write on `translations`, `dailyUsage`, `budget` and `config`.
- [ ] Write the rules. `firestore.indexes.json` gets `fieldOverrides`: TTL (`"ttl": true`) on `translations.expireAt`, `dailyUsage.expireAt` and collection group `shards.expireAt`, plus empty `indexes` exemptions for `translations.sourceText`, `translations.translation` and `translations.createdAt`.
- [ ] Check whether firebase-tools accepts `ttl` in fieldOverrides (`firebase deploy --only firestore:indexes --dry-run` against `demo-translation` or by reading the CLI schema). If it doesn't, put `gcloud firestore fields ttls update expireAt --collection-group=… --enable-ttl` in README's runbook instead.
- [ ] Run `npm run test:emulator`. Expected: PASS.

### Task 9: Ops scripts, README, CI
- [ ] `scripts/check-providers.ts`:
  - Reads `AZURE_TRANSLATOR_KEY` and `AZURE_TRANSLATOR_REGION` from the environment, plus ADC for Google (`gcloud auth application-default login`).
  - For each provider × pair (`bo-en` "བཀྲ་ཤིས་བདེ་ལེགས།", `en-bo` "Thank you", `dz-en` "སྐུ་གཟུགས་བཟང་པོ།", `en-dz` "Thank you"), calls the T4 provider and prints a table: OK + output / unsupported / auth / error.
  - About 80 characters in total. It **settles Google's `bo` support.**
- [ ] `scripts/smoke-callable.ts`:
  - Signs in anonymously through Identity Toolkit `accounts:signUp` with the app's web API key (env).
  - Takes an App Check token from an exchanged debug token (env `APP_CHECK_DEBUG_TOKEN`, `APP_ID`).
  - POSTs `{data:{…}}` to `https://asia-south1-englishtibetandictionary.cloudfunctions.net/{translate,getTranslationStatus}` and prints the result. The second identical call must return `cached:true`.
- [ ] Write `backend/README.md`:
  - Architecture diagram (text) and the API contract table above.
  - The char-count rule and the Android snippet (`Firebase.functions("asia-south1").getHttpsCallable("translate")`).
  - Config reference, and how to switch providers (edit `config/translation.routes`) or invalidate the cache (`cache.generation++`).
  - Kill switch, costs, privacy and attribution duties.
  - Suggested log-based alerts: `outcome=provider_auth_error`, `reason=service_budget`, `AllProvidersFailed` rate.
  - A GCP budget alert, and the full runbook from T10.
- [ ] CI: add job `translation-backend` to `.github/workflows/main.yml`:
  - `defaults.run.working-directory: backend/functions`
  - setup-node 22 with an npm cache on `backend/functions/package-lock.json`
  - setup-java temurin 21
  - `npm ci && npm run lint && npm run typecheck && npm test && npm run test:emulator`
  - Validate the YAML locally (`python3 -I -c "import yaml…"` or `actionlint` if installed).
- [ ] Final local gate: `npm run lint && npm run typecheck && npm test && npm run test:emulator && npm run build`. Expected: all green.

### Task 10: Deploy (only after the owner says go; each sub-step shown before running)
- [ ] Create the service account `translation-fn` with `roles/datastore.user` and `roles/cloudtranslate.user`.
- [ ] `firebase functions:secrets:set AZURE_TRANSLATOR_KEY`, with the real key or `unset`. Set `AZURE_TRANSLATOR_REGION` in `functions/.env`.
- [ ] Enable the Anonymous Auth provider (Identity Toolkit config). It's needed by the smoke test and the future client.
- [ ] `firebase deploy --only functions,firestore:rules,firestore:indexes --project englishtibetandictionary`. This **replaces the open rules**; the legacy `Message` create keeps working.
- [ ] `npm run check-providers` (billable, ~80 chars), then set `config/translation.routes` from its table.
- [ ] Register an App Check debug token, then `npm run smoke`. Expected:
  - The first call returns `cached:false, quota.used:1`.
  - The repeat returns `cached:true, quota.used:1`.
  - `getTranslationStatus` returns `remaining:9`.
- [ ] Update memory with the run's state.

## Verification (end to end)
1. **Local:** `cd backend/functions && npm run lint && npm run typecheck && npm test && npm run test:emulator && npm run build`. All green, covering unit, emulator quota concurrency and rules tests.
2. **CI:** the new `translation-backend` job passes on the branch's workflow run when the owner pushes.
3. **Post-deploy (T10):**
   - `check-providers` table recorded.
   - `smoke` shows a miss then a cache hit with quota counted once.
   - Cloud Logging shows structured `translate` events with no user text.
   - Firestore console shows the `translations`, `dailyUsage` and `budget` docs with `expireAt`, and TTL policies listed as active.

## Execution
Recommended: **Native**. I implement T0–T9 in this session, then one fresh reviewer checks the whole `backend/` before T10. The tasks are sequential and depend tightly on each other's interfaces, there are only 10 of them, and the expensive mistakes (quota races, the rules lock-down) are pinned by emulator tests. Subagent-driven review per task is available if you prefer it. T10 waits for your explicit go.
