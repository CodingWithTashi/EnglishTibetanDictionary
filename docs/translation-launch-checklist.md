# Translation launch checklist

The app side of translation is built: the Translate tab, guest and Google sign-in, quota, smart translate in
search, saved and recent translations, the Home card, Profile's plan card and account deletion, and the Pro fake
door. What is left happens outside the code, in the Firebase and Play consoles, and only the owner can do it. Work
through it in order: sign-in needs steps 1–2, the backend needs steps 3–4 before the app ships, and the device pass
(step 5) needs all four.

Design: `docs/superpowers/specs/2026-10-22-translation-app-integration-design.md`. The backend's contract, config
and runbook are in `backend/README.md`.

## 1. Firebase Auth

- [ ] Firebase console → Authentication → Sign-in method → Add new provider → **Google** → Enable, and pick the
      support email. Anonymous is already on (guests).
- [ ] Firebase console → Project settings → General → Your apps → the Android app → **Add fingerprint**, the SHA-1
      and the SHA-256 of each key:
  - the debug key: `./gradlew signingReport` (variant `debug`);
  - the upload key: Play Console → Test and release → App integrity → App signing → Upload key certificate;
  - the **Play app-signing key**: the same page → App signing key certificate. Release builds that Play installs
    are signed with this one, so without it Google sign-in fails only in production.
- [ ] Re-download `app/google-services.json` from the same page and replace the file. It carries the OAuth web
      client the sign-in sheet needs (`default_web_client_id`).
- [ ] Update the CI copy: the repository secret `GOOGLE_SERVICES_JSON` is that file, base64-encoded
      (`base64 -i app/google-services.json`), set under GitHub → Settings → Secrets and variables → Actions.
      CI fails with an error pointing here when the secret has no OAuth web client.
- [ ] Optional: turn on automatic clean-up of anonymous users. Guests left behind pile up in Authentication:
      after a sign-out, a reinstall, or a sign-in to an account that already exists elsewhere. Clean-up deletes
      anonymous users older than 30 days, but needs the upgrade to Firebase Authentication with Identity Platform
      (Authentication → Settings), which changes Auth's pricing, so check it first.

## 2. App Check

The callables enforce App Check, so a build with no valid token gets `unauthenticated`. Don't turn on App Check
enforcement for Cloud Firestore (see `backend/README.md`).

- [ ] Play Console → Test and release → App integrity → Play Integrity API → **Link a Cloud project**:
      `englishtibetandictionary`.
- [ ] Firebase console → App Check → Apps → the Android app → **Play Integrity** → Register, with the SHA-256 of
      the Play app-signing key from step 1.
- [ ] A debug token for your test device: install a debug build, open Translate once, then find the token with
      `adb logcat | grep -i "debug secret"` (the debug provider prints "Enter this debug secret into the allow list
      …"). Add it under Firebase console → App Check → Apps → the Android app → ⋮ → **Manage debug tokens**. Remove
      it there when you no longer test from that device.
- [ ] After release, watch Crashlytics for non-fatal `UNAUTHENTICATED` errors. The app records every App Check or
      token rejection; the user sees "Something went wrong" and stays signed in. Play Integrity allows 10,000
      requests a day by default: if rejections climb with use, raise the Play Integrity token time-to-live
      (App Check → Apps) or ask for more quota in Play Console.

## 3. IAM

`deleteAccount` deletes the Firebase Auth user, so the functions' service account needs one more role. Run this
with `!` in Claude Code, or in a terminal signed in to gcloud as the project owner:

```bash
SA=translation-fn@englishtibetandictionary.iam.gserviceaccount.com
gcloud projects add-iam-policy-binding englishtibetandictionary \
  --member "serviceAccount:$SA" --role roles/firebaseauth.admin
```

- [ ] Check: `gcloud projects get-iam-policy englishtibetandictionary --flatten=bindings
      --filter="bindings.members:translation-fn@" --format="value(bindings.role)"` lists `roles/firebaseauth.admin`
      next to `roles/datastore.user` and `roles/cloudtranslate.user`.

## 4. Deploy the backend

The live functions predate the guest plan and `deleteAccount`: until this deploy, guests get the free plan's 10 a
day and account deletion fails with "Couldn't delete your account". Deploy before the app release. This branch
changes no Firestore rules or indexes, so functions are enough.

- [ ] From the repo root: `cd backend && firebase deploy --only functions --project englishtibetandictionary`.
      A non-interactive deploy may exit 1 after deploying, only because it couldn't set the container-image
      cleanup policy, which is already set.
- [ ] `firebase functions:list --project englishtibetandictionary` shows `translate`, `getTranslationStatus` and
      `deleteAccount` in asia-south1.
- [ ] Optional: the guest limits default to 3 a day at 100 characters. To change them, add `guest` to the Firestore
      doc `config/translation` as a **map** with `dailyLimit` and `maxChars` as **number** fields (a field added in
      the console is a string unless you pick the type).

## 5. Device pass

A debug build with its App Check debug token registered, against the deployed backend. Start from a fresh install.

- [ ] 1. A fresh install translates as a guest, and the pill counts 3 → 0.
- [ ] 2. Once the 3rd translation arrives, the sign-in card takes the Translate button's place and the result
      stays; signing in with Google shows "7 left".
- [ ] 3. Once the 10th arrives, the "Daily limit reached" card takes its place; Upgrade to Pro opens the Pro
      sheet ("Coming soon").
- [ ] 4. Offline (airplane mode) shows "You're offline · Not counted", and the count doesn't move.
- [ ] 5. Repeating a phrase gives "From cache · not counted".
- [ ] 6. Smart translate in search auto-runs only with more than 3 left; with 3 or fewer it offers a tap.
- [ ] 7. Reinstall and sign in again: the same uid (Firebase console → Authentication → Users), and the count
      holds.
- [ ] 8. Profile → Account → Delete account: the user is gone from Authentication, and the `dailyUsage` docs whose
      id starts with that uid are gone from Firestore.
- [ ] 9. Logcat (`adb logcat`) and Cloud Logging (Logs Explorer, the functions' entries) contain none of the text
      you typed or its translations. The functions log uid, direction, character count, a key prefix, provider,
      outcome and latency.

## 6. Privacy policy

- [ ] Write and host the policy, then set `Links.PRIVACY_URL` in
      `app/src/main/java/com/kharagedition/tibetandictionary/core/Links.kt`; Profile shows its Privacy link once it
      is set. It must cover:
  - text typed for translation is sent to Microsoft (Azure Translator) or Google (Cloud Translation);
  - translations are cached for up to 180 days, keyed by the text and not by the user, so they stay after an
    account is deleted;
  - signing in with Google gives the app the account's name, email and profile photo link, kept by Firebase
    Authentication (the app shows the name and email);
  - a guest gets an anonymous Firebase user ID, and App Check checks the app through Play Integrity;
  - translation usage counts per user and day, kept for 7 days;
  - account deletion: Profile → Account → Delete account removes the account and its usage counts at once; saved
    words and translations stay on the device;
  - Analytics events about translation (outcome, direction, plan) never include the text.

## 7. Account deletion web link

Play requires a web link where users can ask for deletion without the app.

- [ ] Add a "Delete your account" section to the privacy page (or give the developer email with what to send), and
      enter its URL under Play Console → Policy and programs → App content → Data safety → Data deletion.

## 8. Play Data safety

Play Console → Policy and programs → App content → Data safety:

- [ ] Collected: **name**, **email address**, **user IDs**, **app interactions**.
- [ ] Shared with processors for translation: **user-entered text** ("Other user-generated content"), sent to
      Microsoft or Google.
- [ ] Encrypted in transit: **yes**.
- [ ] Users can request that data be deleted: **yes** (in the app, and the link from step 7).

## 9. Tibetan strings for review

Every string the translation work added or changed (Tasks 9–15), with the English and the drafted Tibetan. The
Tibetan is a draft: review each one in `app/src/main/res/values-bo/strings.xml`. `%1$s`, `%2$s`… are filled with
numbers already formatted (Tibetan digits in the Tibetan UI) or with names. `translate_counter` is the same in both
languages and is not listed.

| Key | | English | Tibetan (draft) |
|---|---|---|---|
| `tab_translate` | new | Translate | ཡིག་སྒྱུར། |
| `tab_translate_gloss` | new | ཡིག་སྒྱུར། | Translate |
| `search_placeholder` | changed | Search or translate · ཚིག་འཚོལ། | ཚིག་འཚོལ་དང་ཡིག་སྒྱུར། · Search or translate |
| `saved_tab_words` | new | Words · %1$s | ཚིག · %1$s |
| `saved_tab_translations` | new | Translations · %1$s | ཡིག་སྒྱུར། · %1$s |
| `saved_translations_empty_title` | new | Saved translations appear here | ཉར་བའི་ཡིག་སྒྱུར་འདིར་མངོན། |
| `saved_translations_empty_body` | new | Tap ♡ on a translation to keep it. | ཡིག་སྒྱུར་ཞིག་ཉར་བར་ ♡ ལ་ནོན། |
| `profile_signed_out_body` | changed | Sign in to get 10 free translations a day | ནང་འཛུལ་བྱས་ན་ཉིན་རེར་ཡིག་སྒྱུར་རིན་མེད་ ༡༠ ཐོབ། |
| `profile_plan_none` | new | Translate sentences free every day. Sign in for more. | ཉིན་རེར་ཚིག་གྲུབ་རིན་མེད་དུ་ཡིག་སྒྱུར་བྱེད་ཆོག ནང་འཛུལ་བྱས་ན་དེ་ལས་མང་བ་ཐོབ། |
| `profile_plan_guest` | new | Guest | མགྲོན་པོ། |
| `profile_plan_guest_usage` | new | %1$s of %2$s used today | དེ་རིང་ %2$s ནས་ %1$s བཀོལ། |
| `profile_plan_sign_in_for` | new | Sign in for %1$s | ནང་འཛུལ་བྱས་ན་ %1$s |
| `profile_plan_sign_in_more` | new | Sign in for more | ནང་འཛུལ་བྱས་ན་མང་བ། |
| `profile_plan_free` | new | Free plan | རིན་མེད་འཆར་གཞི། |
| `profile_plan_upgrade` | new | Upgrade | རིམ་སྤར། |
| `profile_plan_free_usage` | new | %1$s of %2$s translations used today · %3$s characters each | དེ་རིང་ཡིག་སྒྱུར་ %2$s ནས་ %1$s བཀོལ། · ཐེངས་རེར་ཡིག་འབྲུ་ %3$s |
| `account_sheet_title` | new | Account | རྩིས་ཁྲ། |
| `sign_out` | new | Sign out | ཕྱིར་ཐོན། |
| `signed_out` | new | Signed out | ཕྱིར་ཐོན་ཟིན། |
| `delete_account` | new | Delete account | རྩིས་ཁྲ་སུབ། |
| `delete_account_title` | new | Delete your account? | རྩིས་ཁྲ་སུབ་དགོས་སམ། |
| `delete_account_body` | new | Your usage history is deleted. Saved words and translations stay on this phone. | བཀོལ་སྤྱོད་ཀྱི་ལོ་རྒྱུས་སུབ་ངེས། ཉར་བའི་ཚིག་དང་ཡིག་སྒྱུར་རྣམས་ཁ་པར་འདིའི་ནང་ལུས་ངེས། |
| `delete` | new | Delete | སུབ། |
| `deleting` | new | Deleting… | སུབ་བཞིན་པ… |
| `cancel` | new | Cancel | དོར། |
| `account_deleted` | new | Account deleted | རྩིས་ཁྲ་སུབ་ཟིན། |
| `account_delete_failed` | new | Couldn't delete your account. Try again. | རྩིས་ཁྲ་སུབ་མ་ཐུབ། ཡང་བསྐྱར་ཚོད་ལྟ་བྱེད། |
| `profile_search` | new | Search | འཚོལ་ཞིབ། |
| `smart_translate` | new | Smart translate in search | འཚོལ་ཞིབ་ནང་རང་འགུལ་ཡིག་སྒྱུར། |
| `smart_translate_body` | new | Auto-translate phrases with no dictionary match | ཚིག་མཛོད་ནང་མེད་པའི་ཚིག་གྲུབ་རང་འགུལ་གྱིས་ཡིག་སྒྱུར་བྱེད། |
| `translation_saved` | new | Saved to Translations | ཡིག་སྒྱུར་ནང་ཉར་ཚགས་བྱས། |
| `translation_removed` | new | Removed from Saved | ཉར་ཚགས་ནས་བསལ། |
| `translate_rate_limited` | new | Too many at once. Try again in a few seconds. | ཐེངས་གྲངས་མང་དྲགས་སོང་། སྐར་ཆ་འགའ་རྗེས་སུ་བསྐྱར་དུ་ཚོད་ལྟ། |
| `signed_in` | new | Signed in | ནང་འཛུལ་བྱས་ཟིན། |
| `signed_in_left` | new | Signed in · %1$s translations left today | ནང་འཛུལ་བྱས་ཟིན། · དེ་རིང་ཡིག་སྒྱུར་ %1$s ལྷག་ཡོད། |
| `sign_in_failed` | new | Couldn't sign in. Try again. | ནང་འཛུལ་བྱེད་མ་ཐུབ། བསྐྱར་དུ་ཚོད་ལྟ། |
| `translate_title` | new | Translate | ཡིག་སྒྱུར། |
| `translate_gloss` | new | ཡིག་སྒྱུར། | Translate |
| `translate_action` | new | Translate | སྒྱུར། |
| `translate_loading` | new | Translating… | སྒྱུར་བཞིན་པ… |
| `translate_left` | new | %1$s of %2$s left | %2$s ནས་ %1$s ལྷག |
| `translate_limit_reached` | new | Limit reached | ཚད་ལ་སླེབས། |
| `translate_pro_pill` | new | Pro | Pro |
| `translate_lang_english` | new | English | དབྱིན་ཡིག |
| `translate_swap` | new | Swap languages | སྐད་ཡིག་བརྗེ། |
| `translate_placeholder_tibetan` | new | ཡི་གེ་འདིར་འབྲི། · Type in Tibetan | ཡི་གེ་འདིར་འབྲི། |
| `translate_placeholder_english` | new | Type English text | དབྱིན་ཡིག་འདིར་འབྲི། |
| `translate_paste` | new | Paste | སྦྱར། |
| `translate_hint_wylie` | new | Type in Tibetan script — Wylie isn't translated | བོད་ཡིག་ཏུ་འབྲི་རོགས། ཝ་ཡིལ་ཡིག་སྒྱུར་མི་བྱེད། |
| `translate_hint_looks_tibetan` | new | This looks like Tibetan | འདི་བོད་ཡིག་ཡིན་པ་འདྲ། |
| `translate_switch` | new | Switch | བརྗེ། |
| `translate_try` | new | Try | ཚོད་ལྟ། |
| `translate_recent` | new | Recent | ཉེ་ཆར། |
| `translate_from_cache` | new | From cache · not counted | ཉར་ཚགས་ནས། · གྲངས་སུ་མི་རྩི། |
| `translate_by_google` | new | Machine translation · Google | འཕྲུལ་ཆས་ཡིག་སྒྱུར། · Google |
| `translate_by_microsoft` | new | Machine translation · Microsoft | འཕྲུལ་ཆས་ཡིག་སྒྱུར། · Microsoft |
| `translate_machine` | new | Machine translation | འཕྲུལ་ཆས་ཡིག་སྒྱུར། |
| `translate_pro_hint` | new | Need longer text? Pro allows %1$s characters. | ཡི་གེ་རིང་བ་དགོས་སམ། Pro ལ་ཡིག་འབྲུ་ %1$s ཆོག |
| `translate_guest_title` | new | You've used today's %1$s free translations | དེ་རིང་གི་རིན་མེད་ཡིག་སྒྱུར་ %1$s བེད་སྤྱོད་ཚར། |
| `translate_guest_body` | new | Sign in with Google to get %1$s more today. It's free. | Google བརྒྱུད་ནས་ནང་འཛུལ་བྱས་ན་དེ་རིང་ %1$s ལྷག་ཐོབ། རིན་མེད་ཡིན། |
| `translate_guest_body_unknown` | new | Sign in with Google for more translations today. It's free. | Google བརྒྱུད་ནས་ནང་འཛུལ་བྱས་ན་དེ་རིང་ཡིག་སྒྱུར་མང་བ་ཐོབ། རིན་མེད་ཡིན། |
| `continue_with_google` | new | Continue with Google | Google བརྒྱུད་ནས་མུ་མཐུད། |
| `translate_look_up` | new | Look up words | ཚིག་འཚོལ། |
| `translate_daily_title` | new | Daily limit reached · Resets at midnight | ཉིན་རེའི་ཚད་ལ་སླེབས། · ནམ་ཕྱེད་ལ་བསྐྱར་གསོ། |
| `translate_daily_body` | new | The dictionary still works for every word. | ཚིག་མཛོད་ནི་ཚིག་ཚང་མར་བེད་སྤྱོད་ཐུབ། |
| `upgrade_to_pro` | new | Upgrade to Pro | Pro ལ་རིམ་སྤར། |
| `translate_offline_title` | new | You're offline | དྲ་རྒྱ་མེད། |
| `translate_offline_body` | new | Not counted. Dictionary still works. | གྲངས་སུ་མི་རྩི། ཚིག་མཛོད་བེད་སྤྱོད་ཐུབ། |
| `translate_busy_title` | new | Translation is busy right now | ཡིག་སྒྱུར་ད་ལྟ་བྲེལ་བ་ཆེ། |
| `translate_busy_body` | new | Not counted. Try again in a moment. | གྲངས་སུ་མི་རྩི། ཅུང་ཙམ་རྗེས་སུ་བསྐྱར་དུ་ཚོད་ལྟ། |
| `translate_disabled_title` | new | Translation is paused | ཡིག་སྒྱུར་གནས་སྐབས་མཚམས་བཞག |
| `translate_disabled_body` | new | The dictionary still works. | ཚིག་མཛོད་བེད་སྤྱོད་ཐུབ། |
| `translate_unexpected_title` | new | Something went wrong | ནོར་འཁྲུལ་ཞིག་བྱུང་། |
| `translate_unexpected_body` | new | Not counted. | གྲངས་སུ་མི་རྩི། |
| `retry` | new | Retry | བསྐྱར་དུ་ཚོད་ལྟ། |
| `search_translate_offer` | new | Translate “%1$s” → %2$s | “%1$s” %2$s ལ་སྒྱུར། |
| `search_translate_tap` | new | Tap to translate → %1$s | མནན་ནས་ %1$s ལ་སྒྱུར། |
| `search_translate_instead` | new | Translate instead | དེའི་ཚབ་ཏུ་ཡིག་སྒྱུར། |
| `search_translate_left_today` | new | %1$s of %2$s left today | དེ་རིང་ %2$s ནས་ %1$s ལྷག |
| `home_translate_title` | new | Translate a sentence | ཚིག་གྲུབ་ཅིག་ཡིག་སྒྱུར་བྱེད། |
| `home_translate_languages` | new | %1$s ⇄ %2$s | %1$s ⇄ %2$s |
| `home_translate_left_today` | new | %1$s · %2$s left today | %1$s · དེ་རིང་ %2$s ལྷག |
| `search_translate_heading` | new | Translation | ཡིག་སྒྱུར། |
| `search_translate_open` | new | Open in Translate | ཡིག་སྒྱུར་ནང་ཁ་ཕྱེ། |
| `search_translate_too_long` | new | Too long for quick translate | མྱུར་སྒྱུར་ལ་རིང་དྲགས། |
| `search_translate_characters` | new | %1$s / %2$s characters | ཡིག་འབྲུ་ %1$s / %2$s |
| `search_translate_sign_in` | new | Sign in to translate | ཡིག་སྒྱུར་ཆེད་ནང་འཛུལ། |
| `search_translate_limit` | new | Daily limit reached | དེ་རིང་གི་ཚད་ལ་སླེབས། |
| `search_translate_upgrade` | new | Upgrade | རིམ་སྤར། |
| `search_in_dictionary` | new | In the dictionary | ཚིག་མཛོད་ནང་། |
| `today_used` | new | %1$s of %2$s used today | དེ་རིང་ %2$s ནས་ %1$s བེད་སྤྱོད་བྱས། |
| `today_resets` | new | Resets at midnight | ནམ་ཕྱེད་ལ་བསྐྱར་གསོ། |
| `today_sign_in` | new | Sign in to get %1$s a day | ནང་འཛུལ་བྱས་ན་ཉིན་རེར་ %1$s ཐོབ། |
| `today_sign_in_unknown` | new | Sign in for more translations | ནང་འཛུལ་བྱས་ན་ཡིག་སྒྱུར་མང་བ་ཐོབ། |
| `pro_title` | new | Translate without limits | ཚད་མེད་ཡིག་སྒྱུར། |
| `pro_subtitle` | new | The dictionary stays free. | ཚིག་མཛོད་རྟག་ཏུ་རིན་མེད། |
| `pro_free` | new | Free | རིན་མེད། |
| `pro_plan` | new | Pro | Pro |
| `pro_per_day` | new | Per day | ཉིན་རེར། |
| `pro_characters` | new | Characters | ཡིག་འབྲུ། |
| `pro_dictionary` | new | Dictionary | ཚིག་མཛོད། |
| `pro_unlimited` | new | Unlimited | ཚད་མེད། |
| `pro_full` | new | Full | ཆ་ཚང་། |
| `pro_coming_soon` | new | Coming soon | མྱུར་དུ་ཡོང་། |
| `pro_note` | new | Pro is on its way. You'll see it here first. | Pro མྱུར་དུ་ཡོང་། འདིར་ཐོག་མར་མཐོང་ཐུབ། |

## 10. Design to confirm

- [ ] The dark-mode amber of the "few left" and warning states has no dark mockup, so these values are a guess:
      `warnContainer` #4A3410, `onWarnContainer` #F3D08A, `warn` #E0B060 (`DictColors`, dark). The light ones
      follow the design (#FBE9C8, #7A4E00, #8A5A00).
