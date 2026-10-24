import { describe, expect, test } from "vitest";

import { createPlanResolver, isGuest, isProActive, PRO_LEEWAY_MS } from "../../src/service/plan.js";
import type { StoredSubscription } from "../../src/store/subscriptionStore.js";
import { fakeLogger, memorySubscriptions } from "./fakes.js";

describe("isGuest", () => {
  test.each([
    ["anonymous, no identities", { sign_in_provider: "anonymous", identities: {} }, true],
    ["anonymous, identities missing", { sign_in_provider: "anonymous" }, true],
    [
      "anonymous token minted after a Google link",
      { sign_in_provider: "anonymous", identities: { "google.com": ["1"] } },
      false,
    ],
    [
      "google",
      { sign_in_provider: "google.com", identities: { "google.com": ["1"], email: ["a@b.c"] } },
      false,
    ],
    ["no firebase claim", undefined, false],
  ])("%s", (_name, firebase, expected) => {
    expect(isGuest({ uid: "u", token: firebase === undefined ? {} : { firebase } })).toBe(expected);
  });

  test("no token at all is not a guest", () => {
    expect(isGuest({ uid: "u" })).toBe(false);
  });
});

const NOW = new Date("2026-10-23T12:00:00Z");
const MINUTE = 60_000;

function subscription(fields: Partial<StoredSubscription> = {}): StoredSubscription {
  return {
    active: true,
    expiresAt: new Date(NOW.getTime() + 30 * 86_400_000),
    product: "monthly",
    willRenew: true,
    billingIssue: false,
    sandbox: false,
    store: "play_store",
    fetchedAt: new Date(NOW.getTime() - MINUTE),
    updatedAt: new Date(NOW.getTime() - MINUTE),
    source: "webhook",
    ...fields,
  };
}

function resolver(doc?: StoredSubscription) {
  const subscriptions = memorySubscriptions();
  if (doc) subscriptions.docs.set("user-1", doc);
  const log = fakeLogger();
  return { resolve: createPlanResolver({ subscriptions, now: () => NOW, log }), subscriptions, log };
}

const signedIn = { uid: "user-1", isGuest: false };

describe("createPlanResolver", () => {
  test("a guest is on the guest plan, without reading a record", async () => {
    const { resolve, subscriptions } = resolver(subscription());
    subscriptions.failGet = new Error("must not read");
    expect(await resolve({ uid: "user-1", isGuest: true })).toBe("guest");
  });

  test("a signed-in user with no record is free", async () => {
    expect(await resolver().resolve(signedIn)).toBe("free");
  });

  test("an active record is pro", async () => {
    expect(await resolver(subscription()).resolve(signedIn)).toBe("pro");
  });

  test.each([
    ["1 ms left", 1, "pro"],
    ["ended 14 min 59 s ago", -(15 * MINUTE - 1_000), "pro"],
    ["ended exactly 15 min ago", -15 * MINUTE, "free"],
    ["ended an hour ago", -60 * MINUTE, "free"],
  ])("expiry %s: %s", async (_name, offsetMs, plan) => {
    const doc = subscription({ expiresAt: new Date(NOW.getTime() + offsetMs) });
    expect(await resolver(doc).resolve(signedIn)).toBe(plan);
  });

  test("an inactive record is free even with a future expiry", async () => {
    expect(await resolver(subscription({ active: false })).resolve(signedIn)).toBe("free");
  });

  test("a record with no expiry is never pro", async () => {
    expect(await resolver(subscription({ expiresAt: null })).resolve(signedIn)).toBe("free");
  });

  test("a failed read is free, logged once at ERROR with the uid", async () => {
    const { resolve, subscriptions, log } = resolver(subscription());
    subscriptions.failGet = new Error("firestore down");
    expect(await resolve(signedIn)).toBe("free");
    expect(log.entries).toEqual([
      expect.objectContaining({
        level: "error",
        data: expect.objectContaining({ uid: "user-1" }) as unknown,
      }),
    ]);
  });
});

describe("isProActive", () => {
  test("no record is not pro", () => {
    expect(isProActive(null, NOW)).toBe(false);
  });

  test("the leeway is 15 minutes", () => {
    expect(PRO_LEEWAY_MS).toBe(15 * MINUTE);
  });
});
