import { describe, expect, test } from "vitest";

import { createTranslationCache, type CacheEntry } from "../../src/store/translationCache.js";
import { fakeLogger } from "../unit/fakes.js";
import { dateAt, DAY_MS, testDb } from "./helpers.js";

const db = testDb();
const T0 = new Date("2026-10-22T12:00:00Z");
const entry: CacheEntry = {
  source: "bo",
  target: "en",
  sourceText: "བཀྲ་ཤིས",
  translation: "Tashi Delek",
  provider: "azure",
  generation: 1,
};

function cacheAt(now: Date) {
  return createTranslationCache(db, { now: () => now, log: fakeLogger() });
}

describe("translation cache", () => {
  test("a miss returns null", async () => {
    expect(await cacheAt(T0).get("missing", 180)).toBeNull();
  });

  test("a stored entry is read from Firestore by a fresh instance, then from memory", async () => {
    await cacheAt(T0).put("k1", entry, 180);
    const cache = cacheAt(T0);
    expect(await cache.get("k1", 180)).toEqual({ translation: "Tashi Delek", provider: "azure", tier: "l2" });
    expect(await cache.get("k1", 180)).toEqual({ translation: "Tashi Delek", provider: "azure", tier: "l1" });
  });

  test("the writer's own instance serves its entry from memory", async () => {
    const cache = cacheAt(T0);
    await cache.put("k1", entry, 180);
    expect((await cache.get("k1", 180))?.tier).toBe("l1");
  });

  test("stores the source text, direction, provider and an expiry", async () => {
    await cacheAt(T0).put("k1", entry, 180);
    const data = (await db.doc("translations/k1").get()).data();
    expect(data).toMatchObject({
      source: "bo",
      target: "en",
      sourceText: "བཀྲ་ཤིས",
      translation: "Tashi Delek",
      provider: "azure",
      generation: 1,
    });
    expect(await dateAt(db, "translations/k1", "createdAt")).toEqual(T0);
    expect(await dateAt(db, "translations/k1", "expireAt")).toEqual(new Date(T0.getTime() + 180 * DAY_MS));
  });

  test("an expired entry that TTL has not deleted yet is a miss", async () => {
    await cacheAt(T0).put("k1", entry, 1);
    expect(await cacheAt(new Date(T0.getTime() + 2 * DAY_MS)).get("k1", 1)).toBeNull();
  });

  test("a hit with under 30 days left extends the expiry", async () => {
    await cacheAt(T0).put("k1", entry, 180);
    const later = new Date(T0.getTime() + 160 * DAY_MS);
    await cacheAt(later).get("k1", 180);
    expect(await dateAt(db, "translations/k1", "expireAt")).toEqual(new Date(later.getTime() + 180 * DAY_MS));
  });

  test("a hit with 30 days or more left does not write", async () => {
    await cacheAt(T0).put("k1", entry, 180);
    const before = (await db.doc("translations/k1").get()).updateTime;
    await cacheAt(new Date(T0.getTime() + 10 * DAY_MS)).get("k1", 180);
    const after = (await db.doc("translations/k1").get()).updateTime;
    expect(after).toEqual(before);
  });

  test("a malformed doc is a miss and is logged", async () => {
    await db.doc("translations/bad").set({ translation: "", provider: "deepl" });
    const log = fakeLogger();
    const cache = createTranslationCache(db, { now: () => T0, log });
    expect(await cache.get("bad", 180)).toBeNull();
    expect(log.entries).toContainEqual(expect.objectContaining({ level: "warn" }));
  });
});
