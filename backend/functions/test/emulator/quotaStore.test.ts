import { describe, expect, test } from "vitest";

import { createQuotaStore, DailyLimitError } from "../../src/store/quotaStore.js";
import { dateAt, DAY_MS, testDb } from "./helpers.js";

const db = testDb();
const T0 = new Date("2026-10-22T12:00:00Z");
const quota = createQuotaStore(db, { now: () => T0 });

describe("quota store", () => {
  test("a user with no usage today has used nothing", async () => {
    expect(await quota.read("user-1", "2026-10-22")).toEqual({ count: 0, chars: 0 });
  });

  test("reserve counts one translation and its characters", async () => {
    expect(await quota.reserve("user-1", "2026-10-22", 10, 7)).toEqual({ count: 1, chars: 7 });
    expect(await quota.reserve("user-1", "2026-10-22", 10, 5)).toEqual({ count: 2, chars: 12 });
    expect(await quota.read("user-1", "2026-10-22")).toEqual({ count: 2, chars: 12 });
    expect(await quota.read("user-1", "2026-10-23")).toEqual({ count: 0, chars: 0 });
    expect(await quota.read("user-2", "2026-10-22")).toEqual({ count: 0, chars: 0 });
  });

  test("the usage doc records the user, the day and a 7-day expiry", async () => {
    await quota.reserve("user-1", "2026-10-22", 10, 7);
    const data = (await db.doc("dailyUsage/user-1_2026-10-22").get()).data();
    expect(data).toMatchObject({ uid: "user-1", day: "2026-10-22", count: 1, chars: 7 });
    expect(await dateAt(db, "dailyUsage/user-1_2026-10-22", "expireAt")).toEqual(
      new Date(T0.getTime() + 7 * DAY_MS),
    );
  });

  test("a user at the limit gets DailyLimitError", async () => {
    await quota.reserve("user-1", "2026-10-22", 1, 5);
    const error = await quota.reserve("user-1", "2026-10-22", 1, 5).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(DailyLimitError);
    expect(error).toMatchObject({ limit: 1, used: 1 });
  });

  test("11 concurrent reservations against a limit of 10: exactly 10 succeed", async () => {
    const results = await Promise.allSettled(
      Array.from({ length: 11 }, () => quota.reserve("user-1", "2026-10-22", 10, 5)),
    );
    const rejected = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(10);
    expect(rejected).toHaveLength(1);
    expect(rejected[0]?.reason).toBeInstanceOf(DailyLimitError);
    expect(await quota.read("user-1", "2026-10-22")).toEqual({ count: 10, chars: 50 });
  });

  test("refund gives a reservation back", async () => {
    await quota.reserve("user-1", "2026-10-22", 10, 7);
    await quota.reserve("user-1", "2026-10-22", 10, 5);
    await quota.refund("user-1", "2026-10-22", 5);
    expect(await quota.read("user-1", "2026-10-22")).toEqual({ count: 1, chars: 7 });
  });

  test("deleteAll removes only that user's docs, across days", async () => {
    for (const day of ["2026-10-20", "2026-10-21", "2026-10-22"]) await quota.reserve("user-1", day, 10, 4);
    await quota.reserve("user-2", "2026-10-22", 10, 6);
    expect(await quota.deleteAll("user-1")).toBe(3);
    for (const day of ["2026-10-20", "2026-10-21", "2026-10-22"]) {
      expect(await quota.read("user-1", day)).toEqual({ count: 0, chars: 0 });
    }
    expect(await quota.read("user-2", "2026-10-22")).toEqual({ count: 1, chars: 6 });
  });

  test("deleteAll with nothing to delete is 0", async () => {
    expect(await quota.deleteAll("user-1")).toBe(0);
  });

  test("a uid with a slash still maps to one document", async () => {
    await quota.reserve("tenant/abc", "2026-10-22", 10, 3);
    expect(await quota.read("tenant/abc", "2026-10-22")).toEqual({ count: 1, chars: 3 });
  });
});
