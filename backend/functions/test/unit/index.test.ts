import type { DecodedIdToken } from "firebase-admin/auth";
import type { CallableRequest } from "firebase-functions/v2/https";
import { afterAll, beforeAll, describe, expect, test, vi } from "vitest";

import {
  deleteAccount,
  getTranslationStatus,
  revenuecatWebhook,
  syncSubscription,
  translate,
} from "../../src/index.js";
import { reasonOf, rejection } from "./fakes.js";

interface Endpoint {
  region?: string[];
  availableMemoryMb?: number;
  cpu?: number;
  concurrency?: number;
  maxInstances?: number;
  timeoutSeconds?: number;
  serviceAccountEmail?: string;
  secretEnvironmentVariables?: { key: string }[];
  callableTrigger?: unknown;
  httpsTrigger?: unknown;
}

const endpointOf = (fn: unknown) => (fn as { __endpoint: Endpoint }).__endpoint;

describe("deployed functions", () => {
  test("translate is a callable in asia-south1 with the Azure key bound", () => {
    const endpoint = endpointOf(translate);
    expect(endpoint).toMatchObject({
      region: ["asia-south1"],
      availableMemoryMb: 512,
      cpu: 1,
      concurrency: 80,
      maxInstances: 10,
      timeoutSeconds: 30,
      serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
      callableTrigger: {},
    });
    expect(endpoint.secretEnvironmentVariables?.map((s) => s.key)).toEqual(["AZURE_TRANSLATOR_KEY"]);
  });

  test("deleteAccount is a callable in asia-south1 with no secrets", () => {
    const endpoint = endpointOf(deleteAccount);
    expect(endpoint).toMatchObject({
      region: ["asia-south1"],
      maxInstances: 2,
      serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
      callableTrigger: {},
    });
    expect(endpoint.secretEnvironmentVariables ?? []).toEqual([]);
  });

  test("getTranslationStatus is a callable in asia-south1 with no secrets", () => {
    const endpoint = endpointOf(getTranslationStatus);
    expect(endpoint).toMatchObject({ region: ["asia-south1"], maxInstances: 5, callableTrigger: {} });
    expect(endpoint.secretEnvironmentVariables ?? []).toEqual([]);
  });
});

describe("billing functions", () => {
  test("revenuecatWebhook is a public HTTPS function in asia-south1 with both RevenueCat secrets", () => {
    const endpoint = endpointOf(revenuecatWebhook);
    expect(endpoint).toMatchObject({
      region: ["asia-south1"],
      maxInstances: 5,
      timeoutSeconds: 30,
      serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
      httpsTrigger: { invoker: ["public"] },
    });
    expect(endpoint.callableTrigger).toBeUndefined();
    expect(endpoint.secretEnvironmentVariables?.map((s) => s.key).sort()).toEqual([
      "REVENUECAT_API_KEY",
      "REVENUECAT_WEBHOOK_AUTH",
    ]);
  });

  test("syncSubscription is a callable in asia-south1 with the RevenueCat API key bound", () => {
    const endpoint = endpointOf(syncSubscription);
    expect(endpoint).toMatchObject({
      region: ["asia-south1"],
      maxInstances: 5,
      timeoutSeconds: 30,
      serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
      callableTrigger: {},
    });
    expect(endpoint.secretEnvironmentVariables?.map((s) => s.key)).toEqual(["REVENUECAT_API_KEY"]);
  });
});

// The refusals return before any Firestore or Auth call, so they run here, with no emulator. `.run()` skips
// the framework's App Check and ID-token checks; the handler's own checks still run.
describe("deleteAccount wiring", () => {
  const request = (auth?: CallableRequest["auth"]): CallableRequest => ({
    data: null,
    rawRequest: {} as CallableRequest["rawRequest"],
    acceptsStreaming: false,
    ...(auth ? { auth } : {}),
  });

  test("without a signed-in user is unauthenticated", async () => {
    const error = await rejection(deleteAccount.run(request()));
    expect(error.code).toBe("unauthenticated");
    expect(reasonOf(error)).toBe("unauthenticated");
  });

  test("as a guest is guest_account", async () => {
    const guest = {
      uid: "guest-1",
      token: { firebase: { sign_in_provider: "anonymous", identities: {} } } as unknown as DecodedIdToken,
      rawToken: "test-id-token",
    };
    const error = await rejection(deleteAccount.run(request(guest)));
    expect(error.code).toBe("failed-precondition");
    expect(reasonOf(error)).toBe("guest_account");
  });
});

describe("syncSubscription wiring", () => {
  test("as a guest is guest_account", async () => {
    const guest = {
      uid: "guest-1",
      token: { firebase: { sign_in_provider: "anonymous", identities: {} } } as unknown as DecodedIdToken,
      rawToken: "test-id-token",
    };
    const request: CallableRequest = {
      data: null,
      rawRequest: {} as CallableRequest["rawRequest"],
      acceptsStreaming: false,
      auth: guest,
    };
    const error = await rejection(syncSubscription.run(request));
    expect(error.code).toBe("failed-precondition");
    expect(reasonOf(error)).toBe("guest_account");
  });
});

// The webhook's refusals answer before RevenueCat or Firestore is reached.
describe("revenuecatWebhook wiring", () => {
  beforeAll(() => {
    vi.stubEnv("REVENUECAT_WEBHOOK_AUTH", "test-webhook-secret");
  });

  afterAll(() => {
    vi.unstubAllEnvs();
  });

  async function call(method: string, authorization?: string): Promise<{ status?: number; body?: unknown }> {
    const sent: { status?: number; body?: unknown } = {};
    const headers: Record<string, string> = authorization === undefined ? {} : { authorization };
    const req = { method, headers, body: { event: { id: "evt", type: "RENEWAL", app_user_id: "u" } } };
    const res = {
      headersSent: false,
      status(code: number) {
        sent.status = code;
        return res;
      },
      send(payload: unknown) {
        sent.body = payload;
        return res;
      },
    };
    const get = (name: string) => headers[name.toLowerCase()];
    await (revenuecatWebhook as unknown as (req: unknown, res: unknown) => Promise<void>)(
      { ...req, get },
      res,
    );
    return sent;
  }

  test("a GET is 405", async () => {
    expect(await call("GET", "test-webhook-secret")).toEqual({ status: 405, body: "method not allowed" });
  });

  test("a POST without the shared secret is 401", async () => {
    expect((await call("POST")).status).toBe(401);
    expect((await call("POST", "wrong")).status).toBe(401);
  });
});
