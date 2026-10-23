import type { DecodedIdToken } from "firebase-admin/auth";
import { Timestamp } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { afterAll, afterEach, beforeAll, describe, expect, test, vi } from "vitest";

import { getTranslationStatus, revenuecatWebhook, syncSubscription, translate } from "../../src/index.js";
import { cacheKey } from "../../src/store/cacheKey.js";
import { reasonOf, rejection } from "../unit/fakes.js";
import { DAY_MS, testDb } from "./helpers.js";

// The exported callables, wired by src/index.ts to the emulator. `.run()` calls the handler directly, so the
// framework's App Check and ID-token checks are skipped; the handler's own auth check still runs. No provider
// can answer: Azure's key is `unset`, Google's credentials file does not exist, and fetch reaches only the
// emulator, so every test that gets past validation and the cache ends in provider_unavailable.

/** What src/util/cloudLogger.ts sends to Cloud Logging. */
const logged = vi.hoisted(() => [] as { level: string; message: string; data: unknown }[]);
vi.mock("firebase-functions/logger", () => ({
  info: (message: string, data: unknown) => logged.push({ level: "info", message, data }),
  warn: (message: string, data: unknown) => logged.push({ level: "warn", message, data }),
  error: (message: string, data: unknown) => logged.push({ level: "error", message, data }),
}));

const db = testDb();
const realFetch = globalThis.fetch;
/** URLs the code tried to reach outside the emulator. */
const outbound: string[] = [];
/** RevenueCat's answers, taken in order by the fetch stub; the URLs it was asked for. */
const revenueCatReplies: (() => Response)[] = [];
const revenueCatCalls: string[] = [];

const RC_KEY = "test-rc-key";
const WEBHOOK_SECRET = "test-webhook-secret";

beforeAll(() => {
  vi.stubEnv("AZURE_TRANSLATOR_KEY", "unset");
  vi.stubEnv("AZURE_TRANSLATOR_REGION", "eastus");
  vi.stubEnv("AZURE_TRANSLATOR_ENDPOINT", "https://translator.invalid");
  vi.stubEnv("GOOGLE_APPLICATION_CREDENTIALS", "/nonexistent/application-default-credentials.json");
  vi.stubEnv("REVENUECAT_API_KEY", RC_KEY);
  vi.stubEnv("REVENUECAT_WEBHOOK_AUTH", WEBHOOK_SECRET);
  // Before the first call: the providers capture fetch when the dependencies are built.
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((input, init) => {
      const url = input instanceof Request ? input.url : String(input);
      if (new URL(url).host === process.env.FIRESTORE_EMULATOR_HOST) return realFetch(input, init);
      if (new URL(url).host === "api.revenuecat.com") {
        revenueCatCalls.push(url);
        expect(new Headers(init?.headers).get("authorization")).toBe(`Bearer ${RC_KEY}`);
        const reply = revenueCatReplies.shift();
        return reply
          ? Promise.resolve(reply())
          : Promise.reject(new TypeError("no scripted RevenueCat reply"));
      }
      outbound.push(url);
      return Promise.reject(new TypeError("fetch failed: no network in tests"));
    }),
  );
});

afterEach(() => {
  expect(outbound.splice(0)).toEqual([]);
  expect(revenueCatReplies.splice(0)).toEqual([]);
  revenueCatCalls.splice(0);
});

afterAll(() => {
  const all = JSON.stringify(logged);
  expect(logged.length).toBeGreaterThan(0);
  for (const text of ["བཀྲ་ཤིས", "bkra shis", "ཀཀཀཀ", "ཁཁཁཁ", "a hundred letters"])
    expect(all).not.toContain(text);
});

let users = 0;
/** A new user per call site, so the per-user rate limit in the module's limiter never carries over. */
const newUser = () => `callable-user-${++users}`;

function callRequest(data: unknown, uid: string | null): CallableRequest {
  return {
    data,
    rawRequest: {} as CallableRequest["rawRequest"],
    acceptsStreaming: false,
    ...(uid === null ? {} : { auth: { uid, token: {} as DecodedIdToken, rawToken: "test-id-token" } }),
  };
}

const bo = (text: string, uid: string = newUser()) =>
  callRequest({ text, source: "bo", target: "en", timeZone: "Asia/Thimphu" }, uid);

describe("translate callable", () => {
  test("without a signed-in user is unauthenticated", async () => {
    const error = await rejection(
      translate.run(callRequest({ text: "བཀྲ་ཤིས", source: "bo", target: "en" }, null)),
    );
    expect(error.code).toBe("unauthenticated");
    expect(reasonOf(error)).toBe("unauthenticated");
  });

  test.each([
    ["no data", undefined],
    ["text is not a string", { text: 5, source: "bo", target: "en" }],
    ["unknown language", { text: "ཀ", source: "zh", target: "en" }],
  ])("a malformed request (%s) is bad_request", async (_name, data) => {
    const error = await rejection(translate.run(callRequest(data, newUser())));
    expect(error.code).toBe("invalid-argument");
    expect(reasonOf(error)).toBe("bad_request");
  });

  test("whitespace and zero-width characters only is empty_text", async () => {
    const error = await rejection(translate.run(bo(" \n\u200B\t ")));
    expect(error.code).toBe("invalid-argument");
    expect(reasonOf(error)).toBe("empty_text");
  });

  test("101 Tibetan code points are text_too_long; 100 pass validation", async () => {
    const tooLong = await rejection(translate.run(bo("ཀ".repeat(101))));
    expect(tooLong.code).toBe("invalid-argument");
    expect(tooLong.details).toEqual({ reason: "text_too_long", maxChars: 100, length: 101 });

    // A cached entry lets the 100-character text through without a provider.
    const text = "ཁ".repeat(100);
    await db.doc(`translations/${cacheKey(1, "bo", "en", text)}`).set({
      translation: "a hundred letters",
      provider: "google",
      expireAt: Timestamp.fromMillis(Date.now() + 10 * DAY_MS),
    });
    expect(await translate.run(bo(text))).toMatchObject({
      translation: "a hundred letters",
      cached: true,
      provider: "google",
      quota: { used: 0, maxChars: 100 },
    });
  });

  test("Tibetan to Dzongkha is unsupported_pair", async () => {
    const error = await rejection(
      translate.run(callRequest({ text: "ཀ", source: "bo", target: "dz" }, newUser())),
    );
    expect(error.code).toBe("invalid-argument");
    expect(reasonOf(error)).toBe("unsupported_pair");
  });

  test("Wylie as a Tibetan source is not_tibetan_script", async () => {
    const error = await rejection(translate.run(bo("bkra shis")));
    expect(error.code).toBe("invalid-argument");
    expect(reasonOf(error)).toBe("not_tibetan_script");
  });

  test("with no provider reachable it is provider_unavailable, and the reservation is refunded", async () => {
    const uid = newUser();
    const error = await rejection(translate.run(bo("བཀྲ་ཤིས", uid)));
    expect(error.code).toBe("unavailable");
    expect(reasonOf(error)).toBe("provider_unavailable");
    expect(logged).toContainEqual({
      level: "warn",
      message: "all providers failed",
      data: expect.objectContaining({
        uid,
        error: "no provider translated the text: azure=not_configured, google=transient, google=transient",
      }) as unknown,
    });

    const usage = await db.collection("dailyUsage").where("uid", "==", uid).get();
    expect(usage.docs.map((doc) => doc.data())).toEqual([expect.objectContaining({ count: 0, chars: 0 })]);
    expect(await getTranslationStatus.run(callRequest({ timeZone: "Asia/Thimphu" }, uid))).toMatchObject({
      used: 0,
      remaining: 10,
    });
  });
});

describe("getTranslationStatus callable", () => {
  test("returns the shape in the README", async () => {
    const status = await getTranslationStatus.run(callRequest({ timeZone: "Asia/Thimphu" }, newUser()));
    expect(Object.keys(status).sort()).toEqual([
      "billingEnabled",
      "enabled",
      "freeLimit",
      "limit",
      "maxChars",
      "pairs",
      "plan",
      "proLimit",
      "proMaxChars",
      "remaining",
      "resetsAt",
      "used",
    ]);
    expect(status).toEqual({
      enabled: true,
      plan: "free",
      used: 0,
      limit: 10,
      remaining: 10,
      resetsAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/) as unknown,
      maxChars: 100,
      pairs: ["bo-en", "en-bo", "dz-en", "en-dz"],
      freeLimit: 10,
      billingEnabled: false,
      proLimit: 300,
      proMaxChars: 500,
    });
    expect(Date.parse(status.resetsAt)).toBeGreaterThan(Date.now());
  });

  test("works with no data", async () => {
    await expect(getTranslationStatus.run(callRequest(null, newUser()))).resolves.toMatchObject({ used: 0 });
  });

  test("without a signed-in user is unauthenticated", async () => {
    const error = await rejection(getTranslationStatus.run(callRequest({}, null)));
    expect(error.code).toBe("unauthenticated");
    expect(reasonOf(error)).toBe("unauthenticated");
  });
});

const DAY = 86_400_000;

/** A v1 subscriber body as RevenueCat would send it now, with a monthly `pro` entitlement ending [expiresIn] ms from now. */
function subscriber(expiresIn: number, fields: { willRenew?: boolean } = {}): () => Response {
  return () => {
    const now = Date.now();
    const expires = new Date(now + expiresIn).toISOString();
    const body = {
      request_date_ms: now,
      subscriber: {
        entitlements: {
          pro: {
            expires_date: expires,
            grace_period_expires_date: null,
            product_identifier: "dictionary_pro",
          },
        },
        subscriptions: {
          dictionary_pro: {
            expires_date: expires,
            unsubscribe_detected_at: fields.willRenew === false ? new Date(now).toISOString() : null,
            billing_issues_detected_at: null,
            is_sandbox: true,
            store: "play_store",
          },
        },
      },
    };
    return new Response(JSON.stringify(body), { status: 200 });
  };
}

const serverError = () => new Response("{}", { status: 500 });

interface Sent {
  status?: number;
  body?: unknown;
}

/** Calls the exported webhook with a minimal Express request and response. */
async function webhook(body: unknown, authorization: string | null = WEBHOOK_SECRET): Promise<Sent> {
  const sent: Sent = {};
  const headers: Record<string, string> = authorization === null ? {} : { authorization };
  const req = { method: "POST", headers, body, get: (name: string) => headers[name.toLowerCase()] };
  const res = {
    headersSent: false,
    status(code: number) {
      sent.status = code;
      return res;
    },
    send(payload: unknown) {
      sent.body = payload;
      res.headersSent = true;
      return res;
    },
  };
  await (revenuecatWebhook as unknown as (req: unknown, res: unknown) => Promise<void>)(req, res);
  return sent;
}

const renewal = (uid: string) => ({
  api_version: "1.0",
  event: { id: "evt-1", type: "RENEWAL", environment: "SANDBOX", app_user_id: uid, aliases: [uid] },
});

describe("syncSubscription callable", () => {
  test("a buyer's sync stores Pro, and status then reports it", async () => {
    const uid = newUser();
    revenueCatReplies.push(subscriber(30 * DAY));
    expect(await syncSubscription.run(callRequest(null, uid))).toEqual({ plan: "pro" });
    expect(revenueCatCalls).toEqual([`https://api.revenuecat.com/v1/subscribers/${uid}`]);
    expect((await db.doc(`subscriptions/${uid}`).get()).get("product")).toBe("monthly");

    expect(await getTranslationStatus.run(callRequest({ timeZone: "Asia/Thimphu" }, uid))).toMatchObject({
      plan: "pro",
      limit: 300,
      maxChars: 500,
      billingEnabled: false,
      proLimit: 300,
      proMaxChars: 500,
    });
  });

  test("RevenueCat down is billing_unavailable and stores nothing", async () => {
    const uid = newUser();
    revenueCatReplies.push(serverError, serverError);
    const error = await rejection(syncSubscription.run(callRequest(null, uid)));
    expect(error.code).toBe("unavailable");
    expect(reasonOf(error)).toBe("billing_unavailable");
    expect((await db.doc(`subscriptions/${uid}`).get()).exists).toBe(false);
  });
});

describe("revenuecatWebhook", () => {
  test("a wrong Authorization header is 401 and reads nothing", async () => {
    expect((await webhook(renewal("user-x"), "wrong")).status).toBe(401);
    expect(revenueCatCalls).toEqual([]);
  });

  test("a renewal re-reads the customer and updates the record", async () => {
    const uid = newUser();
    revenueCatReplies.push(subscriber(30 * DAY));
    await syncSubscription.run(callRequest(null, uid));
    revenueCatReplies.push(subscriber(60 * DAY, { willRenew: false }));
    expect(await webhook(renewal(uid))).toEqual({ status: 200, body: "ok" });
    const doc = (await db.doc(`subscriptions/${uid}`).get()).data();
    expect(doc).toMatchObject({ active: true, willRenew: false, source: "webhook" });
  });

  test("RevenueCat failing twice is 500, so RevenueCat retries", async () => {
    revenueCatReplies.push(serverError, serverError);
    expect((await webhook(renewal(newUser()))).status).toBe(500);
  });

  test("a TEST event is 200 without reading RevenueCat", async () => {
    expect((await webhook({ event: { id: "evt-test", type: "TEST" } })).status).toBe(200);
    expect(revenueCatCalls).toEqual([]);
  });
});
