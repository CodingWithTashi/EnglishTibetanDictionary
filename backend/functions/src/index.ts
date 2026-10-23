import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onCall, type CallableOptions } from "firebase-functions/v2/https";
import { GoogleAuth } from "google-auth-library";

import { AZURE_TRANSLATOR_ENDPOINT, AZURE_TRANSLATOR_KEY, AZURE_TRANSLATOR_REGION } from "./config/params.js";
import { createConfigSource } from "./config/runtimeConfig.js";
import { createAzureProvider } from "./providers/azure.js";
import { createGoogleProvider } from "./providers/google.js";
import { createRouter } from "./providers/router.js";
import { handleDeleteAccount, type AccountDeps } from "./service/accountHandler.js";
import { resolvePlan } from "./service/plan.js";
import { createRateLimiter } from "./service/rateLimiter.js";
import { handleStatus, handleTranslate, type Deps, type StatusDeps } from "./service/translateHandler.js";
import { createBudgetStore } from "./store/budgetStore.js";
import { createQuotaStore } from "./store/quotaStore.js";
import { createTranslationCache } from "./store/translationCache.js";
import { createUserDeleter } from "./auth/userDeleter.js";
import { cloudLogger } from "./util/cloudLogger.js";

const app = initializeApp();
const db = getFirestore(app);
// REST instead of gRPC: smaller cold start; nothing here needs streaming. Not on the emulator: the REST client
// signs every request with Application Default Credentials, so it fails where there are none (CI).
db.settings({ preferRest: !process.env.FIRESTORE_EMULATOR_HOST });

const log = cloudLogger;
const now = () => new Date();

function projectId(): string {
  const config: unknown = JSON.parse(process.env.FIREBASE_CONFIG ?? "{}");
  const fromConfig = (config as { projectId?: unknown }).projectId;
  return process.env.GCLOUD_PROJECT ?? (typeof fromConfig === "string" ? fromConfig : "");
}

let statusDeps: StatusDeps | undefined;
let translateDeps: Deps | undefined;
let accountDeps: AccountDeps | undefined;

/** Built on first use: secrets and params can only be read while handling a request. */
function getStatusDeps(): StatusDeps {
  statusDeps ??= {
    config: createConfigSource(async () => (await db.collection("config").doc("translation").get()).data(), {
      log,
    }),
    quota: createQuotaStore(db, { now }),
    resolvePlan,
    log,
    now,
  };
  return statusDeps;
}

function getTranslateDeps(): Deps {
  if (translateDeps) return translateDeps;
  const googleAuth = new GoogleAuth({ scopes: "https://www.googleapis.com/auth/cloud-translation" });
  translateDeps = {
    ...getStatusDeps(),
    cache: createTranslationCache(db, { log, now }),
    budget: createBudgetStore(db, { now }),
    rateLimiter: createRateLimiter(),
    router: createRouter({
      log,
      providers: {
        azure: createAzureProvider({
          key: AZURE_TRANSLATOR_KEY.value(),
          region: AZURE_TRANSLATOR_REGION.value(),
          endpoint: AZURE_TRANSLATOR_ENDPOINT.value(),
        }),
        google: createGoogleProvider({
          projectId: projectId(),
          getAccessToken: async () => {
            const token = await googleAuth.getAccessToken();
            if (!token) throw new Error("no token from application default credentials");
            return token;
          },
        }),
      },
    }),
  };
  return translateDeps;
}

function getAccountDeps(): AccountDeps {
  accountDeps ??= { quota: createQuotaStore(db, { now }), users: createUserDeleter(getAuth(app)), log };
  return accountDeps;
}

const REGION = "asia-south1"; // next to the Firestore database

const common: CallableOptions = {
  region: REGION,
  enforceAppCheck: true,
  cpu: 1,
  memory: "512MiB",
  concurrency: 80,
  timeoutSeconds: 30,
  // The full email: firebase-tools 15.34 grants secret access to the short "translation-fn@" form verbatim,
  // which Secret Manager rejects.
  serviceAccount: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
};

/** Translates text between Tibetan or Dzongkha and English. See backend/README.md for the contract. */
export const translate = onCall({ ...common, maxInstances: 10, secrets: [AZURE_TRANSLATOR_KEY] }, (request) =>
  handleTranslate(request, getTranslateDeps()),
);

/** Today's quota and the open directions, for the app's Home and Settings. */
export const getTranslationStatus = onCall({ ...common, maxInstances: 5 }, (request) =>
  handleStatus(request, getStatusDeps()),
);

/** Deletes a signed-in user's account and usage; Play requires it for apps that create accounts. */
export const deleteAccount = onCall({ ...common, maxInstances: 2 }, (request) =>
  handleDeleteAccount(request, getAccountDeps()),
);
