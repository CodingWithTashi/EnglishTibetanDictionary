# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This folder holds the Firebase Functions translation backend for the Android app: two callables, `translate`
and `getTranslationStatus`. TypeScript on Node 22, region asia-south1, Firebase project
`englishtibetandictionary`.
- `README.md`: the API contract, error reasons, runtime config and deploy runbook.
- `functions/architecture.md`: the request flow and how the parts fit.
- Design and decisions: `docs/superpowers/specs/2026-10-22-translation-backend-design.md`.

## Commands

Run everything from `backend/functions`. Needs Node 22, and Java 21+ for the Firestore emulator.

```bash
npm ci
npm run format          # Prettier (format:check in CI); also checks the .md files here
npm run lint            # ESLint, typescript-eslint strictTypeChecked + stylisticTypeChecked
npm run typecheck
npm test                # unit tests (test/unit), no emulator
npm run test:emulator   # Firestore emulator tests (test/emulator) as project demo-translation
npm run coverage        # unit tests + coverage thresholds (vitest.config.ts)
npm run coverage:emulator   # emulator tests + coverage thresholds (vitest.emulator.config.ts)
npm run build           # tsc → lib/

npx vitest run test/unit/router.test.ts -t "breaker"     # one unit file / test name
npx firebase emulators:exec --only firestore --project demo-translation \
  "npx vitest run --config vitest.emulator.config.ts test/emulator/quotaStore.test.ts"   # one emulator file
```

CI runs format:check, lint, typecheck, unit and emulator tests (as the two coverage commands) and build in the
`translation-backend` job of `.github/workflows/main.yml`. Coverage thresholds are a floor: raise them as
coverage grows, never lower them to get a change through. `release.yml` reuses that workflow, so a failing
backend job blocks Android releases.

## Architecture in brief

- `src/index.ts` is wiring only. It builds dependencies lazily on the first request, because secrets and
  params can't be read at deploy-analysis time. It also sets the `onCall` options: App Check enforced, the
  `translation-fn` service account, `maxInstances`.
- `src/service/translateHandler.ts` is the whole pipeline: auth → config → validation and normalisation →
  rate limit → cache (memory, then Firestore) → global budget → transactional quota reserve → provider router
  → cache and budget writes. A provider failure refunds the quota. Cache hits are never counted.
- The handler gets everything through the `Deps` interfaces. Unit tests use the fakes in `test/unit/fakes.ts`.
  Store and security-rules behaviour is tested only on the emulator (`test/emulator/`).
- `src/domain/` is pure: no I/O, no firebase imports. That's why `HttpsError` mapping lives in
  `src/service/errors.ts`.
- `src/providers/router.ts` walks the ordered route for a direction from the Firestore doc
  `config/translation`. Switching provider is a doc edit, not a deploy. Defaults and per-field validation are
  in `src/config/runtimeConfig.ts`. If `enabled` is present but not a boolean, translation turns off.

## Rules and gotchas

- ESM with NodeNext: relative imports need the `.js` extension.
- `console` is banned outside `scripts/`; log through the `Logger`.
- Never log user text or translations. `translateHandler.test.ts` asserts this over every log payload.
- Emulator tests must use project `demo-translation`, never the real project.
- Test layers: `test/unit` (fakes), `test/emulator/*Store.test.ts` (one store), `translatePipeline.test.ts`
  (the handler on the real stores, fake router and config), `callables.test.ts` (the exported functions through
  `.run()`, which skips App Check). The callables test has no network: it points Google credentials at a
  missing file, sets the Azure key to `unset`, and its fetch stub fails any request that isn't to the emulator.
- `src/index.ts` uses Firestore over REST in production but gRPC when `FIRESTORE_EMULATOR_HOST` is set: the
  REST client signs every request with Application Default Credentials, so it fails on the emulator wherever
  there are none (CI) and uses your gcloud credentials where there are.
- Behaviour that is deliberate but looks odd is listed under "Known limits" in `README.md` and pinned by a
  test. Change it only on purpose, together with that test.
- `test/unit/index.test.ts` pins the deploy options through `__endpoint`: region, memory, cpu, concurrency,
  maxInstances, timeout, service account and secret binding. If you change an option, update that test.
- `serviceAccount` must be the full email. firebase-tools 15.34 passes the short `translation-fn@` form
  unchanged to Secret Manager when granting access to the secret, and the deploy fails.
- Parameters and secrets:
  - `functions/.env` is committed and holds the non-secret params: `AZURE_TRANSLATOR_REGION=eastus` and
    `AZURE_TRANSLATOR_ENDPOINT`. Keep both lines, even empty; without them `firebase deploy` prompts, or fails
    when non-interactive.
  - The Azure key is the Secret Manager secret `AZURE_TRANSLATOR_KEY`. For the local functions emulator, put
    it in `functions/.secret.local` (git-ignored).
- Google Cloud Translation uses the service account through Application Default Credentials, with no API key.
  Its `quotaProject` option (header `x-goog-user-project`) is only for local scripts running on user
  credentials. Never set it in `index.ts`.
- Google's API accepts `bo` even though its published language list omits it. Azure does not support `dz`.
  Trust `npm run check-providers` (real calls) over language lists.
- Version pins are deliberate:
  - The test-only `firebase` 12.x and `@firebase/rules-unit-testing` 5.x stay because newer versions need
    Node ≥ 24.12.
  - The `overrides` for `@firebase/app` in `package.json` stop duplicate SDK copies from breaking the rules
    tests.
  - TypeScript stays at 6.0.x for typescript-eslint.
  - The runtime is nodejs22, which Cloud Functions deprecates on 2027-04-30.
- Don't enable App Check enforcement for Cloud Firestore. App versions before 3.0 write `Message` feedback
  signed out with no App Check token.
- These commands touch the live project and need the owner's explicit go:
  - `firebase deploy` (from `backend/`).
  - `npm run check-providers` (billable). Get the key with
    `AZURE_TRANSLATOR_KEY="$(gcloud secrets versions access latest --secret AZURE_TRANSLATOR_KEY --project englishtibetandictionary)"`.
  - `npm run smoke` (billable). It needs an App Check debug token; delete the token afterwards.
- A non-interactive first deploy exits 1 after a successful deploy, only because it couldn't set the
  container-image cleanup policy. The policy is already set: 1 day, asia-south1.
