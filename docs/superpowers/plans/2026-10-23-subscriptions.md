# Pro Subscriptions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sell Pro as two renewing Play subscriptions through RevenueCat, with the server deciding who is Pro from a
per-user record that a RevenueCat webhook (and an app-triggered sync) keeps current, behind a remote paywall switch.

**Architecture:** Backend: one write path, `refreshSubscription(uid)`, re-reads the RevenueCat v1 subscriber and
stores `subscriptions/{uid}` only if newer; the webhook and the `syncSubscription` callable both call it, and
`createPlanResolver` reads the record on every request. App: a `BillingRepository` interface whose only
implementation imports RevenueCat; one `PaywallViewModel` drives the 1za sheet from every entry point, and the server's
quota plan (not the SDK) decides the Pro surfaces.

**Tech Stack:** Backend: TypeScript ESM (NodeNext), firebase-functions 7.4, firebase-admin 14.5, zod 4, vitest,
Firestore emulator. App: Kotlin, Compose, Hilt, Robolectric, RevenueCat `com.revenuecat.purchases:purchases` 10.26.0.

**Spec:** `docs/superpowers/specs/2026-10-23-subscriptions-design.md`

## Global Constraints

- Never commit or push unless the owner asks in the conversation. No AI attribution in any commit, PR or file.
- Deploys, `config/translation` edits, billable calls, IAM grants and secret values: owner's explicit go only.
- Never log user text, emails, prices, store tokens, the webhook `Authorization` header or any secret.
- Backend: relative imports end in `.js`; no `console` outside `scripts/`; `src/domain/` stays pure; emulator tests use
  project `demo-translation`; `index.test.ts` pins every endpoint's options.
- Backend coverage thresholds (unit 95/95, emulator config as is) are floors; never lower them.
- RevenueCat products `dictionary_pro:monthly`, `dictionary_pro_annually:yearly`; entitlement `pro`; offering
  `default` (`offerings.current ?: offerings["default"]`); packages `$rc_monthly`, `$rc_annual`.
- Subscription ids: `dictionary_pro` → monthly, `dictionary_pro_annually` → yearly, matched exactly on the id before
  `:`. Anything else → product `null`.
- Pro leeway 15 min; `syncSubscription` rate 6/min/uid; reconciler throttle 5 min; RevenueCat fetch timeout 10 s, one
  retry on network/429/5xx; webhook uid cap 10; uid plausible = 1–128 chars, no `/`, not `$RCAnonymousID:`.
- Webhook: onRequest, asia-south1, `invoker: "public"`, maxInstances 5, timeout 30 s, secrets
  `REVENUECAT_WEBHOOK_AUTH` and `REVENUECAT_API_KEY`, SA
  `translation-fn@englishtibetandictionary.iam.gserviceaccount.com`.
- `config/translation.billing.enabled` defaults to false; present-but-not-boolean → false.
- App: only `RevenueCatBillingRepository.kt` (and `BillingSetup.kt`, which configures the SDK) import `com.revenuecat`.
  Blank `REVENUECAT_ANDROID_KEY` → `available = false` → fake door. Never hardcode a price.
- Manage URL: `https://play.google.com/store/account/subscriptions?sku={subscriptionId}&package=com.kharagedition.tibetandictionary`;
  without a known plan: `https://play.google.com/store/account/subscriptions?package=com.kharagedition.tibetandictionary`.
- Switch to yearly: purchase the yearly package with old product `dictionary_pro` and Google replacement mode
  `WITH_TIME_PRORATION`.
- New strings go in `values` and `values-bo`; Tibetan drafts are listed for the owner to check.

## Review Focus

1. A uid with characters that need escaping (`a/b` is rejected, `a%b`, `ü`) reaches RevenueCat URL-encoded, never as a
   path break. Pinned in Task 2.
2. The webhook can land before RevenueCat's v1 shows the entitlement: the refresh then writes nothing for a user with no
   doc, and the app's sync or `ProReconciler` repairs it later. Pinned in Tasks 4 and 11.
3. A webhook refresh and a sync refresh finishing out of order: the older `fetchedAt` never overwrites the newer.
   Pinned in Task 3.
4. The account changes while the paywall is open (sign-out, switch to another Google account): the sheet drops back
   to `NeedsSignIn` or reloads, and never buys for the old uid. Pinned in Task 12.
5. Server says Pro but the build has no RevenueCat key (or the SDK has no entitlement yet): the Pro card still shows,
   without dates, and Manage subscription opens the generic URL. Pinned in Task 14.

---

## Backend (`backend/functions`, run commands there)

### Task 1: Billing switch and status fields

**Files:**
- Modify: `src/config/runtimeConfig.ts`, `src/service/translateHandler.ts` (`StatusResponse`, `handleStatus`)
- Test: `test/unit/runtimeConfig.test.ts`, `test/unit/translateHandler.test.ts`

**Interfaces:**
- Produces: `RuntimeConfig.billing: { readonly enabled: boolean }`, default `{ enabled: false }`.
  `StatusResponse` gains `billingEnabled: boolean`, `proLimit: number`, `proMaxChars: number`.

- [ ] **Step 1: Failing tests.** runtimeConfig: `billing` absent → `{enabled:false}`; `{billing:{enabled:true}}` → true;
  `{billing:{enabled:"true"}}` → false and logs an error; `{billing:"on"}` → false. translateHandler status: reply has
  `billingEnabled` (false by default, true when config says so), `proLimit: 300`, `proMaxChars: 500` (and follows a
  `pro` override, e.g. 200/400).
- [ ] **Step 2:** `npx vitest run test/unit/runtimeConfig.test.ts test/unit/translateHandler.test.ts` → FAIL.
- [ ] **Step 3: Implement.** `billing` parsed with `field(doc, "billing", z.object({ enabled: z.unknown() }).partial(), log)`
  then `enabled === true` only when it is a boolean `true`; a non-boolean present value logs and fails closed.
- [ ] **Step 4:** same command → PASS; `npm run typecheck`.

### Task 2: RevenueCat v1 client and mapping

**Files:**
- Create: `src/billing/revenueCat.ts`, `test/unit/revenueCat.test.ts`, `test/fixtures/revenuecat/*.json`
  (active-monthly, active-yearly, cancelled-active, grace, billing-issue, expired, sandbox, no-entitlement,
  unknown-product), copied from RevenueCat's documented v1 subscriber shape.

**Interfaces:**
- Produces:
  ```ts
  export type ProProduct = "monthly" | "yearly";
  export interface ProEntitlement { active: boolean; expiresAt: Date | null; product: ProProduct | null;
    willRenew: boolean; billingIssue: boolean; sandbox: boolean; store: string }
  export interface SubscriberSnapshot { fetchedAt: Date; pro: ProEntitlement | null }
  export class RevenueCatError extends Error { readonly kind: "network" | "rate_limited" | "server" | "auth" | "bad_response" }
  export function toSnapshot(body: unknown): SubscriberSnapshot;          // throws RevenueCatError("bad_response")
  export function productOf(productIdentifier: string): ProProduct | null;
  export interface RevenueCatClient { getSubscriber(uid: string): Promise<SubscriberSnapshot> }
  export function createRevenueCatClient(options: { apiKey: string; fetchImpl?: typeof fetch;
    timeoutMs?: number; retryDelayMs?: number }): RevenueCatClient;
  ```

- [ ] **Step 1: Failing mapping tests** (one per fixture):
  active monthly → `{active:true, product:"monthly", willRenew:true, billingIssue:false, sandbox:false, store:"play_store"}`;
  active yearly → product `"yearly"`; cancelled-active → `willRenew:false`, still active; grace →
  `expiresAt` = `grace_period_expires_date` (later than `expires_date`), active; billing-issue → `billingIssue:true`;
  expired → `active:false`; sandbox → `sandbox:true`; no-entitlement → `pro: null` with `fetchedAt` from
  `request_date_ms`; unknown-product (`dictionary_pro_x`) → `product:null`. `productOf("dictionary_pro:monthly")` →
  monthly, `productOf("dictionary_pro_annually")` → yearly, `productOf("dictionary_pro_annually_x")` → null.
  A body without `subscriber` or with `entitlements` not a map → throws `bad_response`. `active` is
  `expiresAt > fetchedAt`; an entitlement with `expires_date: null` → active false (never lifetime).
- [ ] **Step 2: Failing fetch tests** with a scripted `fetchImpl`: URL is
  `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(uid)}` (assert for `a%b` and `ü`), header
  `Authorization: Bearer <key>`; 200 and 201 parse; 401/403 → `auth` (no retry); 404 → `bad_response`; 429 then 200 →
  succeeds after one retry; 500 twice → `server`; network error twice → `network`; abort after `timeoutMs` → `network`;
  non-JSON 200 → `bad_response`. The error messages never contain the key.
- [ ] **Step 3:** `npx vitest run test/unit/revenueCat.test.ts` → FAIL.
- [ ] **Step 4: Implement** with zod (`.passthrough()` objects, dates as ISO strings or null), `AbortSignal.timeout`.
  Subscription entry = `subscriber.subscriptions[entitlement.product_identifier]`; missing entry → `willRenew:false`,
  `billingIssue:false`, `sandbox:false`, `store:"unknown"`.
- [ ] **Step 5:** same command → PASS.

### Task 3: Subscription store and rules

**Files:**
- Create: `src/store/subscriptionStore.ts`, `test/emulator/subscriptionStore.test.ts`
- Modify: `test/emulator/rules.test.ts`, `backend/firestore.rules` (comment only: list `subscriptions` as server-only)

**Interfaces:**
- Consumes: `ProEntitlement` (Task 2).
- Produces:
  ```ts
  export type SyncSource = "webhook" | "sync";
  export interface StoredSubscription extends ProEntitlement { fetchedAt: Date; updatedAt: Date; source: SyncSource }
  export interface SubscriptionStore {
    get(uid: string): Promise<StoredSubscription | null>;
    /** Writes in a transaction unless the stored fetchedAt is the same or newer; with onlyIfExists, never creates. */
    putIfNewer(uid: string, record: ProEntitlement & { fetchedAt: Date }, source: SyncSource,
      options?: { onlyIfExists?: boolean }): Promise<"written" | "stale" | "absent">;
    delete(uid: string): Promise<void>;
  }
  export function createSubscriptionStore(db: Firestore, options?: { now?: () => Date }): SubscriptionStore;
  ```
  Doc path `subscriptions/{encodeURIComponent(uid)}`; dates stored as Timestamps; `expiresAt` may be null.

- [ ] **Step 1: Failing emulator tests:** write then `get` round-trips every field; an older `fetchedAt` → `"stale"`
  and the doc is unchanged; an equal `fetchedAt` → `"stale"`; a newer one → `"written"`; two concurrent writes in
  either order leave the newer; `onlyIfExists` on a missing doc → `"absent"` and no doc; `delete` removes it and is a
  no-op when missing; `get` of a missing uid → null. Rules: signed-in and signed-out clients can't get, list, create,
  update or delete `subscriptions/x`.
- [ ] **Step 2:** `npx firebase emulators:exec --only firestore --project demo-translation "npx vitest run --config vitest.emulator.config.ts test/emulator/subscriptionStore.test.ts test/emulator/rules.test.ts"` → FAIL.
- [ ] **Step 3: Implement.** A `get` that finds a malformed doc throws (the resolver logs it and treats it as free).
- [ ] **Step 4:** same command → PASS.

### Task 4: Refresh and plan resolver

**Files:**
- Create: `src/service/subscriptionRefresh.ts`, `test/unit/subscriptionRefresh.test.ts`
- Modify: `src/service/plan.ts`, `test/unit/plan.test.ts`, `test/unit/fakes.ts` (add `memorySubscriptions()`),
  `test/emulator/translatePipeline.test.ts`

**Interfaces:**
- Consumes: `RevenueCatClient` (Task 2), `SubscriptionStore` (Task 3).
- Produces:
  ```ts
  export interface RefreshDeps { revenueCat: RevenueCatClient; subscriptions: SubscriptionStore; log: Logger }
  export function refreshSubscription(uid: string, source: SyncSource, deps: RefreshDeps): Promise<StoredSubscription | null>;
  // plan.ts
  export const PRO_LEEWAY_MS = 15 * 60_000;
  export function isProActive(sub: Pick<StoredSubscription, "active" | "expiresAt"> | null, now: Date): boolean;
  export function createPlanResolver(deps: { subscriptions: Pick<SubscriptionStore, "get">; now: () => Date; log: Logger }): PlanResolver;
  ```
  The constant `resolvePlan` is removed; `isGuest`, `Plan`, `PlanUser`, `PlanResolver` stay.

- [ ] **Step 1: Failing unit tests.** refresh: entitlement → `putIfNewer(..., source)` and returns the stored record;
  no entitlement and no doc → `putIfNewer` with `onlyIfExists` → returns null, nothing stored; no entitlement with a doc
  → doc becomes `active:false, expiresAt:null, product:null, willRenew:false, billingIssue:false`; `"stale"` →
  returns what `get` now holds; a `RevenueCatError` propagates (logged with `uid`, `kind`, never the key). Resolver:
  guest → guest without a read; no doc → free; active and `expiresAt = now + 1 ms` → pro; `expiresAt = now − 14 min
  59 s` → pro; `now − 15 min` → free; `active:false` with a future `expiresAt` → free; `expiresAt: null` → free; a
  `get` that rejects → free and one ERROR log with the uid.
- [ ] **Step 2:** `npx vitest run test/unit/subscriptionRefresh.test.ts test/unit/plan.test.ts` → FAIL.
- [ ] **Step 3: Implement**, with `memorySubscriptions()` in `fakes.ts` mirroring the store contract.
- [ ] **Step 4: Failing pipeline emulator tests** (`translatePipeline.test.ts`, resolver = `createPlanResolver` over the
  real store): a Pro doc → status says `plan:"pro"`, `limit:300`, `maxChars:500` and 450 characters translate; an expired
  Pro doc → free limits; Pro at 300 used → `daily_limit` with `details.plan === "pro"`.
- [ ] **Step 5:** run the unit command and the emulator file → PASS.

### Task 5: Webhook and sync handlers, account deletion

**Files:**
- Create: `src/service/webhookHandler.ts`, `src/service/syncHandler.ts`, `test/unit/webhookHandler.test.ts`,
  `test/unit/syncHandler.test.ts`
- Modify: `src/service/errors.ts` (`"billing_unavailable"`), `src/service/accountHandler.ts`,
  `test/unit/accountHandler.test.ts`, `test/unit/errors.test.ts` if it enumerates reasons

**Interfaces:**
- Consumes: `refreshSubscription` (Task 4), `isProActive`, `RateLimiter`, `requireUser`, `toHttpsError`.
- Produces:
  ```ts
  export interface WebhookRequest { method: string; header(name: string): string | undefined; body: unknown }
  export interface WebhookReply { status: 200 | 400 | 401 | 405 | 500; body: string }
  export interface WebhookDeps { authSecret: () => string; refresh: (uid: string) => Promise<unknown>; log: Logger }
  export function handleWebhook(request: WebhookRequest, deps: WebhookDeps): Promise<WebhookReply>;
  export function webhookUids(event: Record<string, unknown>): string[];   // collected, filtered, deduped, capped at 10
  export interface SyncDeps { refresh: (uid: string) => Promise<StoredSubscription | null>; rateLimiter: RateLimiter;
    log: Logger; now: () => Date }
  export function handleSyncSubscription(request: CallableLike, deps: SyncDeps): Promise<{ plan: Plan }>;
  // AccountDeps gains: subscriptions: Pick<SubscriptionStore, "delete">
  ```

- [ ] **Step 1: Failing webhook tests**, one per row of the spec's table: GET → 405; missing, wrong, longer, shorter or
  differently-cased `Authorization` → 401 with no refresh, and no log entry contains the header value or the secret;
  body not `{event:{id,type}}` → 400; `TEST` → 200, no refresh, logged; `INITIAL_PURCHASE` for `u1` → refresh `u1`, 200;
  `TRANSFER` with `transferred_from:["a"]`, `transferred_to:["b"]` → refreshes both; `$RCAnonymousID:x`, `""`, `"a/b"` and a
  129-char id are skipped; 12 aliases → 10 refreshes; duplicates refresh once; one refresh rejects → 500 after all ran;
  the log line has `id`, `type`, `environment`, `uids` count and `outcome`, and never `price`, `email` or tokens from
  the body. The secret compare uses `timingSafeEqual` on equal-length buffers (length mismatch → 401 without compare).
- [ ] **Step 2: Failing sync tests:** no auth → `unauthenticated`; guest → `failed-precondition`/`guest_account`;
  7th call in a minute → `resource-exhausted`/`rate_limited` with `retryAfterSec`; refresh returns an active record →
  `{plan:"pro"}`; returns null → `{plan:"free"}`; refresh throws `RevenueCatError` → `unavailable`/`billing_unavailable`;
  any other error → `internal`.
- [ ] **Step 3: Failing account test:** delete removes usage, then `subscriptions.delete(uid)`, then the user; a failed
  subscription delete fails the call before the user is deleted.
- [ ] **Step 4:** `npx vitest run test/unit/webhookHandler.test.ts test/unit/syncHandler.test.ts test/unit/accountHandler.test.ts` → FAIL.
- [ ] **Step 5: Implement.** The webhook handler has no firebase imports.
- [ ] **Step 6:** same command → PASS.

### Task 6: Wiring, emulator end-to-end, docs

**Files:**
- Modify: `src/config/params.ts` (`REVENUECAT_API_KEY`, `REVENUECAT_WEBHOOK_AUTH` via `defineSecret`),
  `src/index.ts`, `test/unit/index.test.ts`, `test/emulator/callables.test.ts`, `backend/README.md`,
  `functions/architecture.md`, `backend/CLAUDE.md`, `vitest.config.ts` only if a new pure file needs no exclusion

**Interfaces:**
- Produces exports `revenuecatWebhook` (onRequest) and `syncSubscription` (onCall `{...common, maxInstances: 5,
  secrets: [REVENUECAT_API_KEY]}`); status/translate deps use `createPlanResolver`; account deps get the store.

- [ ] **Step 1: Failing `index.test.ts`:** `revenuecatWebhook` endpoint has `region ["asia-south1"]`, `maxInstances 5`,
  `timeoutSeconds 30`, the SA email, `httpsTrigger: { invoker: ["public"] }`, secrets
  `["REVENUECAT_WEBHOOK_AUTH","REVENUECAT_API_KEY"]` (order-insensitive); `syncSubscription` is a callable with
  `maxInstances 5` and secret `REVENUECAT_API_KEY`; existing endpoints unchanged. `syncSubscription.run()` as a guest →
  `guest_account`.
- [ ] **Step 2: Failing callables emulator tests:** stub env `REVENUECAT_API_KEY=test-rc-key`,
  `REVENUECAT_WEBHOOK_AUTH=test-webhook-secret`; the fetch stub answers `api.revenuecat.com` from a per-test script and
  records the URL. `syncSubscription` for a signed-in uid with an active fixture → `{plan:"pro"}` and the doc exists;
  then `getTranslationStatus` → `plan:"pro"`, `billingEnabled:false`, `proLimit:300`. The webhook through
  `revenuecatWebhook(req, res)` with a minimal req/res double: wrong auth → 401; `RENEWAL` → 200 and the doc updated;
  RevenueCat 500 twice → 500. `deleteAccount` of a Pro user removes `subscriptions/{uid}`.
- [ ] **Step 3:** `npm test` and `npm run test:emulator` → FAIL on the new tests only.
- [ ] **Step 4: Implement** the wiring. `getSyncDeps()` and `getWebhookDeps()` built lazily like the others; one
  `createRateLimiter()` for sync; the webhook reads `REVENUECAT_WEBHOOK_AUTH.value()` per request.
- [ ] **Step 5: Docs.** README: the two functions' contracts (webhook status table, sync reply and errors incl.
  `billing_unavailable`), `subscriptions/{uid}` fields, `billing.enabled`, the new status fields, secrets runbook
  (`firebase functions:secrets:set REVENUECAT_API_KEY`, `... REVENUECAT_WEBHOOK_AUTH` from
  `openssl rand -base64 32`), known limits from the spec. architecture.md: webhook → refresh → store, sync, resolver.
  backend/CLAUDE.md: the new functions in the intro and the "owner's go" list, the webhook gotchas.
- [ ] **Step 6: Full gate:** `npm run format && npm run lint && npm run typecheck && npm run coverage && npm run coverage:emulator && npm run build`
  → all pass, thresholds met.

---

## App (run Gradle from the repo root)

### Task 7: Translation API and status fields

**Files:**
- Modify: `data/translation/TranslationApi.kt`, `CallableTranslationApi.kt`, `TranslationModels.kt`,
  `TranslationRepository.kt`, `TranslationRepositoryImpl.kt`
- Test: `CallableTranslationApiTest.kt`, `TranslationRepositoryImplTest.kt`, fakes `FakeTranslationApi.kt`,
  `FakeTranslationRepository.kt`

**Interfaces:**
- Produces:
  - `TranslationApi.syncSubscription(): ApiResult<Plan>` (callable `syncSubscription`, no data).
  - `StatusReply(enabled, quota, freeLimit, pairs, billingEnabled: Boolean, proLimit: Int?, proMaxChars: Int?)`;
    the three new fields are optional in the reply (an older backend omits them → false/null).
  - `TranslationAvailability(enabled, pairs, billingEnabled: Boolean = false, proLimit: Int? = null, proMaxChars: Int? = null)`.
  - `TranslationRefusal.BillingUnavailable` (`UNAVAILABLE` + `billing_unavailable`), analytics name
    `billing_unavailable`.
  - `TranslationRepository.syncSubscription(): Plan?`: calls the API; on `Ok` runs `refresh(force = true)` and returns
    the plan; on refusal returns null (AccountGone still signs out).

- [ ] **Step 1: Failing tests:** status parses the three fields and defaults them when missing; `syncSubscription`
  maps `{plan:"pro"}` → `Ok(PRO)`, `UNAVAILABLE`/`billing_unavailable` → `BillingUnavailable`,
  `FAILED_PRECONDITION`/`guest_account` → `GuestAccount`; repository: availability carries `billingEnabled`; a sync
  `Ok` forces one status call; a refusal returns null and makes no status call.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*CallableTranslationApiTest' --tests '*TranslationRepositoryImplTest'` → FAIL.
- [ ] **Step 3: Implement**; extend the fakes (`syncResults` queue, `syncCalls` count).
- [ ] **Step 4:** same command → PASS.

### Task 8: Billing models, interface, analytics

**Files:**
- Create: `data/billing/BillingModels.kt`, `data/billing/BillingRepository.kt`,
  test `fakes/FakeBillingRepository.kt`
- Modify: `core/analytics/Analytics.kt`, `core/Links.kt` (`manageSubscriptionUrl(plan: ProPlan?)`)
- Test: `core/analytics/AnalyticsEventTest.kt` (or the existing analytics test), `core/LinksTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class ProPlan(val productId: String, val code: String) { MONTHLY("dictionary_pro", "monthly"), YEARLY("dictionary_pro_annually", "yearly");
      companion object { fun fromProductId(id: String?): ProPlan? } }   // exact match on the part before ':'
  data class ProOffer(val plan: ProPlan, val price: String, val pricePerMonth: String?)
  data class ProOffers(val monthly: ProOffer?, val yearly: ProOffer?)
  data class ProEntitlement(val active: Boolean, val plan: ProPlan?, val expiresAt: Instant?, val willRenew: Boolean, val billingIssue: Boolean)
  enum class BillingError(val code: String) { NETWORK("network"), STORE("store"), NOT_ALLOWED("not_allowed"), UNAVAILABLE("unavailable"), UNKNOWN("unknown") }
  sealed interface PurchaseOutcome { data object Purchased; data object Cancelled; data object Pending; data class Failed(val error: BillingError) }
  sealed interface OffersResult { data class Loaded(val offers: ProOffers); data class Failed(val error: BillingError) }
  sealed interface RestoreOutcome { data object Restored; data object NothingToRestore; data class Failed(val error: BillingError) }
  interface BillingRepository {
      val available: Boolean
      val entitlement: StateFlow<ProEntitlement?>
      suspend fun offers(): OffersResult
      suspend fun purchase(activity: Activity, plan: ProPlan, switchFrom: ProPlan? = null): PurchaseOutcome
      suspend fun restore(): RestoreOutcome
      /** Google uid → logIn; null → logOut (no-op when already anonymous). Never throws. */
      suspend fun identify(uid: String?)
  }
  // Analytics
  data class PaywallView(val source: PromptSource, val mode: String)        // "paywall_view": source, mode
  data class PurchaseStart(val plan: String, val source: PromptSource)       // "purchase_start"
  data class PurchaseResult(val plan: String, val outcome: String, val code: String?, val switch: Boolean) // "purchase_result"
  data class RestoreResult(val outcome: String)                              // "restore_result"
  ```
  `FakeBillingRepository`: settable `available`, `entitlement` MutableStateFlow, queues `offersResults`,
  `purchaseResults`, `restoreResults`, records `purchaseCalls` (plan, switchFrom) and `identifyCalls`.

- [ ] **Step 1: Failing tests:** `ProPlan.fromProductId("dictionary_pro:monthly")` → MONTHLY, `"dictionary_pro_annually"`
  → YEARLY, `"dictionary_pro_x"` → null, null → null; event names and params exactly as above (`code` omitted when null,
  `switch` as `"true"/"false"`); manage URL for MONTHLY contains `sku=dictionary_pro&package=…`, null → generic URL.
- [ ] **Step 2:** run those tests → FAIL. **Step 3:** implement. **Step 4:** → PASS.

### Task 9: RevenueCat SDK, build key, DI

**Files:**
- Modify: `gradle/libs.versions.toml` (add `revenuecat = "10.26.0"`, `revenuecat-purchases`; remove `billing`,
  `billing-ktx`), `app/build.gradle.kts`, `.github/workflows/release.yml`, `DictionaryApplication.kt`
- Create: `data/billing/RevenueCatBillingRepository.kt`, `data/billing/BillingSetup.kt`,
  `data/billing/RevenueCatMapping.kt` (pure functions over plain values, so tests need no SDK objects),
  `core/di/BillingModule.kt`
- Test: `data/billing/RevenueCatMappingTest.kt`

**Interfaces:**
- Consumes: Task 8 types.
- Produces: `BuildConfig.REVENUECAT_ANDROID_KEY: String`; `BillingSetup.configure(context: Context, uid: String?)`
  (no-op on a blank key or no FirebaseApp; never throws); `BillingModule` binds `BillingRepository` →
  `RevenueCatBillingRepository` as a singleton. Mapping functions:
  `entitlementOf(isActive, productIdentifier, expiration: Date?, willRenew, billingIssueAt: Date?): ProEntitlement`,
  `billingErrorOf(code: String /* PurchasesErrorCode.name */): BillingError`,
  `purchaseOutcomeOf(code: String?, userCancelled: Boolean): PurchaseOutcome`.

- [ ] **Step 1: Verify the SDK surface** in the resolved AAR (`./gradlew :app:dependencies` then `javap` on the
  classes jar from the Gradle cache): `Purchases.configure(PurchasesConfiguration.Builder(ctx, key).appUserID(..))`,
  `Purchases.isConfigured`, `awaitOfferings`, `awaitPurchase(PurchaseParams)`, `awaitRestore`, `awaitLogIn`,
  `awaitLogOut`, `awaitCustomerInfo`, `PurchaseParams.Builder.oldProductId` and the Google replacement-mode setter,
  `StoreProduct.pricePerMonth`, `PurchasesTransactionException.userCancelled`, `PurchasesErrorCode` names,
  `UpdatedCustomerInfoListener`. Record the exact names in this task before writing code. Check the transitive Play
  Billing version is ≥ 8.
- [ ] **Step 2: Failing mapping tests:** active `dictionary_pro` → `ProEntitlement(true, MONTHLY, …)`; billing issue
  date → `billingIssue = true`; unknown product → plan null; `PurchaseCancelledError` → Cancelled;
  `PaymentPendingError` → Pending; `NetworkError` → Failed(NETWORK); `StoreProblemError` → Failed(STORE);
  `PurchaseNotAllowedError`/`PurchaseInvalidError` → NOT_ALLOWED; `ConfigurationError`/`ProductNotAvailableForPurchaseError`
  → UNAVAILABLE; anything else → UNKNOWN; `userCancelled = true` with any code → Cancelled.
- [ ] **Step 3:** `./gradlew :app:testDebugUnitTest --tests '*RevenueCatMappingTest'` → FAIL.
- [ ] **Step 4: Implement.** `RevenueCatBillingRepository`: `available = Purchases.isConfigured`; every call returns a
  failure/`UNAVAILABLE` (or no-op) when not available; `entitlement` fed by the customer-info listener and every
  result; offerings → `current ?: all["default"]`, packages `monthly` / `annual`; purchase of the switch passes
  old product `dictionary_pro` with `WITH_TIME_PRORATION`; `ProductAlreadyPurchasedError` → restore, then Purchased if
  the restore shows `pro` active; `identify(null)` skips `logOut` when `isAnonymous`. Build: `REVENUECAT_ANDROID_KEY` from env,
  else gradle property, else `local.properties` `revenuecat.androidKey`, else `""`, as a quoted `buildConfigField`.
  `DictionaryApplication.configureFirebase()` calls `billingSetup.configure(this, currentGoogleUid)` inside its
  `runCatching`. release.yml: pass `REVENUECAT_ANDROID_KEY: ${{ secrets.REVENUECAT_ANDROID_KEY }}` to the build step.
- [ ] **Step 5:** mapping tests PASS; `./gradlew :app:assembleDebug` succeeds; `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep billing`
  shows only RevenueCat's billing ≥ 8.

### Task 10: Identity sync and reconciler

**Files:**
- Create: `data/billing/BillingIdentitySync.kt`, `data/billing/ProReconciler.kt`, tests for both
- Modify: `core/init/AppInitializer.kt` (start both after `install`, each in `step`), its test if it pins steps;
  `navigation/AppNavHostTest.kt` (uninstall `BillingModule`, `@BindValue` a `FakeBillingRepository`)

**Interfaces:**
- Consumes: `BillingRepository`, `AuthRepository.account`, `TranslationRepository.quota` and `syncSubscription()`, `AppClock`.
- Produces: `BillingIdentitySync.start()`, `ProReconciler.start()`; both launch in `@ApplicationScope` and return.

- [ ] **Step 1: Failing tests.** Identity: Google account `u1` → `identify("u1")`; then guest → `identify(null)`;
  null account → `identify(null)`; the same uid twice → one call; unavailable billing → no calls. Reconciler:
  entitlement active + Google account + quota plan FREE → one `syncSubscription()`; a second mismatch within 5 min
  (FakeClock) → none; after 5 min → another; quota PRO, guest, or inactive entitlement → none.
- [ ] **Step 2:** run them → FAIL. **Step 3:** implement. **Step 4:** → PASS; `AppNavHostTest` still passes.

### Task 11: PaywallViewModel

**Files:**
- Create: `ui/paywall/PaywallViewModel.kt`, `ui/paywall/PaywallUiState.kt`, test `ui/paywall/PaywallViewModelTest.kt`

**Interfaces:**
- Consumes: `BillingRepository`, `AuthRepository`, `TranslationRepository` (availability, quota, `syncSubscription`),
  `SignInAction`, `Analytics`, `ErrorReporter`.
- Produces:
  ```kotlin
  enum class PaywallMode(val code: String) { PURCHASE("offers"), SWITCH("switch") }
  data class ProTable(val freePerDay: Int, val freeChars: Int, val proLimit: Int, val proChars: Int) // fallbacks 10, 100, 300, 500
  sealed interface ProSheetUi {
      data object FakeDoor; data object NeedsSignIn; data object Loading
      data class Offers(val offers: ProOffers, val selected: ProPlan, val working: Boolean = false)
      data class Switch(val yearly: ProOffer, val working: Boolean = false)
      data object Error; data object Pending; data object AlreadyPro
  }
  data class PaywallUiState(val sheet: ProSheetUi, val table: ProTable)
  sealed interface PaywallEvent { data class Select(val plan: ProPlan); data object Retry; data object Restore }
  sealed interface PaywallEffect { data class Close(val toast: UiText?) }
  class PaywallViewModel { val uiState: StateFlow<PaywallUiState>; val effects: Flow<PaywallEffect>
      fun open(source: PromptSource, mode: PaywallMode); fun onEvent(event: PaywallEvent)
      fun purchase(activity: Activity); fun signIn(activity: Activity) }
  ```
  Mode resolution on `open`: billing off (`availability.billingEnabled != true`) or `!billing.available` → FakeDoor and
  logs `ProInterest(source)` + `PaywallView(source, "fake_door")`; no Google account → NeedsSignIn
  (`"sign_in"`); entitlement active and server plan not PRO → AlreadyPro (runs `syncSubscription`, then Close(null));
  else Loading → offers → Offers (selected YEARLY when present, else MONTHLY) or, in SWITCH, Switch(yearly) — logs
  `PaywallView(source, mode.code)` once loaded.

- [ ] **Step 1: Failing tests** (each a test): billing off → FakeDoor + `pro_interest`; no key → FakeDoor; guest →
  NeedsSignIn, sign-in success → Loading → Offers; Offers preselects Yearly; Select(MONTHLY) changes selection;
  purchase logs `purchase_start{plan=yearly, source}`, Purchased → one `syncSubscription()` → Close("Welcome to Pro") and
  `purchase_result{outcome=success, switch=false}`; sync returning null still closes; Cancelled → back to Offers,
  `outcome=cancelled`; Pending → Pending, `outcome=pending`; Failed(NETWORK) → Error, `outcome=error, code=network`;
  Retry from Error reloads offers; offers failing → Error; Restore found → sync → Close("Pro restored"),
  `restore_result{restored}`; Restore none → toast "No Pro purchase…" effect, stays, `restore_result{none}`;
  SWITCH mode → Switch(yearly) and purchase passes `switchFrom = MONTHLY`, `switch=true`; `working = true` while a
  purchase runs and a second `purchase()` is ignored; entitlement active + server FREE → AlreadyPro → sync → Close;
  account switches to guest while Offers → NeedsSignIn; account switches to another Google uid → reloads offers.
  The table uses status values when present (proLimit 250 → 250) and the fallbacks otherwise.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*PaywallViewModelTest'` → FAIL.
- [ ] **Step 3: Implement.** Toast strings: `pro_welcome`, `pro_restored`, `pro_restore_none`.
- [ ] **Step 4:** → PASS.

### Task 12: Paywall sheet UI and entry points

**Files:**
- Modify: `ui/components/ProSheet.kt` (stateless `ProSheet(state: PaywallUiState, onEvent, onContinue, onSignIn, onDismiss)`
  rendering every `ProSheetUi`; the fake-door body unchanged), `ui/components/Sheets.kt` (optional
  `dismissible: Boolean = true` on `DictModalSheet` via `confirmValueChange`)
- Create: `ui/paywall/PaywallSheet.kt` (`PaywallSheet(source, mode, onDismiss)`: `hiltViewModel<PaywallViewModel>()`,
  `LaunchedEffect(source, mode) { open }`, effects → toast + dismiss; activity from `LocalActivity`)
- Modify entry points to open `PaywallSheet` and stop logging `ProInterest` themselves: `TranslateScreen`/
  `TranslateViewModel` (`TranslateSheet.PRO` + `proSource: PromptSource?` in state), `SearchContent`/`SearchViewModel`,
  `ProfileScreen`/`ProfileViewModel` (`paywall: PaywallMode?` replaces `showProSheet`)
- Strings (`values`, `values-bo`): `pro_continue`, `pro_sign_in_to_continue`, `pro_yearly`, `pro_monthly`,
  `pro_per_month`, `pro_terms` ("Renews automatically · Cancel any time in Google Play"), `pro_restore`,
  `pro_fair_use_note` ("*Fair use: up to %1$s a day"), `pro_error`, `pro_retry`, `pro_pending`, `pro_already`,
  `pro_switch_title`, `pro_switch_button`, `pro_switch_note`, `pro_welcome`, `pro_restored`, `pro_restore_none`
- Test: `ui/components/ProSheetTest.kt`, updated `TranslateViewModelTest`, `SearchViewModelTest`, `ProfileViewModelTest`

- [ ] **Step 1: Failing screen tests** by tag: `pro_sheet_coming_soon` (FakeDoor); `pro_sheet_sign_in` (NeedsSignIn);
  `pro_sheet_loading`; `pro_sheet_plan_yearly` selected and `pro_sheet_plan_monthly` with Play's price strings,
  `pro_sheet_continue`, `pro_sheet_restore`, the fair-use note with the number; `pro_sheet_progress` while working;
  `pro_sheet_error` + retry; `pro_sheet_pending`; `pro_sheet_already`; Switch shows only `pro_sheet_plan_yearly` and
  `pro_sheet_switch`. Table cell reads "Unlimited*" for Pro per day. VM tests: OpenPro sets the sheet and source and
  logs nothing (the paywall logs).
- [ ] **Step 2:** run those tests → FAIL. **Step 3:** implement. **Step 4:** → PASS.

### Task 13: Pro surfaces (limits and counts)

**Files:**
- Modify: `ui/translate/TranslateUiState.kt` (`UsagePillUi.unlimited`, `TranslateBlock.FairUseLimit`),
  `TranslateViewModel.kt`, `TranslateCards.kt` (`FairUseCard`, tag `translate_block_fair_use`, title
  `translate_fair_use_title` "You've reached today's fair-use limit", body `today_resets`, Look up words, no upgrade),
  `TranslateSections.kt`, `TodaySheet.kt` (Pro: title `today_unlimited` "Unlimited today", no button),
  `ui/search/SmartTranslate.kt` + `SmartTranslateViews.kt` (Pro blocked row: `search_translate_fair_use`, not
  clickable; Pro line "Unlimited today"), `ui/home/HomeUiState.kt` + `HomeViewModel.kt` + `HomeScreen.kt`
  (`TranslateCardUi.unlimited`; line `home_translate_unlimited` "%1$s · Unlimited today")
- Test: the matching ViewModel and screen tests

- [ ] **Step 1: Failing tests:** Pro at 300/300 → `TranslateBlock.FairUseLimit` (FREE stays `DailyLimit`); the fair-use
  card has no `translate_upgrade`; Pro pill `unlimited = true`, `low = false` until 0 left; Today sheet for Pro shows
  "Unlimited today" and no button; Search Pro blocked row text and no `SmartUpgrade` on tap; Search Pro line
  "Unlimited today"; Home card for Pro "… · Unlimited today"; `showProHint` stays false for Pro.
- [ ] **Step 2:** run them → FAIL. **Step 3:** implement. **Step 4:** → PASS.

### Task 14: Profile Pro card, switch, delete note

**Files:**
- Modify: `ui/profile/ProfileUiState.kt` (`PlanCardUi.Pro`, `ProfileEvent.ManageSubscription`, `SwitchToYearly`),
  `ProfileViewModel.kt` (inject `BillingRepository`; `planCard` takes the entitlement and availability),
  `ProfileAccount.kt` (Pro card, tags `profile_plan_pro`, `profile_plan_manage`, `profile_plan_switch`,
  `profile_plan_billing_issue`; delete dialog note tag `profile_delete_pro_note` with a Manage link)
- Strings: `profile_plan_pro` ("Pro · %1$s"), `profile_plan_pro_plain` ("Pro"), `profile_plan_renews` ("Renews %1$s"),
  `profile_plan_ends` ("Ends %1$s"), `profile_plan_pro_usage` ("Unlimited translations · %1$s characters each"),
  `profile_plan_fair_use` ("Fair use: %1$s of %2$s today"), `profile_plan_billing_issue` ("Payment problem · Fix in
  Google Play"), `profile_plan_manage`, `profile_plan_switch`, `delete_account_pro_note` ("Your Pro subscription keeps
  renewing until you cancel it in Google Play.")
- Test: `ProfileViewModelTest.kt`, `ProfileScreenTest.kt`

**Interfaces:**
- Produces: `PlanCardUi.Pro(val plan: ProPlan?, val renewsOn: LocalDate?, val willRenew: Boolean, val billingIssue: Boolean,
  val used: Int, val limit: Int, val maxChars: Int, val canSwitch: Boolean)`; `ProfileUiState.proNoteOnDelete: Boolean`.
  `planCard` shows Pro whenever the server quota plan is PRO; entitlement fields fill in when the SDK has them.
  `canSwitch = billingEnabled && billing.available && entitlement.plan == MONTHLY && entitlement.active`.

- [ ] **Step 1: Failing tests:** server PRO + yearly renewing entitlement → "Pro · Yearly", "Renews {date}", no switch;
  monthly → switch shown only with billing on; `willRenew = false` → "Ends {date}"; billing issue → warn line; used
  255/300 (85%) → fair-use line instead of the unlimited line; server PRO with no entitlement → "Pro", no date, Manage
  opens the generic URL; Manage emits `OpenUrl(manage URL with sku)`; SwitchToYearly sets `paywall = SWITCH`; delete
  dialog shows the note only for an active renewing entitlement.
- [ ] **Step 2:** run them → FAIL. **Step 3:** implement. **Step 4:** → PASS.

### Task 15: Whole-app gate, docs, store wiring left to the owner

**Files:**
- Modify: `CLAUDE.md` (app) if it lists data packages or dependencies; the spec's status line and store facts
- Memory: `subscriptions-billing-run.md`

- [ ] **Step 1:** `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleRelease` (R8 with RevenueCat's
  consumer rules) → all pass. Check `grep -rl "com.revenuecat" app/src/main` lists only `RevenueCatBillingRepository.kt`
  and `BillingSetup.kt`.
- [ ] **Step 2:** backend full gate from Task 6 Step 6 again.
- [ ] **Step 3:** hand the owner the credential steps (service account and key, Play invite, secrets, SDK key,
  GitHub secret), then, once credentials are in: RTDN topic in Play, RevenueCat webhook + test event, deploy (owner's
  go), internal-track build, `billing.enabled = true` (owner's go), the spec's end-to-end list.
