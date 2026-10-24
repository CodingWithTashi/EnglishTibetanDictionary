import { describe, expect, test } from "vitest";

import { createBudgetStore } from "../../src/store/budgetStore.js";
import { testDb } from "./helpers.js";

const db = testDb();
const T0 = new Date("2026-10-22T12:00:00Z").getTime();

function budgetAt(clock: { now: number }, random: () => number = Math.random) {
  return createBudgetStore(db, { now: () => new Date(clock.now), random });
}

describe("budget store", () => {
  test("sums characters across shards", async () => {
    const clock = { now: T0 };
    let shard = 0;
    const writer = budgetAt(clock, () => (shard++ % 10) / 10);
    await writer.add(100);
    await writer.add(100);
    await writer.add(100);
    expect((await db.collection("budget/2026-10-22/shards").get()).size).toBe(3);
    const reader = budgetAt(clock);
    expect(await reader.isOver(300)).toBe(true);
    expect(await budgetAt(clock).isOver(301)).toBe(false);
  });

  test("re-reads the shards only after 30 s", async () => {
    const clock = { now: T0 };
    const reader = budgetAt(clock);
    expect(await reader.isOver(1_000)).toBe(false);
    await budgetAt(clock).add(2_000);
    clock.now += 29_999;
    expect(await reader.isOver(1_000)).toBe(false);
    clock.now += 1;
    expect(await reader.isOver(1_000)).toBe(true);
  });

  test("an instance counts its own additions at once", async () => {
    const clock = { now: T0 };
    const budget = budgetAt(clock);
    expect(await budget.isOver(400)).toBe(false);
    await budget.add(500);
    expect(await budget.isOver(400)).toBe(true);
  });

  test("a shard whose chars is not a number counts as zero", async () => {
    // A field edited in the Firestore console becomes a string unless the type is changed.
    await db.doc("budget/2026-10-22/shards/0").set({ chars: "500" });
    await db.doc("budget/2026-10-22/shards/1").set({ chars: 100 });
    const clock = { now: T0 };
    expect(await budgetAt(clock).isOver(100)).toBe(true);
    expect(await budgetAt(clock).isOver(101)).toBe(false);
  });

  test("a new UTC day starts from zero", async () => {
    const clock = { now: T0 };
    const budget = budgetAt(clock);
    await budget.add(500);
    expect(await budget.isOver(400)).toBe(true);
    clock.now = new Date("2026-10-23T00:00:01Z").getTime();
    expect(await budget.isOver(400)).toBe(false);
  });
});
