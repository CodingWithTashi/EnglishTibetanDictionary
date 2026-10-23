# Pro launch checklist

The code for Pro is built and tested, on the server and in the app: the monthly and yearly subscriptions through
RevenueCat, the server's per-user Pro record (webhook and `syncSubscription`), the paywall switch
`config/translation.billing.enabled`, the paywall sheet, and Pro's limits across Translate, Search, Home and Profile.
Nothing is deployed and the switch is off. What is left needs credentials or the owner's go. Work through it in
order: steps 1–4 set up the credentials, 5 deploys, 6–7 build and test, 8 turns Pro on.

Design: `docs/superpowers/specs/2026-10-23-subscriptions-design.md`. The backend's contract and runbook (steps 8–10)
are in `backend/README.md`.

Already done in the consoles (2026-10-23): renewing base plans `monthly` and `yearly` (active, prices copied from the
prepaid plans and checked), the prepaid plans deactivated, the subscriptions renamed; in RevenueCat the Play app,
both products, entitlement `pro`, offering `default` with `$rc_monthly` and `$rc_annual`, and restore behaviour
"Transfer".

## 1. Google Cloud service account for RevenueCat

Run with `!` in Claude Code, or in a terminal signed in to gcloud as the project owner. It enables the APIs RevenueCat
uses, creates the account with the two roles RevenueCat asks for, and writes a JSON key to your home folder.

```bash
P=englishtibetandictionary
SA=revenuecat-play@$P.iam.gserviceaccount.com
gcloud services enable androidpublisher.googleapis.com playdeveloperreporting.googleapis.com pubsub.googleapis.com --project $P
gcloud iam service-accounts create revenuecat-play --project $P --display-name "RevenueCat Play"
gcloud projects add-iam-policy-binding $P --member "serviceAccount:$SA" --role roles/pubsub.admin
gcloud projects add-iam-policy-binding $P --member "serviceAccount:$SA" --role roles/monitoring.viewer
gcloud iam service-accounts keys create ~/revenuecat-play.json --iam-account $SA --project $P
```

- [ ] Play Console → Users and permissions → Invite new users: `revenuecat-play@englishtibetandictionary.iam.gserviceaccount.com`,
      for this app only, with **View app information**, **View financial data** and **Manage orders and
      subscriptions**.
- [ ] RevenueCat → the Play app (`app4468d671e5`) → Service account credentials JSON: upload
      `~/revenuecat-play.json`, then delete the file. Never commit it. Google can take up to a day before RevenueCat
      shows the credentials as valid.

## 2. Real-time developer notifications

- [ ] RevenueCat → the Play app → Google developer notifications → **Connect to Google**. It creates a Pub/Sub topic
      and shows its name.
- [ ] Play Console → Monetize with Play → Monetization setup → Real-time developer notifications: enable, paste the
      topic name, Save, then **Send test notification**; RevenueCat shows it as received.
- [ ] Play Console → Settings → License testing: your Google account is a license tester (test renewals then take
      minutes).

## 3. Server secrets and the webhook

- [ ] RevenueCat → Project settings → API keys → **+ New secret API key**, version **v1**. Then, from `backend/`:
      `firebase functions:secrets:set REVENUECAT_API_KEY --project englishtibetandictionary` and paste it.
- [ ] `openssl rand -base64 32`, then `firebase functions:secrets:set REVENUECAT_WEBHOOK_AUTH --project
      englishtibetandictionary` with that value. Keep it for step 5's webhook form; don't paste it anywhere else.

## 4. The app's SDK key

RevenueCat → Project settings → API keys → the **public** Google Play key (starts with `goog_`).

- [ ] Local builds: add `revenuecat.androidKey=goog_…` to `local.properties` (git-ignored).
- [ ] Release builds: GitHub → Settings → Secrets and variables → Actions → `REVENUECAT_ANDROID_KEY`.
      `release.yml` already passes it to the build.

A build without the key works, but the paywall stays the "Coming soon" sheet.

## 5. Deploy the backend (your go)

- [ ] From `backend/`: `firebase deploy --only functions --project englishtibetandictionary`. It adds
      `syncSubscription` and `revenuecatWebhook` and updates the others; `billing.enabled` stays off, so nothing
      changes for users. Rules changed only in a comment, so functions are enough.
- [ ] RevenueCat → Integrations → Webhooks → Add: URL
      `https://asia-south1-englishtibetandictionary.cloudfunctions.net/revenuecatWebhook`, events from production
      and sandbox, Authorization header = the `REVENUECAT_WEBHOOK_AUTH` value. **Send test event** must show 200.

## 6. Internal-testing build

- [ ] Run the Release workflow to the internal track (with `REVENUECAT_ANDROID_KEY` set) and install it on the Pixel 8
      from the Play internal-testing link, signed in as the license tester.

## 7. Device pass (switch on for testers)

Turn the switch on first (step 8): only builds with the paywall read it, so production users see no change until
they update. For each step, check RevenueCat's customer page, the `revenuecatWebhook` logs, and the Firestore doc
`subscriptions/{uid}`.

- [ ] 1. Profile → Upgrade: the sheet shows Yearly (preselected, with its price per month) and Monthly, Play's prices.
      Buy monthly → "Welcome to Pro"; Translate's pill says Pro and the Today sheet "Unlimited today".
      Rotate the phone (or switch dark mode) while Play's payment sheet is up: after paying, the app still says
      "Welcome to Pro" and never shows the plans again.
- [ ] 2. Wait for an accelerated renewal: a RENEWAL event and a later `expiresAt`.
- [ ] 3. Profile → Switch to yearly: PRODUCT_CHANGE, `product = yearly`, and the card says "Pro · Yearly".
- [ ] 4. Cancel in Google Play: `willRenew = false`, and the card says "Ends …".
- [ ] 5. Let it expire: EXPIRATION, and free limits return.
- [ ] 6. Reinstall, use the app as a guest for a minute (close and reopen it once), then sign in with Google: Pro
      returns by itself, and RevenueCat's customer history shows no TRANSFER to an anonymous id. (The app only starts
      RevenueCat for a Google user, so a guest never claims the Play purchase. If it doesn't return, Upgrade → Restore
      purchase, and tell Claude.)
- [ ] 7. Restore while signed in to a second Google account: TRANSFER, and the first account drops to free.
- [ ] 8. Switch off: Upgrade shows "Coming soon", and a subscriber keeps Pro.
- [ ] Copy a real sandbox `GET /v1/subscribers/{uid}` response from step 1 over
      `backend/functions/test/fixtures/revenuecat/subscriber-active-monthly.json` (it is the documentation sample now)
      and rerun the backend tests.

## 8. Turn Pro on (your go)

- [ ] Firestore → `config/translation` → add `billing` as a **map** with `enabled` as a **boolean** `true` (a field
      added in the console is a string unless you pick the type; a string keeps the paywall off). It takes effect
      within a minute. Turning it off later hides new purchases only; subscribers keep Pro.

## 9. Privacy policy and Data safety

- [ ] Privacy policy: purchases are processed by Google Play and RevenueCat; RevenueCat keeps the app account id
      and the purchase history.
- [ ] Play Console → App content → Data safety: purchase history is collected (to provide the subscription), not
      shared for advertising.

## 10. Tibetan strings for review

Every string Pro added, with the English and the drafted Tibetan. Review each in
`app/src/main/res/values-bo/strings.xml`. `%1$s`, `%2$s` are numbers already formatted (Tibetan digits in the Tibetan
UI), Play's formatted prices, dates, or the plan or language names.

| Key | English | Tibetan (draft) |
|---|---|---|
| `pro_sign_in_to_continue` | Sign in to continue | མུ་མཐུད་པར་ནང་འཛུལ་གནང་། |
| `pro_yearly` | Yearly | ལོ་རེར། |
| `pro_monthly` | Monthly | ཟླ་རེར། |
| `pro_per_month` | %1$s / month | %1$s / ཟླ་རེ། |
| `pro_terms` | Renews automatically · Cancel any time in Google Play | རང་འགུལ་གྱིས་བསྐྱར་གསོ་འགྲོ། · Google Play ནང་དུས་ནམ་ཡིན་ཡང་འདོར་ཆོག |
| `pro_restore` | Restore purchase | ཉོས་ཟིན་པ་སླར་གསོ། |
| `pro_fair_use_note` | *Fair use: up to %1$s a day | *འོས་འཚམས་བེད་སྤྱོད། ཉིན་རེར་ %1$s བར། |
| `pro_error` | Couldn't reach Google Play. Check your connection and try again. | Google Play དང་འབྲེལ་མ་ཐུབ། དྲ་རྒྱ་ལ་ཞིབ་བཤེར་གནང་ནས་ཡང་བསྐྱར་ཚོད་ལྟ་གནང་། |
| `pro_pending` | Payment pending. Pro starts when Google Play confirms it. | དངུལ་སྤྲོད་སྒུག་བཞིན་པ། Google Play ཡིས་གཏན་འཁེལ་བྱས་རྗེས་ Pro འགོ་འཛུགས། |
| `pro_already` | Pro is active on this Google Play account | Google Play རྩིས་ཐོ་འདིར་ Pro ནུས་ལྡན་ཡིན། |
| `pro_switch_title` | Switch to yearly | ལོ་རེའི་འཆར་གཞིར་སྤོ། |
| `pro_switch_button` | Switch to yearly | ལོ་རེའི་འཆར་གཞིར་སྤོ། |
| `pro_switch_note` | The time left on your monthly plan is credited to the year. | ཟླ་རེའི་འཆར་གཞིའི་དུས་ཚོད་ལྷག་མ་ལོ་རེའི་ནང་བསྣན་འགྲོ། |
| `pro_welcome` | Welcome to Pro | Pro ལ་ཕེབས་པར་དགའ་བསུ་ཞུ། |
| `pro_restored` | Pro restored | Pro སླར་གསོ་བྱས་ཟིན། |
| `pro_restore_none` | No Pro purchase on this Google Play account | Google Play རྩིས་ཐོ་འདིར་ Pro ཉོས་པ་མི་འདུག |
| `today_unlimited` | Unlimited today | དེ་རིང་ཚད་མེད། |
| `translate_fair_use_title` | You've reached today's fair-use limit | དེ་རིང་གི་འོས་འཚམས་བེད་སྤྱོད་ཚད་ལ་སླེབས་ཟིན། |
| `search_translate_fair_use` | Today's fair-use limit reached | དེ་རིང་གི་འོས་འཚམས་བེད་སྤྱོད་ཚད་ལ་སླེབས་ཟིན། |
| `home_translate_unlimited` | %1$s · Unlimited today | %1$s · དེ་རིང་ཚད་མེད། |
| `profile_plan_pro` | Pro · %1$s | Pro · %1$s |
| `profile_plan_pro_plain` | Pro | Pro |
| `profile_plan_renews` | Renews %1$s | %1$s ལ་བསྐྱར་གསོ། |
| `profile_plan_ends` | Ends %1$s | %1$s ལ་མཇུག་རྫོགས། |
| `profile_plan_pro_usage` | Unlimited translations · %1$s characters each | ཡིག་སྒྱུར་ཚད་མེད། · ཐེངས་རེར་ཡིག་འབྲུ་ %1$s |
| `profile_plan_fair_use` | Fair use: %1$s of %2$s today | འོས་འཚམས་བེད་སྤྱོད། དེ་རིང་ %2$s ནས་ %1$s |
| `profile_plan_billing_issue` | Payment problem · Fix in Google Play | དངུལ་སྤྲོད་ལ་དཀའ་ངལ། · Google Play ནང་བཅོས། |
| `profile_plan_manage` | Manage subscription | ཞུ་འབུལ་དོ་དམ། |
| `profile_plan_switch` | Switch to yearly | ལོ་རེའི་འཆར་གཞིར་སྤོ། |
| `delete_account_pro_note` | Your Pro subscription keeps renewing until you cancel it in Google Play. | Google Play ནང་མ་བཏང་བར་དུ་ཁྱེད་ཀྱི་ Pro ཞུ་འབུལ་བསྐྱར་གསོ་རྒྱུན་མཐུད་འགྲོ། |
