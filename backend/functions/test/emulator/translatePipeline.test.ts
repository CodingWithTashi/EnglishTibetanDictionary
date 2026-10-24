import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, test, vi } from "vitest";

import { DEFAULT_CONFIG, type RuntimeConfig } from "../../src/config/runtimeConfig.js";
import { AllProvidersFailedError, type Router } from "../../src/providers/router.js";
import { createRateLimiter } from "../../src/service/rateLimiter.js";
import { createPlanResolver } from "../../src/service/plan.js";
import { handleStatus, handleTranslate, type Deps } from "../../src/service/translateHandler.js";
import { createBudgetStore } from "../../src/store/budgetStore.js";
import { cacheKey } from "../../src/store/cacheKey.js";
import { createQuotaStore } from "../../src/store/quotaStore.js";
import { createSubscriptionStore } from "../../src/store/subscriptionStore.js";
import { createTranslationCache } from "../../src/store/translationCache.js";
import { fakeLogger, reasonOf, rejection } from "../unit/fakes.js";
import { testDb } from "./helpers.js";

// The handler wired to the real Firestore stores; only the router and the config source are fakes.

const db = testDb();
const NOW = new Date("2026-10-22T19:00:00Z"); // 2026-10-23 01:00 in Thimphu; UTC day 2026-10-22
const USAGE_DOC = "dailyUsage/user-1_2026-10-23";

/** A router that answers "Translation #n" for its n-th call. */
function countingRouter() {
  let calls = 0;
  return vi.fn<Router["translate"]>(() => {
    calls++;
    return Promise.resolve({ translation: `Translation #${calls}`, provider: "azure" });
  });
}

/** One function instance: its own memory cache, budget cache and rate limiter over the shared emulator. */
function instance(options: { config?: Partial<RuntimeConfig>; router?: Router["translate"] } = {}) {
  const config: RuntimeConfig = { ...DEFAULT_CONFIG, ...options.config };
  const log = fakeLogger();
  const now = () => NOW;
  const router = options.router ?? countingRouter();
  const deps = {
    config: { get: () => Promise.resolve(config) },
    cache: createTranslationCache(db, { log, now }),
    quota: createQuotaStore(db, { now }),
    budget: createBudgetStore(db, { now }),
    router: { translate: router },
    rateLimiter: createRateLimiter({ now: () => NOW.getTime() }),
    resolvePlan: () => Promise.resolve("free" as const),
    log,
    now,
  } satisfies Deps;
  return { deps, router, log };
}

/** [deps] with the real plan resolver over `subscriptions/{uid}`. */
function withSubscriptions(deps: Deps): Deps {
  const subscriptions = createSubscriptionStore(db, { now: () => NOW });
  return { ...deps, resolvePlan: createPlanResolver({ subscriptions, now: () => NOW, log: deps.log }) };
}

/** Stores a monthly Pro record for user-1 that ends at [expiresAt]. */
async function storePro(expiresAt: Date): Promise<void> {
  await createSubscriptionStore(db).putIfNewer(
    "user-1",
    {
      active: true,
      expiresAt,
      product: "monthly",
      willRenew: true,
      billingIssue: false,
      sandbox: true,
      store: "play_store",
      fetchedAt: new Date(NOW.getTime() - 60_000),
    },
    "webhook",
  );
}

const bo = (text: string) => ({
  data: { text, source: "bo", target: "en", timeZone: "Asia/Thimphu" },
  auth: { uid: "user-1" },
});

/** The same request, signed in as [provider] with [identities] in the ID token, the way Firebase Auth sends it. */
const signedIn = (data: unknown, signInProvider: string, identities: Record<string, unknown>) => ({
  data,
  auth: { uid: "user-1", token: { firebase: { sign_in_provider: signInProvider, identities } } },
});
const boText = (text: string) => ({ text, source: "bo", target: "en", timeZone: "Asia/Thimphu" });

async function usageDoc(): Promise<Record<string, unknown> | undefined> {
  return (await db.doc(USAGE_DOC).get()).data();
}

describe("translate pipeline on Firestore", () => {
  test("usage carries over when a guest links Google: 3 used of the guest's 3 become 3 of 10", async () => {
    const { deps } = instance();
    const real = withSubscriptions(deps);
    const guest = (text: string) => signedIn(boText(text), "anonymous", {});
    for (const text of ["ཀ", "ཁ", "ག"]) {
      expect((await handleTranslate(guest(text), real)).quota).toMatchObject({ plan: "guest", limit: 3 });
    }
    expect(reasonOf(await rejection(handleTranslate(guest("ང"), real)))).toBe("daily_limit");

    const google = (data: unknown) => signedIn(data, "google.com", { "google.com": ["1"] });
    expect(await handleStatus(google({ timeZone: "Asia/Thimphu" }), real)).toMatchObject({
      plan: "free",
      used: 3,
      limit: 10,
      remaining: 7,
    });
    expect((await handleTranslate(google(boText("ང")), real)).quota).toMatchObject({ plan: "free", used: 4 });
  });

  test("a miss is translated and counted; the same text on a fresh instance is a free Firestore hit", async () => {
    const first = instance();
    const miss = await handleTranslate(bo("བཀྲ་ཤིས"), first.deps);
    expect(miss).toMatchObject({ translation: "Translation #1", cached: false, quota: { used: 1 } });

    const second = instance();
    const hit = await handleTranslate(bo("བཀྲ་ཤིས"), second.deps);
    expect(hit).toMatchObject({ translation: "Translation #1", cached: true, provider: "azure" });
    expect(hit.quota).toMatchObject({ used: 1, remaining: 9 });
    expect(second.router).not.toHaveBeenCalled();
    expect(second.log.entries).toContainEqual(
      expect.objectContaining({
        message: "translate",
        data: expect.objectContaining({ outcome: "cached", tier: "l2" }) as unknown,
      }),
    );
    expect(await usageDoc()).toMatchObject({ count: 1, chars: 7 });
  });

  test("a text with a lone surrogate is cached like any other, so a repeat is free", async () => {
    const first = instance();
    await handleTranslate(bo("བཀྲ་ཤིས\uD800"), first.deps);
    expect(first.log.entries).not.toContainEqual(expect.objectContaining({ message: "cache write failed" }));

    const second = instance();
    expect(await handleTranslate(bo("བཀྲ་ཤིས\uD800"), second.deps)).toMatchObject({
      cached: true,
      quota: { used: 1 },
    });
    expect(second.router).not.toHaveBeenCalled();
  });

  test("the quota in each response matches the dailyUsage doc", async () => {
    const { deps } = instance();
    for (const text of ["ཀ", "ཀཁ", "ཀཁག"]) {
      const { quota } = await handleTranslate(bo(text), deps);
      const count = (await usageDoc())?.count as number;
      expect(quota).toMatchObject({ used: count, remaining: DEFAULT_CONFIG.free.dailyLimit - count });
    }
    expect(await usageDoc()).toMatchObject({ count: 3, chars: 6 });
  });

  test("11 different texts in parallel at a limit of 10: 10 translate, 1 is daily_limit, none internal", async () => {
    const { deps, router } = instance();
    const texts = Array.from({ length: 11 }, (_, i) => `ཀ${"ཁ".repeat(i)}`);
    const results = await Promise.allSettled(texts.map((text) => handleTranslate(bo(text), deps)));

    const refused = results.flatMap((r) => (r.status === "rejected" ? [r.reason as unknown] : []));
    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(10);
    expect(refused).toHaveLength(1);
    expect(refused[0]).toBeInstanceOf(HttpsError);
    expect(refused[0]).toMatchObject({ code: "resource-exhausted", details: { reason: "daily_limit" } });
    expect(router).toHaveBeenCalledTimes(10);
    expect(await usageDoc()).toMatchObject({ count: 10 });
  });

  test("a router failure is provider_unavailable and refunds the usage doc to its earlier count and characters", async () => {
    const router = vi
      .fn<Router["translate"]>()
      .mockResolvedValueOnce({ translation: "Tashi Delek", provider: "azure" })
      .mockRejectedValueOnce(new AllProvidersFailedError([{ provider: "azure", outcome: "transient" }]));
    const { deps } = instance({ router });
    await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    expect(await usageDoc()).toMatchObject({ count: 1, chars: 7 });

    const error = await rejection(handleTranslate(bo("ཐུགས་རྗེ་ཆེ"), deps));
    expect(error.code).toBe("unavailable");
    expect(reasonOf(error)).toBe("provider_unavailable");
    expect(await usageDoc()).toMatchObject({ count: 1, chars: 7 });
    expect((await db.doc(`translations/${cacheKey(1, "bo", "en", "ཐུགས་རྗེ་ཆེ")}`).get()).exists).toBe(false);
  });

  test("a cached text is still served after the user reaches the daily limit", async () => {
    const config = { free: { dailyLimit: 2, maxChars: 100 } };
    const { deps } = instance({ config });
    await handleTranslate(bo("ཀ"), deps);
    await handleTranslate(bo("ཁ"), deps);
    const limited = await rejection(handleTranslate(bo("ག"), deps));
    expect(limited.details).toEqual({
      reason: "daily_limit",
      limit: 2,
      resetsAt: "2026-10-23T18:00:00.000Z",
      plan: "free",
      freeLimit: 2,
    });

    const hit = await handleTranslate(bo("ཀ"), instance({ config }).deps);
    expect(hit).toMatchObject({ cached: true, quota: { used: 2, limit: 2, remaining: 0 } });
    expect(await usageDoc()).toMatchObject({ count: 2, chars: 2 });
  });

  test("over the global daily character budget is service_budget, and no dailyUsage doc is written", async () => {
    await db.doc("budget/2026-10-22/shards/3").set({ chars: 300, requests: 30 });
    const { deps, router } = instance({ config: { globalDailyCharBudget: 300 } });
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), deps));
    expect(error.code).toBe("resource-exhausted");
    expect(reasonOf(error)).toBe("service_budget");
    expect(router).not.toHaveBeenCalled();
    expect((await db.collection("dailyUsage").get()).empty).toBe(true);
  });

  test("a translation adds its characters to today's budget shards", async () => {
    const { deps } = instance();
    await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    const shards = await db.collection("budget/2026-10-22/shards").get();
    const total = shards.docs.reduce((sum, doc) => sum + (doc.get("chars") as number), 0);
    expect(total).toBe(7);
  });

  test("the same text twice in parallel is translated and counted twice (README: Known limits)", async () => {
    // Both requests miss the cache before either has written it. Pinned, not fixed: a double tap costs the
    // user one extra translation; see "Known limits" in backend/README.md.
    const { deps, router } = instance();
    const [a, b] = await Promise.all([
      handleTranslate(bo("བཀྲ་ཤིས"), deps),
      handleTranslate(bo("བཀྲ་ཤིས"), deps),
    ]);
    expect([a.cached, b.cached]).toEqual([false, false]);
    expect(router).toHaveBeenCalledTimes(2);
    expect(await usageDoc()).toMatchObject({ count: 2, chars: 14 });
  });
});

describe("Pro on Firestore", () => {
  const MONTH_LATER = new Date(NOW.getTime() + 30 * 86_400_000);

  test("a Pro record gives 300 a day and 500 characters", async () => {
    await storePro(MONTH_LATER);
    const deps = withSubscriptions(instance().deps);
    expect(
      await handleStatus({ data: { timeZone: "Asia/Thimphu" }, auth: { uid: "user-1" } }, deps),
    ).toMatchObject({
      plan: "pro",
      limit: 300,
      maxChars: 500,
    });
    const response = await handleTranslate(bo("ཀ".repeat(450)), deps);
    expect(response.quota).toMatchObject({ plan: "pro", used: 1, limit: 300, maxChars: 500 });
  });

  test("an expired Pro record gets the free limits", async () => {
    await storePro(new Date(NOW.getTime() - 16 * 60_000));
    const deps = withSubscriptions(instance().deps);
    const error = await rejection(handleTranslate(bo("ཀ".repeat(450)), deps));
    expect(reasonOf(error)).toBe("text_too_long");
    expect((await handleTranslate(bo("བཀྲ་ཤིས"), deps)).quota).toMatchObject({ plan: "free", limit: 10 });
  });

  test("Pro at its cap is refused with plan pro", async () => {
    await storePro(MONTH_LATER);
    await db.doc(USAGE_DOC).set({ uid: "user-1", day: "2026-10-23", count: 300, chars: 3_000 });
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), withSubscriptions(instance().deps)));
    expect(error.code).toBe("resource-exhausted");
    expect(error.details).toMatchObject({ reason: "daily_limit", plan: "pro", limit: 300 });
  });
});
