import { describe, expect, test, vi } from "vitest";

import {
  RevenueCatError,
  type ProEntitlement,
  type RevenueCatClient,
  type SubscriberSnapshot,
} from "../../src/billing/revenueCat.js";
import { refreshSubscription } from "../../src/service/subscriptionRefresh.js";
import { fakeLogger, memorySubscriptions } from "./fakes.js";

const NOW = new Date("2026-10-23T12:00:00Z");
const T1 = new Date("2026-10-23T11:00:00Z");
const T2 = new Date("2026-10-23T11:00:01Z");

const ACTIVE: ProEntitlement = {
  active: true,
  expiresAt: new Date("2026-11-23T11:00:00Z"),
  product: "monthly",
  willRenew: true,
  billingIssue: false,
  sandbox: true,
  store: "play_store",
};

function setup(answer: () => Promise<SubscriberSnapshot>) {
  const getSubscriber = vi.fn<RevenueCatClient["getSubscriber"]>(answer);
  const subscriptions = memorySubscriptions({ now: () => NOW });
  const log = fakeLogger();
  return { deps: { revenueCat: { getSubscriber }, subscriptions, log }, subscriptions, log, getSubscriber };
}

const snapshot = (fetchedAt: Date, pro: ProEntitlement | null) => () => Promise.resolve({ fetchedAt, pro });

describe("refreshSubscription", () => {
  test("an entitlement is stored and returned", async () => {
    const { deps, subscriptions, getSubscriber } = setup(snapshot(T1, ACTIVE));
    const stored = await refreshSubscription("user-1", "webhook", deps);
    expect(getSubscriber).toHaveBeenCalledWith("user-1");
    expect(stored).toEqual({ ...ACTIVE, fetchedAt: T1, updatedAt: NOW, source: "webhook" });
    expect(subscriptions.docs.get("user-1")).toEqual(stored);
  });

  test("no entitlement and no record writes nothing: users who never paid get no doc", async () => {
    const { deps, subscriptions } = setup(snapshot(T1, null));
    expect(await refreshSubscription("user-1", "sync", deps)).toBeNull();
    expect(subscriptions.docs.size).toBe(0);
  });

  test("no entitlement with a record marks it inactive", async () => {
    const { deps, subscriptions } = setup(snapshot(T2, null));
    await subscriptions.putIfNewer("user-1", { ...ACTIVE, fetchedAt: T1 }, "webhook");
    expect(await refreshSubscription("user-1", "webhook", deps)).toMatchObject({
      active: false,
      expiresAt: null,
      product: null,
      willRenew: false,
      billingIssue: false,
      fetchedAt: T2,
    });
  });

  test("a stale read returns the newer record already stored", async () => {
    const { deps, subscriptions } = setup(snapshot(T1, { ...ACTIVE, product: "monthly" }));
    await subscriptions.putIfNewer("user-1", { ...ACTIVE, product: "yearly", fetchedAt: T2 }, "sync");
    expect(await refreshSubscription("user-1", "webhook", deps)).toMatchObject({
      product: "yearly",
      fetchedAt: T2,
      source: "sync",
    });
  });

  test("a RevenueCat failure propagates and is logged with the uid and kind", async () => {
    const { deps, subscriptions, log } = setup(() =>
      Promise.reject(new RevenueCatError("server", "HTTP 503")),
    );
    await expect(refreshSubscription("user-1", "sync", deps)).rejects.toMatchObject({ kind: "server" });
    expect(subscriptions.docs.size).toBe(0);
    expect(log.entries).toContainEqual(
      expect.objectContaining({
        level: "warn",
        data: expect.objectContaining({ uid: "user-1", kind: "server", source: "sync" }) as unknown,
      }),
    );
  });

  test("the outcome is logged without anything but ids and flags", async () => {
    const { deps, log } = setup(snapshot(T1, ACTIVE));
    await refreshSubscription("user-1", "webhook", deps);
    expect(log.entries).toContainEqual(
      expect.objectContaining({
        level: "info",
        data: { uid: "user-1", source: "webhook", result: "written", active: true, product: "monthly" },
      }),
    );
  });
});
