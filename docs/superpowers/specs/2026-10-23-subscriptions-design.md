# Pro Subscriptions: Design

**Status:** approved by the owner, 2026-10-23; built 2026-10-23, not deployed (owner steps: `docs/pro-launch-checklist.md`) · **Plan:** `docs/superpowers/plans/2026-10-23-subscriptions.md` · **Builds on:** `docs/superpowers/specs/2026-10-22-translation-app-integration-design.md`,
`docs/superpowers/specs/2026-10-22-translation-backend-design.md` · **UI source:** `docs/tibetan-dictionary-ui-revamp/project/Translate & Dictionary.dc.html` (1za)

## Context

Translation is live with three plans on the server: guest (3 a day), free (Google sign-in, 10 a day, 100 characters)
and pro (300 a day, 500 characters, in `config/translation.pro`). Nobody can reach Pro yet:
`backend/functions/src/service/plan.ts` returns `guest` or `free` only. In the app, "Upgrade to Pro" opens the 1za
sheet as a fake door, with a disabled "Coming soon" button that logs `pro_interest`.

This phase sells Pro as two Google Play subscriptions through RevenueCat. The server, not the app, decides who is Pro,
from a per-user record that a RevenueCat webhook keeps current.

**State found on 2026-10-23 (read-only):**

| Where | State |
|---|---|
| Play Console | Two subscriptions. `dictionary_pro` (display name "dictionary_pro_monthly") has base plan `dictionary-pro-monthly`. `dictionary_pro_annually` ("Dictionary Pro Annually") has base plan `dictionary-pro-annually`. **Both base plans are prepaid**: they never renew and can't carry offers. Prices: monthly INR 49 / CAD 2.99 (USD 2.09); yearly INR 399 / CAD 19.99 (USD 13.99); 175 regions each. Real-time developer notifications are off. Subscription pause is on. |
| RevenueCat | Project `232d3082` ("Tibetan Dictionary"). Only the Test Store: no Play app, products, entitlements, offerings or webhook. Webhooks are included in the account's plan. |
| Backend | `resolvePlan` is the seam. `config/translation.pro` exists. No subscription data. |
| App | `com.android.billingclient:billing-ktx:8.0.0` is a dependency only so Play Console would allow subscriptions. No purchase code. |

**Owner decisions (2026-10-23):**
- **Two subscriptions**, one renewing monthly and one renewing yearly. The prices and periods the owner set are
  correct and stay as they are. Other configuration is updated where needed.
- **"Backend flag" means both:**
  - a per-user Pro record on the server, written from RevenueCat, which the server trusts for limits;
  - a remote switch that turns the real paywall on. While it is off, the app keeps today's "Coming soon" sheet.
- **Pro is shown as "Unlimited" with a fair-use note.** The server keeps its daily cap (300, from config).
- Claude does the console configuration in Chrome. Steps that handle credentials stay with the owner.
- Never commit or push unless asked. No AI attribution anywhere. Deploys, live config changes and billable calls
  only on the owner's explicit go.

**Defaults taken in this spec (owner may overturn):**
- Buying needs Google sign-in.
- No free trial at launch.
- RevenueCat's default restore behaviour, "transfer to new App User ID", stays as it is.

## Product rules

### Plans

| Plan | Who | Daily limit | Characters | At the limit |
|---|---|---|---|---|
| Guest | Anonymous Firebase user | 3 | 100 | Sign-in card (unchanged) |
| Free | Signed in with Google | 10 | 100 | Limit card with **Upgrade to Pro**, which opens the paywall when the switch is on and the fake door when it is off |
| **Pro** | Signed in with Google, with an active `pro` entitlement | 300 (`config/translation.pro.dailyLimit`), shown as "Unlimited" | 500 | Fair-use card: "You've reached today's fair-use limit · Resets at midnight". It has no upgrade button. |

### Products

| Plan | Play subscription | Base plan (new) | RevenueCat product | RevenueCat package |
|---|---|---|---|---|
| Monthly | `dictionary_pro` | `monthly`, auto-renewing, 1 month | `dictionary_pro:monthly` | `$rc_monthly` |
| Yearly | `dictionary_pro_annually` | `yearly`, auto-renewing, 1 year | `dictionary_pro_annually:yearly` | `$rc_annual` |

- Both products unlock the RevenueCat entitlement **`pro`**. Both packages are in the offering **`default`**.
- Prices are copied country by country from the prepaid plans. The app always shows Play's localised price. It never
  hardcodes a price.

### Who can buy

- Only a user signed in with Google. Pro belongs to the app account (the Firebase uid), and the uid is the RevenueCat
  App User ID.
- A guest who reaches the paywall sees "Sign in to continue". Google sign-in runs, and then the same sheet shows the
  plans.
- A Pro user never sees the purchase paywall, so nobody can end up paying for two subscriptions. A monthly subscriber
  can **switch to yearly** from Profile (see Plan switching).

### Remote switch

- `config/translation.billing.enabled` is a boolean that defaults to `false`.
- A value that is present but not a boolean counts as `false`, so the paywall stays hidden.
- `getTranslationStatus` returns it as `billingEnabled`.
- **Off:**
  - Upgrade opens the existing fake door and logs `pro_interest`.
  - "Switch to yearly" is hidden.
  - Existing subscribers keep Pro, the Pro card and "Manage subscription". The switch never takes away what someone
    paid for.
- **On:** Upgrade opens the paywall, provided the app build has a RevenueCat key. A build without one keeps the
  fake door.

### Plan switching

- Monthly to yearly is offered in Profile's Pro card as "Switch to yearly".
- It is a purchase of the yearly package with `oldProductId = "dictionary_pro"` and replacement mode
  `WITH_TIME_PRORATION`:
  - the switch takes effect at once;
  - the unused monthly time is credited as yearly time;
  - Play charges the yearly price when that credit runs out.
- Yearly to monthly is not offered in the app. The user can cancel in Google Play and subscribe monthly once the year
  ends.

### Sign-out, account switch, deletion

- **Sign-out:** `Purchases.logOut()`. Pro stays with the Google account and returns when the user signs in again.
- **Restore on another app account:** RevenueCat transfers the purchase to the account doing the restore (its default
  restore behaviour). The webhook refreshes both sides, so the old account drops to free.
- **Delete account:**
  - deletes `subscriptions/{uid}` along with the usage docs;
  - does **not** cancel the Play subscription. The confirm dialog tells a Pro user so and links to Manage subscription;
  - leaves the RevenueCat customer record in place (Known limits).

## Store setup

### Play Console (Claude, in Chrome)

1. **`dictionary_pro`:**
   - Add base plan `monthly`: auto-renewing, 1 month.
   - Copy the prices of `dictionary-pro-monthly`. That means the base price, plus the INR 49 override and any other
     override found.
   - Keep Play's default grace period, account hold and resubscribe settings, and record the values used.
   - Activate it.
2. **`dictionary_pro_annually`:** the same steps for base plan `yearly`, copying `dictionary-pro-annually`.
3. **Verify the prices:** export the price list of each new plan and of its prepaid plan from the pages, and diff them
   with a script. Every region must match before going on.
4. **Retire the prepaid plans:** deactivate `dictionary-pro-monthly` and `dictionary-pro-annually`.
   - This is safe because nobody has bought them; there has never been purchase code in the app.
   - An activated base plan can't be deleted, so they stay as inactive.
5. **Rename the subscriptions:** users see these names in Google Play's subscription centre. "dictionary_pro_monthly"
   becomes "Dictionary Pro (Monthly)", and "Dictionary Pro Annually" becomes "Dictionary Pro (Yearly)". The product
   IDs can't change and stay as they are.
6. **Real-time developer notifications:** Monetization setup → enable, with the Pub/Sub topic RevenueCat shows once
   its Google credentials are in place.
7. **License testing:** check that the owner's Google account is a license tester. Test renewals are accelerated, so
   a monthly period lasts minutes.

**Done 2026-10-23 (Claude, in Chrome):** steps 1–5. `monthly`: grace period 7 days, account hold 53 days.
`yearly`: grace period 14 days, account hold 46 days. Both: resubscribe allowed, "charge at next billing date" for
price changes, backwards compatible, active in 175 regions. The price diff matched all 176 rows. Steps 6–7 wait for
RevenueCat's credentials.

### Google Cloud and Play access for RevenueCat (owner, because it creates credentials)

- Service account `revenuecat-play` in project `englishtibetandictionary`, with Pub/Sub Admin and Monitoring Viewer,
  as RevenueCat's Play setup asks.
- Invite it in Play Console under Users and permissions with these permissions:
  - View app information;
  - View financial data;
  - Manage orders and subscriptions.
- Create a JSON key and upload it in RevenueCat. Google can take up to about a day before the credentials work.
- The plan gives the exact `gcloud` lines as one short script to run with `!`.

### RevenueCat (Claude in Chrome, except the credential fields)

1. Apps → New app configuration → Google Play, with package `com.kharagedition.tibetandictionary`. The owner
   uploads the JSON key, then Claude runs RevenueCat's "Connect to Google" for the notification topic.
2. Products: import `dictionary_pro:monthly` and `dictionary_pro_annually:yearly`.
3. Entitlement `pro` ("Pro"), with both products attached.
4. Offering `default`, made current, with packages `$rc_monthly` → monthly and `$rc_annual` → yearly.
5. API keys:
   - the **public Android SDK key** goes in the app build (owner: `local.properties` and a GitHub secret);
   - a **v1 secret key** for the server goes into Secret Manager (owner).
6. Integrations → Webhooks:
   - URL `https://asia-south1-englishtibetandictionary.cloudfunctions.net/revenuecatWebhook`;
   - events from both production and sandbox;
   - the owner pastes the Authorization value, the same value stored as `REVENUECAT_WEBHOOK_AUTH`.
7. Check that Project settings → Restore behaviour is "Transfer to new App User ID".
8. Send a test event. The function must answer 200.

**Done 2026-10-23:** steps 1 (app `app4468d671e5`, no credentials yet), 2, 3, 4 and 7. Step 1's "Connect to
Google", 5, 6 and 8 wait for the owner's credentials and the deploy.

## Backend changes (`backend/functions`)

### Data: `subscriptions/{uid}`

| Field | Type | Meaning |
|---|---|---|
| `active` | boolean | `expiresAt` is after RevenueCat's `request_date_ms`. v1 also lists expired entitlements, which give `false`. |
| `expiresAt` | timestamp | The later of the entitlement's `expires_date` and `grace_period_expires_date` |
| `product` | `"monthly"` \| `"yearly"` \| null | From the subscription id: `dictionary_pro` → monthly, `dictionary_pro_annually` → yearly. The match is on the exact id before `:`. |
| `willRenew` | boolean | The subscription has no `unsubscribe_detected_at` |
| `billingIssue` | boolean | The subscription has a `billing_issues_detected_at` |
| `sandbox` | boolean | `is_sandbox` |
| `store` | string | `play_store`, as RevenueCat reports it |
| `fetchedAt` | timestamp | RevenueCat's `request_date_ms` for the answer this record came from |
| `updatedAt` | timestamp | Server write time |
| `source` | `"webhook"` \| `"sync"` | Which path wrote it |

- Clients have no access. `firestore.rules` already denies everything except `Message` creates, and a rules test pins
  that for `subscriptions` too.
- No TTL: the record lives as long as the account.
- `deleteAccount` deletes it.

### Refresh: the one write path

`refreshSubscription(uid, source)`:

1. `GET https://api.revenuecat.com/v1/subscribers/{uid}` with the v1 secret key.
   - Timeout 10 s.
   - One retry on a network error, 429 or 5xx.
   - The uid is URL-encoded.
2. Parse defensively with zod. Read `subscriber.entitlements.pro` and the matching entry of `subscriber.subscriptions`.
   An unknown shape is an error and is never treated as "not Pro".
3. If there is no `pro` entitlement **and no record exists**, write nothing, so users who never paid get no doc.
   Otherwise build the record.
4. Write in a transaction **only if `fetchedAt` is newer than the stored one**. A slow, stale read can never overwrite
   a newer one.

The v1 endpoint creates an empty RevenueCat customer when the uid is unknown. That is harmless, because it is called
only for uids that bought, restored, or that the app thinks are Pro.

**Why refresh instead of applying the event:**
- RevenueCat documents duplicate deliveries.
- Cancellations can arrive about 2 hours late.
- TRANSFER and PRODUCT_CHANGE need careful handling to apply by hand.

Re-reading the customer turns every event into the same idempotent operation. A forged webhook can at most cause a
refresh: it can never grant Pro.

### `revenuecatWebhook` (HTTPS, `onRequest`)

- Region asia-south1, `invoker: "public"`, maxInstances 5, timeout 30 s.
- Secrets `REVENUECAT_WEBHOOK_AUTH` and `REVENUECAT_API_KEY`.
- Runs as the `translation-fn` service account.
- No App Check: RevenueCat's servers call it.

| Step | Result |
|---|---|
| Method is not POST | 405 |
| `Authorization` header is not exactly the secret (timing-safe compare) | 401, logged without the header |
| Body doesn't parse as `{ event: { id, type, … } }` | 400 |
| `type = TEST` | 200, logged, no refresh |
| Otherwise | Collect the uids from `app_user_id`, `original_app_user_id`, `aliases`, `transferred_from` and `transferred_to`. Drop `$RCAnonymousID:…` ids and anything that isn't a plausible Firebase uid (1–128 characters, no `/`). Cap at 10 uids. Refresh each one. |
| Every refresh succeeded | 200 |
| Any refresh failed | 500, so RevenueCat retries (5 times over about 80 minutes). A retry repeats the same idempotent refreshes. |

The log records event `id`, `type`, `environment`, the uid count and the outcome. It records no email, price or
store token.

### `syncSubscription` (callable, App Check enforced)

- Called by the app right after a purchase, restore or plan switch, and when the app's RevenueCat state says Pro but
  the server says free.
- Guests are refused with `failed-precondition` / `guest_account`.
- Rate-limited per uid to 6 a minute. Over the limit: `resource-exhausted` / `rate_limited`.
- Runs `refreshSubscription(uid, "sync")` and returns `{ plan }`.
- If RevenueCat can't be reached: `unavailable` / `billing_unavailable`. The webhook still catches up.

### Plan resolution

`createPlanResolver({ subscriptions, now, log })` replaces the constant `resolvePlan`:

- guest → `guest`;
- otherwise read `subscriptions/{uid}`. Pro when `active && expiresAt + 15 min > now`. A missing `expiresAt` is
  never Pro, because only subscriptions are sold, never lifetime purchases;
- otherwise `free`;
- if the read fails: log at ERROR and return `free`. A Pro user then only notices if they're past 10 that day.

The 15-minute leeway covers the gap between a period ending and its RENEWAL webhook. Because expiry is checked on
read, Pro ends on time even if the EXPIRATION webhook never arrives.

### Config and status

- `runtimeConfig`: a new `billing: { enabled: boolean }` with default `false`. It is checked field by field like the
  rest, and a non-boolean `enabled` fails closed to `false`.
- `getTranslationStatus` adds:
  - `billingEnabled`;
  - `proLimit`, the Pro daily limit, for the fair-use note;
  - `proMaxChars`, for the comparison table.
- The `daily_limit` details already carry `plan`. The app uses `plan: "pro"` to choose the fair-use card.

### Parameters and secrets

| Name | Kind | Holder |
|---|---|---|
| `REVENUECAT_API_KEY` | Secret Manager | v1 secret key. The owner sets it with `firebase functions:secrets:set`. |
| `REVENUECAT_WEBHOOK_AUTH` | Secret Manager | A random value of at least 32 bytes, made with `openssl rand`. The owner sets it and pastes the same value into RevenueCat. |

`translation-fn` gets access to both through the deploy's secret binding. No new IAM role is needed.

### Units

| File | Responsibility |
|---|---|
| `src/billing/revenueCat.ts` | v1 subscriber fetch and zod parse into a `ProRecord` or `null`. Pure mapping functions, tested with fixtures. |
| `src/store/subscriptionStore.ts` | `get(uid)`, `putIfNewer(uid, record)` (transaction on `fetchedAt`), `delete(uid)` |
| `src/service/subscriptionRefresh.ts` | `refreshSubscription`, shared by the webhook and the callable |
| `src/service/webhookHandler.ts` | The request → response table above, with no firebase imports in its logic |
| `src/service/syncHandler.ts` | The callable |
| `src/service/plan.ts` | `createPlanResolver` |
| `src/index.ts` | Wiring: two new exports, secrets, options |

Docs: `README.md` gets the new functions, data, errors, config field, runbook and known limits.
`functions/architecture.md` and `backend/CLAUDE.md` get the webhook flow.

## App changes

### Units

| Unit | Responsibility |
|---|---|
| `data/billing/BillingModels.kt` | Pure Kotlin models, described below the table. |
| `data/billing/BillingRepository.kt` | Interface, described below the table. |
| `data/billing/RevenueCatBillingRepository.kt` | **The only file that imports `com.revenuecat`.** Maps SDK types to the models with small pure functions. A purchase that comes back already owned runs a restore. |
| `data/billing/BillingSetup.kt` | Configures `Purchases` once in `DictionaryApplication`. It needs a non-blank key and a Firebase app, the same guard as today. Starts with the current Google uid, or anonymous. |
| `data/billing/BillingIdentitySync.kt` | Started by `AppInitializer`. Follows `AuthRepository.account`: Google → `identify(uid)`; guest or none → `identify(null)`. |
| `data/billing/ProReconciler.kt` | When RevenueCat says active but the server's quota plan is `FREE` for a signed-in user, calls `syncSubscription` at most once every 5 minutes, then forces a status refresh. This covers webhooks that never arrive. |
| `data/translation` | `TranslationApi.syncSubscription()`; `StatusReply` and `TranslationAvailability` gain `billingEnabled`, `proLimit` and `proMaxChars`; `TranslationRefusal.BillingUnavailable`. |
| `ui/paywall/PaywallViewModel.kt` | One ViewModel for the sheet wherever it opens (Translate, Search, Today sheet, Profile), so no screen repeats purchase logic. |
| `ui/components/ProSheet.kt` | Renders the states below. The fake door stays as one of them. |
| `ui/profile/*` | `PlanCardUi.Pro`, the delete-dialog note, Switch to yearly |
| `ui/translate/*`, `ui/search/*`, `ui/home/*` | No upgrade prompts for Pro. The fair-use limit card. "Unlimited today" in place of "N left". |

`BillingModels.kt` holds:
- `ProPlan { MONTHLY, YEARLY }`;
- `ProOffer(plan, price, pricePerMonth?)`: formatted strings from Play;
- `ProOffers(monthly?, yearly?)`;
- `ProEntitlement(active, plan?, expiresAt?, willRenew, billingIssue)`;
- `PurchaseOutcome { Purchased, Cancelled, Pending, Failed(BillingError) }`;
- `BillingError { NETWORK, STORE, NOT_ALLOWED, UNAVAILABLE, UNKNOWN }`.

`BillingRepository` exposes:
- `available`, which is false without a key;
- `entitlement: StateFlow<ProEntitlement?>`;
- `offers()`;
- `purchase(activity, plan, switchFrom?)`;
- `restore()`;
- `identify(uid?)`.

### Paywall (1za) states

| State | When | Shows |
|---|---|---|
| `FakeDoor` | The switch is off, or the build has no RevenueCat key | Today's sheet, unchanged ("Coming soon", `pro_interest`) |
| `NeedsSignIn` | Guest or no user | Table, then "Sign in to continue". After sign-in, goes to `Loading`. |
| `Loading` | Fetching offerings | Table, then a skeleton for the plan rows |
| `Offers` | Offerings loaded | Table, then the plan rows, the button and the terms line, described below |
| `Working` | Purchase or restore running | Button shows progress. The sheet can't be dismissed by a swipe. |
| `Error` | Offerings or purchase failed (not cancelled) | Inline message and Retry |
| `Pending` | Play reports a pending payment | "Payment pending. Pro starts when Google Play confirms it." |
| `AlreadyPro` | RevenueCat says active but the server still says free, for example before the webhook lands | "Pro is active on this Google Play account". It runs `syncSubscription`, then closes. No purchase button. |
| `Switch` | Opened from "Switch to yearly" | Only the Yearly row, the button "Switch to yearly", and a line saying the remaining monthly time is credited |

The `Offers` state shows:
- **Yearly** row, preselected: the price and "{price per month} / month", both from Play.
- **Monthly** row: the price.
- The **Continue** button.
- The terms line "Renews automatically · Cancel any time in Google Play · **Restore purchase**", where Restore
  purchase is a link.

The comparison table reads Per day: Free 10 / Pro **Unlimited\***. Under it, the note "\*Fair use: up to {proLimit}
a day", with the number from status (300). Characters: Free 100 / Pro {proMaxChars} (500).

### Purchase flow

1. Continue → `purchase_start{plan, source}` → `billing.purchase(activity, plan)`.
2. **Purchased:**
   - `syncSubscription()`, then `translation.refresh(force = true)`;
   - the sheet closes with the toast "Welcome to Pro";
   - `purchase_result{outcome=success}`;
   - if the sync fails, the sheet still closes. `ProReconciler` retries, and the webhook covers it too.
3. **Cancelled:** stays on `Offers` and logs the result.
4. **Pending:** shows `Pending` and logs the result. Pro starts when the webhook arrives.
5. **Failed:** shows `Error` with Retry and logs the result with its `code`.

**Restore purchase:**
1. `billing.restore()`.
2. If active: sync, refresh, and the toast "Pro restored".
3. Otherwise the toast "No Pro purchase on this Google Play account".

RevenueCat acknowledges Play purchases itself, so nothing is refunded for being unacknowledged.

### Pro surfaces

- **Profile plan card, `PlanCardUi.Pro`:**
  - "Pro · Yearly" (or Monthly), then "Renews {date}" or "Ends {date}", from the SDK entitlement;
  - the line "Unlimited translations · {proMaxChars} characters each". At 85% or more of the fair-use limit it reads
    "Fair use: {used} of {limit} today";
  - with a billing issue, a warn-coloured "Payment problem · Fix in Google Play";
  - buttons: **Manage subscription**, which opens
    `https://play.google.com/store/account/subscriptions?sku={subscriptionId}&package=com.kharagedition.tibetandictionary`,
    and **Switch to yearly** (monthly, switch on).
- **Today sheet and Home card:** "Unlimited today" in place of "N left".
- **Translate:** no "Need longer text?" line. The Pro limit card is the fair-use card.
- **Delete-account dialog:** for an active, renewing Pro user, add "Your Pro subscription keeps renewing until you
  cancel it in Google Play" and the Manage subscription link.

### Analytics (no user text, prices or ids)

| Event | Params |
|---|---|
| `paywall_view` | `source`, `mode` (offers, switch, fake_door, sign_in) |
| `purchase_start` | `plan`, `source` |
| `purchase_result` | `plan`, `outcome` (success, cancelled, pending, error), `code?`, `switch` |
| `restore_result` | `outcome` (restored, none, error) |

`pro_interest` stays for the fake door.

### Build and dependencies

- Add `com.revenuecat.purchases:purchases`, pinned in `gradle/libs.versions.toml`.
  - Use the newest 10.x release that is at least two weeks old.
  - Before pinning, check that it uses Play Billing Library 8 or newer, which Play requires for updates in 2026.
- **Remove `billing-ktx`.** RevenueCat brings its own Billing Library and the BILLING permission. Two declared
  versions could clash.
- `REVENUECAT_ANDROID_KEY` becomes a `buildConfigField`:
  - read from the environment, else from `local.properties` (`revenuecat.androidKey`), else blank;
  - blank → `available = false` → fake door. Debug, CI and Robolectric builds work without it;
  - `release.yml` passes the GitHub secret `REVENUECAT_ANDROID_KEY`.
- ProGuard: RevenueCat ships consumer rules. Check the release R8 build in CI.

### Strings

New keys go in `values` and `values-bo`. Tibetan wording is drafted and listed for the owner to check, as in earlier
phases. No `values-mcc402` overrides are needed.

## Error handling

| Case | Behaviour |
|---|---|
| RevenueCat unreachable when offerings load | `Error` with Retry. Never fall back to hardcoded prices. |
| Purchase succeeded, `syncSubscription` failed | Close as purchased. `ProReconciler` and the webhook bring the server in line. Pro limits apply once the server agrees, which is usually seconds. |
| Webhook delivery fails | RevenueCat retries 5 times. The expiry check bounds a missed EXPIRATION, and `ProReconciler` repairs a missed purchase or RENEWAL. |
| Grace period or billing issue | The entitlement stays active until the grace end (`expiresAt`), and Profile shows the payment problem. |
| Pause | RevenueCat ends the entitlement while the subscription is paused. The refresh records it, and Pro returns when the subscription resumes. |
| Refund or revocation | RevenueCat ends the entitlement. The refresh records it, and Pro ends within the leeway. |
| Subscription read fails in `resolvePlan` | `free` for that request, logged at ERROR |
| The switch is off while users hold Pro | Pro is unaffected. Only new purchases are hidden. |

## Testing

Test-first throughout, in the existing layers.

### Backend unit tests

- `revenueCat` mapping, from fixtures:
  - active monthly;
  - active yearly;
  - cancelled but still active;
  - in grace;
  - billing issue;
  - expired;
  - sandbox;
  - no entitlement;
  - an unknown product.
- The fetch: HTTP 404, 429, 5xx, timeout, and a malformed body.
- The webhook handler: every row of its table, including a transfer refreshing both sides, anonymous ids skipped,
  the uid cap, and no secret or header in the logs.
- The sync handler: guest, rate limit, RevenueCat down.
- The plan resolver: leeway edges, a read failure.
- The `billing` field in the runtime config.
- The new status fields.
- `index.test.ts` pins the new endpoints' options: region, invoker, secrets, service account and maxInstances.

### Backend emulator tests

- `subscriptionStore`: `putIfNewer` ordering, no doc for a never-paid user, delete.
- Pipeline:
  - Pro gets 300 and 500 characters;
  - expired Pro gets the free limits;
  - Pro at its cap is refused with `plan: "pro"`.
- `deleteAccount` removes the subscription doc.
- Rules: clients can't read or write `subscriptions`.
- The callables and webhook through `.run()` and the request handler, with RevenueCat answered by the fetch stub.

### App tests (Robolectric, with fakes of `BillingRepository` and `TranslationApi`)

- `PaywallViewModel`, every state and transition:
  - Yearly preselected;
  - purchase success runs sync, then refresh, then closes;
  - a failed sync still closes;
  - cancelled, pending, error and retry;
  - restore found and restore none;
  - the guest sign-in path;
  - the switch off gives the fake door;
  - switch mode passes `switchFrom`.
- `ProReconciler`: throttling, and that it runs only on a mismatch.
- `BillingIdentitySync`.
- The pure SDK-mapping functions.
- Screens: each `ProSheet` state by tag, the Pro plan card variants, the fair-use limit card, "Unlimited today" on
  Home and the Today sheet, and the delete-dialog note.

### End to end (owner's Pixel 8, internal testing track, license tester)

Each step must show the expected webhook in RevenueCat and Cloud Logging and the right `subscriptions/{uid}` record.

1. Buy monthly. Pro limits apply.
2. Wait for an accelerated renewal: RENEWAL.
3. Switch to yearly: PRODUCT_CHANGE, `product = yearly`.
4. Cancel in Play: `willRenew = false`, and the card says "Ends …".
5. Let it expire: EXPIRATION, then free limits.
6. Reinstall, sign in and restore: Pro returns.
7. Restore while signed in to a second Google account: TRANSFER, and the first account drops to free.
8. With the switch off: the fake door shows, and an existing subscriber keeps Pro.

A real sandbox `GET /subscribers` response from step 1 becomes the backend's fixture, replacing the documentation
sample.

## Rollout

1. **Store setup:** Play, Google Cloud and RevenueCat as above. Credential steps are the owner's.
2. **Backend:**
   - the owner sets both secrets;
   - deploy with the owner's go;
   - RevenueCat "Send test event" → 200.

   `billing.enabled` stays false.
3. **App:** a build with the RevenueCat key goes to the internal testing track.
4. **Switch on:** set `config/translation.billing.enabled = true`, with the owner's go. Only builds that contain the
   paywall react to it, because older versions ignore the field. So it can go on before the production release.
5. **Test:** run the end-to-end list.
6. **Release:** promote to production. Turning the switch off later hides new purchases without affecting
   subscribers.

## Owner tasks

- The Google Cloud service account, its key, the Play invite with the three permissions, and uploading the key to
  RevenueCat.
- Setting `REVENUECAT_API_KEY` and `REVENUECAT_WEBHOOK_AUTH` in Secret Manager, and pasting the webhook value into
  RevenueCat.
- The RevenueCat public Android key in `local.properties` and the GitHub secret `REVENUECAT_ANDROID_KEY`.
- Privacy policy: purchases are processed by Google Play and RevenueCat. RevenueCat stores the app account id and the
  purchase history.
- Play Data safety: purchase history is collected.
- Go for each deploy and for turning the switch on.
- Checking the Tibetan strings.

## Known limits (accepted)

- **Deleting an account doesn't cancel the subscription.** The dialog says so. The RevenueCat customer record (uid and
  purchase history) is kept for financial records. Deleting it while a subscription renews would leave renewals with
  no owner.
- **"Unlimited" has a fair-use cap** (300 a day by default), stated in the note under the table.
- **Pro can last up to 15 minutes past its end** because of the renewal leeway.
- **Pro follows the app account, not the Play account.** Signed out, the device is free. A restore while signed in
  to another account moves Pro there.
- **The prepaid base plans stay in Play as inactive.** Play doesn't delete activated base plans.
- **A webhook that fails all 5 retries is lost.** The expiry check and `ProReconciler` bound the effect.

## Out of scope

- Free trials and intro offers. The paywall shows a trial if one is added to a base plan later, but its wording is
  not designed yet.
- In-app yearly → monthly switching.
- iOS and web purchases.
- Promo codes.
- RevenueCat Paywalls. The paywall is built from the design.
- HMAC webhook signing. It isn't needed, because the payload is never trusted.
- Deleting RevenueCat customers.
- Price experiments.
