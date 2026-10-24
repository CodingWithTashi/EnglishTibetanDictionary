import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, test, vi } from "vitest";

import {
  createRevenueCatClient,
  productOf,
  RevenueCatError,
  toSnapshot,
} from "../../src/billing/revenueCat.js";

// RevenueCat's documented v1 subscriber shape for an active monthly Play subscription. Replace it with a real
// sandbox response once the first test purchase has been made (spec, End to end).
const ACTIVE_MONTHLY: unknown = JSON.parse(
  readFileSync(resolve(import.meta.dirname, "../fixtures/revenuecat/subscriber-active-monthly.json"), "utf8"),
);

const FETCHED_AT = new Date("2026-10-23T10:00:00Z");

interface Body {
  request_date_ms: number;
  subscriber: {
    entitlements: Record<string, Record<string, unknown>>;
    subscriptions: Record<string, Record<string, unknown>>;
  };
}

/** The active monthly fixture with `edit` applied to a deep copy. */
function body(edit: (body: Body) => void = () => undefined): Body {
  const copy = structuredClone(ACTIVE_MONTHLY) as Body;
  edit(copy);
  return copy;
}

const pro = (b: Body) => b.subscriber.entitlements.pro ?? {};
const monthly = (b: Body) => b.subscriber.subscriptions.dictionary_pro ?? {};

/** Makes the fixture a yearly subscription. */
function yearly(b: Body): void {
  pro(b).product_identifier = "dictionary_pro_annually";
  pro(b).product_plan_identifier = "yearly";
  b.subscriber.subscriptions = {
    dictionary_pro_annually: { ...monthly(b), product_plan_identifier: "yearly" },
  };
}

describe("toSnapshot", () => {
  test("an active monthly subscription", () => {
    expect(toSnapshot(body())).toEqual({
      fetchedAt: FETCHED_AT,
      pro: {
        active: true,
        expiresAt: new Date("2026-11-23T09:00:00Z"),
        product: "monthly",
        willRenew: true,
        billingIssue: false,
        sandbox: false,
        store: "play_store",
      },
    });
  });

  test("an active yearly subscription", () => {
    expect(toSnapshot(body(yearly)).pro).toMatchObject({ active: true, product: "yearly", willRenew: true });
  });

  test("cancelled but still in its paid period: active, not renewing", () => {
    const snapshot = toSnapshot(body((b) => (monthly(b).unsubscribe_detected_at = "2026-10-24T09:00:00Z")));
    expect(snapshot.pro).toMatchObject({ active: true, willRenew: false });
  });

  test("in a grace period: active until the grace period ends", () => {
    const snapshot = toSnapshot(
      body((b) => {
        pro(b).expires_date = "2026-10-23T08:00:00Z";
        pro(b).grace_period_expires_date = "2026-10-30T08:00:00Z";
        monthly(b).billing_issues_detected_at = "2026-10-23T08:00:00Z";
      }),
    );
    expect(snapshot.pro).toMatchObject({
      active: true,
      expiresAt: new Date("2026-10-30T08:00:00Z"),
      billingIssue: true,
    });
  });

  test("a billing issue is reported while the period still runs", () => {
    const snapshot = toSnapshot(
      body((b) => (monthly(b).billing_issues_detected_at = "2026-10-23T09:30:00Z")),
    );
    expect(snapshot.pro).toMatchObject({ active: true, billingIssue: true });
  });

  test("an expired entitlement is listed but not active", () => {
    const snapshot = toSnapshot(body((b) => (pro(b).expires_date = "2026-10-23T09:59:59Z")));
    expect(snapshot.pro).toMatchObject({ active: false, expiresAt: new Date("2026-10-23T09:59:59Z") });
  });

  test("an entitlement with no expiry is never active: only subscriptions are sold", () => {
    expect(toSnapshot(body((b) => (pro(b).expires_date = null))).pro).toMatchObject({
      active: false,
      expiresAt: null,
    });
  });

  test("a sandbox purchase is marked", () => {
    expect(toSnapshot(body((b) => (monthly(b).is_sandbox = true))).pro).toMatchObject({ sandbox: true });
  });

  test("no pro entitlement gives no record, with the request time", () => {
    expect(toSnapshot(body((b) => (b.subscriber.entitlements = {})))).toEqual({
      fetchedAt: FETCHED_AT,
      pro: null,
    });
  });

  test("an unknown product still unlocks pro, with no product", () => {
    const snapshot = toSnapshot(
      body((b) => {
        pro(b).product_identifier = "dictionary_pro_x";
        b.subscriber.subscriptions = { dictionary_pro_x: monthly(b) };
      }),
    );
    expect(snapshot.pro).toMatchObject({ active: true, product: null, willRenew: true });
  });

  test("a subscription keyed with its base plan is still found", () => {
    const snapshot = toSnapshot(
      body((b) => {
        pro(b).product_identifier = "dictionary_pro:monthly";
        b.subscriber.subscriptions = {
          "dictionary_pro:monthly": { ...monthly(b), unsubscribe_detected_at: "2026-10-24T09:00:00Z" },
        };
      }),
    );
    expect(snapshot.pro).toMatchObject({ product: "monthly", willRenew: false });
  });

  test("an entitlement with no matching subscription entry is not renewing", () => {
    const snapshot = toSnapshot(body((b) => (b.subscriber.subscriptions = {})));
    expect(snapshot.pro).toMatchObject({
      active: true,
      product: "monthly",
      willRenew: false,
      billingIssue: false,
      sandbox: false,
      store: "unknown",
    });
  });

  test.each([
    ["no subscriber", { request_date_ms: 1 }],
    ["entitlements not a map", { request_date_ms: 1, subscriber: { entitlements: [] } }],
    ["no request time", { subscriber: { entitlements: {} } }],
    ["a pro entitlement with no product", body((b) => delete pro(b).product_identifier)],
    ["an unreadable expiry", body((b) => (pro(b).expires_date = "soon"))],
    [
      "a subscription entry that is not a map",
      body((b) => (b.subscriber.subscriptions = { dictionary_pro: 3 } as never)),
    ],
    ["not an object", "ok"],
  ])("%s is a bad_response, never 'not Pro'", (_name, raw) => {
    expect(() => toSnapshot(raw)).toThrow(expect.objectContaining({ kind: "bad_response" }));
  });
});

describe("productOf", () => {
  test.each([
    ["dictionary_pro", "monthly"],
    ["dictionary_pro:monthly", "monthly"],
    ["dictionary_pro_annually", "yearly"],
    ["dictionary_pro_annually:yearly", "yearly"],
    ["dictionary_pro_annually_x", null],
    ["dictionary_pro_x:monthly", null],
    ["", null],
  ])("%s → %s", (id, product) => {
    expect(productOf(id)).toBe(product);
  });
});

describe("createRevenueCatClient", () => {
  const KEY = "sk_test_secret_key";

  function reply(status: number, json: unknown = body()): Response {
    return new Response(typeof json === "string" ? json : JSON.stringify(json), { status });
  }

  /** A client whose fetch answers from `replies` in order and records each call. */
  function client(replies: (() => Promise<Response>)[], options: { timeoutMs?: number } = {}) {
    const calls: { url: string; init: RequestInit | undefined }[] = [];
    const fetchImpl = vi.fn<typeof fetch>((input, init) => {
      calls.push({ url: input instanceof Request ? input.url : input.toString(), init });
      const next = replies.shift();
      if (!next) throw new Error("unexpected extra request");
      return next();
    });
    return { rc: createRevenueCatClient({ apiKey: KEY, fetchImpl, retryDelayMs: 0, ...options }), calls };
  }

  const ok =
    (status = 200) =>
    () =>
      Promise.resolve(reply(status));

  async function failure(promise: Promise<unknown>): Promise<RevenueCatError> {
    try {
      await promise;
    } catch (error) {
      if (error instanceof RevenueCatError) return error;
      throw error;
    }
    throw new Error("expected a RevenueCatError");
  }

  test("GETs the subscriber with the secret key and parses it", async () => {
    const { rc, calls } = client([ok()]);
    expect((await rc.getSubscriber("firebase-uid-1")).pro?.product).toBe("monthly");
    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toBe("https://api.revenuecat.com/v1/subscribers/firebase-uid-1");
    expect(new Headers(calls[0]?.init?.headers).get("authorization")).toBe(`Bearer ${KEY}`);
    expect(calls[0]?.init?.method ?? "GET").toBe("GET");
  });

  test.each([
    ["a%b", "a%25b"],
    ["ü", "%C3%BC"],
    ["a b", "a%20b"],
  ])("the uid %s is URL-encoded", async (uid, encoded) => {
    const { rc, calls } = client([ok()]);
    await rc.getSubscriber(uid);
    expect(calls[0]?.url).toBe(`https://api.revenuecat.com/v1/subscribers/${encoded}`);
  });

  test("201 (a customer RevenueCat just created) parses too", async () => {
    const { rc } = client([
      () =>
        Promise.resolve(
          reply(
            201,
            body((b) => (b.subscriber.entitlements = {})),
          ),
        ),
    ]);
    expect((await rc.getSubscriber("u")).pro).toBeNull();
  });

  test.each([401, 403])("HTTP %i is auth and not retried", async (status) => {
    const { rc, calls } = client([() => Promise.resolve(reply(status, { message: "bad key" }))]);
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("auth");
    expect(calls).toHaveLength(1);
  });

  test("HTTP 404 is a bad_response and not retried", async () => {
    const { rc, calls } = client([() => Promise.resolve(reply(404, {}))]);
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("bad_response");
    expect(calls).toHaveLength(1);
  });

  test("429 then 200 succeeds after one retry", async () => {
    const { rc, calls } = client([() => Promise.resolve(reply(429, {})), ok()]);
    expect((await rc.getSubscriber("u")).pro?.active).toBe(true);
    expect(calls).toHaveLength(2);
  });

  test("429 twice is rate_limited", async () => {
    const { rc } = client([() => Promise.resolve(reply(429, {})), () => Promise.resolve(reply(429, {}))]);
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("rate_limited");
  });

  test("5xx twice is server, after exactly one retry", async () => {
    const { rc, calls } = client([
      () => Promise.resolve(reply(500, {})),
      () => Promise.resolve(reply(503, {})),
    ]);
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("server");
    expect(calls).toHaveLength(2);
  });

  test("a network error twice is network", async () => {
    const down = () => Promise.reject(new TypeError("fetch failed"));
    const { rc, calls } = client([down, down]);
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("network");
    expect(calls).toHaveLength(2);
  });

  test("a request that outlives the timeout is aborted and counts as network", async () => {
    const calls: AbortSignal[] = [];
    const fetchImpl = vi.fn<typeof fetch>((_input, init) => {
      const signal = init?.signal;
      if (!signal) throw new Error("no signal");
      calls.push(signal);
      // Never answers; only the abort ends it.
      return new Promise<Response>((_resolve, reject) => {
        signal.addEventListener("abort", () => {
          reject(signal.reason as Error);
        });
      });
    });
    const rc = createRevenueCatClient({ apiKey: KEY, fetchImpl, timeoutMs: 20, retryDelayMs: 0 });
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("network");
    expect(calls).toHaveLength(2);
  });

  test("a 200 that is not JSON is a bad_response", async () => {
    const { rc } = client([() => Promise.resolve(reply(200, "<html>"))]);
    expect((await failure(rc.getSubscriber("u"))).kind).toBe("bad_response");
  });

  test("no error message carries the key", async () => {
    const down = () => Promise.reject(new TypeError(`fetch failed`));
    for (const replies of [
      [() => Promise.resolve(reply(401, { message: KEY }))],
      [down, down],
      [() => Promise.resolve(reply(200, KEY))],
    ]) {
      const error = await failure(client(replies).rc.getSubscriber("u"));
      expect(error.message).not.toContain(KEY);
    }
  });
});
