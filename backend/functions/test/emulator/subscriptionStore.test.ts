import { Timestamp } from "firebase-admin/firestore";
import { describe, expect, test } from "vitest";

import type { ProEntitlement } from "../../src/billing/revenueCat.js";
import { createSubscriptionStore } from "../../src/store/subscriptionStore.js";
import { testDb } from "./helpers.js";

const db = testDb();
const NOW = new Date("2026-10-23T10:00:05Z");
const store = createSubscriptionStore(db, { now: () => NOW });

const T1 = new Date("2026-10-23T10:00:00Z");
const T2 = new Date("2026-10-23T10:00:01Z");

const ACTIVE: ProEntitlement = {
  active: true,
  expiresAt: new Date("2026-11-23T09:00:00Z"),
  product: "monthly",
  willRenew: true,
  billingIssue: false,
  sandbox: false,
  store: "play_store",
};

const record = (fetchedAt: Date, fields: Partial<ProEntitlement> = {}) => ({
  ...ACTIVE,
  ...fields,
  fetchedAt,
});

describe("subscription store", () => {
  test("a user with no record has none", async () => {
    expect(await store.get("user-1")).toBeNull();
  });

  test("a write round-trips every field, with the write time and the path that wrote it", async () => {
    expect(await store.putIfNewer("user-1", record(T1), "webhook")).toBe("written");
    expect(await store.get("user-1")).toEqual({
      ...ACTIVE,
      fetchedAt: T1,
      updatedAt: NOW,
      source: "webhook",
    });
    const raw = (await db.doc("subscriptions/user-1").get()).data();
    expect(raw?.expiresAt).toBeInstanceOf(Timestamp);
    expect(raw?.fetchedAt).toBeInstanceOf(Timestamp);
  });

  test("an entitlement with no expiry is stored as null", async () => {
    await store.putIfNewer("user-1", record(T1, { active: false, expiresAt: null, product: null }), "sync");
    expect(await store.get("user-1")).toMatchObject({ active: false, expiresAt: null, product: null });
  });

  test("an older or equal read never overwrites a newer one", async () => {
    await store.putIfNewer("user-1", record(T2, { product: "yearly" }), "webhook");
    expect(await store.putIfNewer("user-1", record(T1, { product: "monthly" }), "sync")).toBe("stale");
    expect(await store.putIfNewer("user-1", record(T2, { willRenew: false }), "sync")).toBe("stale");
    expect(await store.get("user-1")).toMatchObject({
      product: "yearly",
      willRenew: true,
      source: "webhook",
    });
  });

  test("a newer read replaces the record", async () => {
    await store.putIfNewer("user-1", record(T1), "webhook");
    expect(await store.putIfNewer("user-1", record(T2, { willRenew: false }), "sync")).toBe("written");
    expect(await store.get("user-1")).toMatchObject({ willRenew: false, fetchedAt: T2, source: "sync" });
  });

  test.each([
    ["newer first", [T2, T1]],
    ["older first", [T1, T2]],
  ])("two concurrent writes (%s) leave the newer", async (_name, order) => {
    await Promise.all(
      order.map((at) => store.putIfNewer("user-1", record(at, { willRenew: at === T2 }), "webhook")),
    );
    expect(await store.get("user-1")).toMatchObject({ fetchedAt: T2, willRenew: true });
  });

  test("onlyIfExists never creates a record", async () => {
    expect(
      await store.putIfNewer("user-1", record(T1, { active: false }), "webhook", { onlyIfExists: true }),
    ).toBe("absent");
    expect((await db.doc("subscriptions/user-1").get()).exists).toBe(false);
  });

  test("onlyIfExists updates an existing record", async () => {
    await store.putIfNewer("user-1", record(T1), "webhook");
    expect(
      await store.putIfNewer("user-1", record(T2, { active: false }), "webhook", { onlyIfExists: true }),
    ).toBe("written");
    expect(await store.get("user-1")).toMatchObject({ active: false });
  });

  test("a uid with a slash or percent sign gets its own encoded doc", async () => {
    await store.putIfNewer("a/b", record(T1), "sync");
    await store.putIfNewer("a%2Fb", record(T1, { product: "yearly" }), "sync");
    expect(await store.get("a/b")).toMatchObject({ product: "monthly" });
    expect(await store.get("a%2Fb")).toMatchObject({ product: "yearly" });
  });

  test("delete removes the record and is a no-op when there is none", async () => {
    await store.putIfNewer("user-1", record(T1), "webhook");
    await store.delete("user-1");
    expect(await store.get("user-1")).toBeNull();
    await expect(store.delete("user-1")).resolves.toBeUndefined();
  });

  test("a malformed record is an error, not a missing one", async () => {
    await db.doc("subscriptions/user-1").set({ active: "yes" });
    await expect(store.get("user-1")).rejects.toThrow();
  });
});
