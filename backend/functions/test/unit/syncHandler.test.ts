import { describe, expect, test, vi } from "vitest";

import { RevenueCatError } from "../../src/billing/revenueCat.js";
import { createRateLimiter } from "../../src/service/rateLimiter.js";
import { handleSyncSubscription, type SyncDeps } from "../../src/service/syncHandler.js";
import type { StoredSubscription } from "../../src/store/subscriptionStore.js";
import { fakeLogger, reasonOf, rejection } from "./fakes.js";

const NOW = new Date("2026-10-23T12:00:00Z");

const ACTIVE: StoredSubscription = {
  active: true,
  expiresAt: new Date("2026-11-23T12:00:00Z"),
  product: "monthly",
  willRenew: true,
  billingIssue: false,
  sandbox: false,
  store: "play_store",
  fetchedAt: NOW,
  updatedAt: NOW,
  source: "sync",
};

function setup(refresh: SyncDeps["refresh"] = () => Promise.resolve(ACTIVE)) {
  const deps = {
    refresh: vi.fn(refresh),
    rateLimiter: createRateLimiter({ now: () => NOW.getTime() }),
    log: fakeLogger(),
    now: () => NOW,
  } satisfies SyncDeps;
  return deps;
}

const google = (uid = "user-1") => ({
  data: null,
  auth: { uid, token: { firebase: { sign_in_provider: "google.com", identities: { "google.com": ["1"] } } } },
});

describe("handleSyncSubscription", () => {
  test("refreshes the caller and answers pro for an active record", async () => {
    const deps = setup();
    expect(await handleSyncSubscription(google(), deps)).toEqual({ plan: "pro" });
    expect(deps.refresh).toHaveBeenCalledWith("user-1");
  });

  test("no record is free", async () => {
    expect(
      await handleSyncSubscription(
        google(),
        setup(() => Promise.resolve(null)),
      ),
    ).toEqual({ plan: "free" });
  });

  test("an expired record is free", async () => {
    const expired = { ...ACTIVE, expiresAt: new Date(NOW.getTime() - 3_600_000) };
    expect(
      await handleSyncSubscription(
        google(),
        setup(() => Promise.resolve(expired)),
      ),
    ).toEqual({
      plan: "free",
    });
  });

  test("no user is unauthenticated", async () => {
    const deps = setup();
    const error = await rejection(handleSyncSubscription({ data: null, auth: undefined }, deps));
    expect(reasonOf(error)).toBe("unauthenticated");
    expect(deps.refresh).not.toHaveBeenCalled();
  });

  test("a guest is guest_account", async () => {
    const deps = setup();
    const guest = {
      data: null,
      auth: { uid: "user-1", token: { firebase: { sign_in_provider: "anonymous", identities: {} } } },
    };
    const error = await rejection(handleSyncSubscription(guest, deps));
    expect(error.code).toBe("failed-precondition");
    expect(reasonOf(error)).toBe("guest_account");
    expect(deps.refresh).not.toHaveBeenCalled();
  });

  test("the 7th call in a minute is rate_limited", async () => {
    const deps = setup();
    for (let i = 0; i < 6; i++) await handleSyncSubscription(google(), deps);
    const error = await rejection(handleSyncSubscription(google(), deps));
    expect(error.code).toBe("resource-exhausted");
    expect(reasonOf(error)).toBe("rate_limited");
    expect(error.details).toMatchObject({ retryAfterSec: 60 });
    expect(deps.refresh).toHaveBeenCalledTimes(6);
    await expect(handleSyncSubscription(google("user-2"), deps)).resolves.toEqual({ plan: "pro" });
  });

  test("RevenueCat unreachable is billing_unavailable", async () => {
    const deps = setup(() => Promise.reject(new RevenueCatError("server", "HTTP 503")));
    const error = await rejection(handleSyncSubscription(google(), deps));
    expect(error.code).toBe("unavailable");
    expect(reasonOf(error)).toBe("billing_unavailable");
  });

  test("any other failure is internal", async () => {
    const deps = setup(() => Promise.reject(new Error("firestore down")));
    expect((await rejection(handleSyncSubscription(google(), deps))).code).toBe("internal");
  });
});
