import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onCall, onRequest, type CallableOptions } from "firebase-functions/v2/https";
import { GoogleAuth } from "google-auth-library";

import { createRevenueCatClient } from "./billing/revenueCat.js";
import {
  AZURE_TRANSLATOR_ENDPOINT,
  AZURE_TRANSLATOR_KEY,
  AZURE_TRANSLATOR_REGION,
  REVENUECAT_API_KEY,
  REVENUECAT_WEBHOOK_AUTH,
} from "./config/params.js";
import { createConfigSource } from "./config/runtimeConfig.js";
import { createAzureProvider } from "./providers/azure.js";
import { createGoogleProvider } from "./providers/google.js";
import { createRouter } from "./providers/router.js";
import { handleDeleteAccount, type AccountDeps } from "./service/accountHandler.js";
import { createPlanResolver } from "./service/plan.js";
import { createRateLimiter } from "./service/rateLimiter.js";
import { refreshSubscription, type RefreshDeps } from "./service/subscriptionRefresh.js";
import { handleSyncSubscription, type SyncDeps } from "./service/syncHandler.js";
import { handleStatus, handleTranslate, type Deps, type StatusDeps } from "./service/translateHandler.js";
import { handleWebhook, type WebhookDeps } from "./service/webhookHandler.js";
import { createBudgetStore } from "./store/budgetStore.js";
import { createQuotaStore } from "./store/quotaStore.js";
import { createSubscriptionStore } from "./store/subscriptionStore.js";
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
let refreshDeps: RefreshDeps | undefined;
let syncDeps: SyncDeps | undefined;
let webhookDeps: WebhookDeps | undefined;

/** Built on first use: secrets and params can only be read while handling a request. */
function getStatusDeps(): StatusDeps {
  statusDeps ??= {
    config: createConfigSource(async () => (await db.collection("config").doc("translation").get()).data(), {
      log,
    }),
    quota: createQuotaStore(db, { now }),
    resolvePlan: createPlanResolver({ subscriptions: createSubscriptionStore(db, { now }), now, log }),
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
  accountDeps ??= {
    quota: createQuotaStore(db, { now }),
    subscriptions: createSubscriptionStore(db, { now }),
    users: createUserDeleter(getAuth(app)),
    log,
  };
  return accountDeps;
}

function getRefreshDeps(): RefreshDeps {
  refreshDeps ??= {
    revenueCat: createRevenueCatClient({ apiKey: REVENUECAT_API_KEY.value() }),
    subscriptions: createSubscriptionStore(db, { now }),
    log,
  };
  return refreshDeps;
}

function getSyncDeps(): SyncDeps {
  syncDeps ??= {
    refresh: (uid) => refreshSubscription(uid, "sync", getRefreshDeps()),
    rateLimiter: createRateLimiter(),
    log,
    now,
  };
  return syncDeps;
}

function getWebhookDeps(): WebhookDeps {
  webhookDeps ??= {
    authSecret: () => REVENUECAT_WEBHOOK_AUTH.value(),
    refresh: (uid) => refreshSubscription(uid, "webhook", getRefreshDeps()),
    log,
  };
  return webhookDeps;
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

/** Re-reads the caller's subscription from RevenueCat after a purchase, restore or plan switch; returns the plan. */
export const syncSubscription = onCall(
  { ...common, maxInstances: 5, secrets: [REVENUECAT_API_KEY] },
  (request) => handleSyncSubscription(request, getSyncDeps()),
);

/**
 * RevenueCat's webhook: every event re-reads the customers it names into `subscriptions/{uid}`. Public, because
 * RevenueCat calls it; it checks the shared `Authorization` value instead of App Check.
 */
export const revenuecatWebhook = onRequest(
  {
    region: REGION,
    invoker: "public",
    cpu: 1,
    memory: "256MiB",
    concurrency: 80,
    maxInstances: 5,
    timeoutSeconds: 30,
    serviceAccount: common.serviceAccount,
    secrets: [REVENUECAT_WEBHOOK_AUTH, REVENUECAT_API_KEY],
  },
  async (req, res) => {
    const reply = await handleWebhook(
      { method: req.method, header: (name) => req.get(name), body: req.body as unknown },
      getWebhookDeps(),
    );
    res.status(reply.status).send(reply.body);
  },
);
