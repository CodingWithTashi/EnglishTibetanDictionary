import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, test, vi } from "vitest";

import { DEFAULT_CONFIG, type RuntimeConfig } from "../../src/config/runtimeConfig.js";
import { AllProvidersFailedError, type Router } from "../../src/providers/router.js";
import { createRateLimiter } from "../../src/service/rateLimiter.js";
import { handleStatus, handleTranslate, type Deps } from "../../src/service/translateHandler.js";
import { cacheKey } from "../../src/store/cacheKey.js";
import { fakeLogger, memoryBudget, memoryCache, memoryQuota } from "./fakes.js";

const NOW = new Date("2026-10-22T19:00:00Z"); // 2026-10-23 01:00 in Thimphu
const TIBETAN_100 = "ཀ".repeat(100);

function setup(overrides: { config?: Partial<RuntimeConfig>; router?: Router } = {}) {
  const config: RuntimeConfig = { ...DEFAULT_CONFIG, ...overrides.config };
  const translateFn = vi.fn<Router["translate"]>(() =>
    Promise.resolve({ translation: "Tashi Delek", provider: "azure" }),
  );
  const deps = {
    config: { get: () => Promise.resolve(config) },
    cache: memoryCache(),
    quota: memoryQuota(),
    budget: memoryBudget(),
    router: overrides.router ?? { translate: translateFn },
    rateLimiter: createRateLimiter({ now: () => NOW.getTime() }),
    resolvePlan: () => Promise.resolve("free" as const),
    log: fakeLogger(),
    now: () => NOW,
  } satisfies Deps;
  return { deps, translateFn };
}

/** `uid: null` sends the request signed out. */
const request = (data: unknown, uid: string | null = "user-1") => ({
  data,
  auth: uid === null ? undefined : { uid },
});

const bo = (text: string, extra: Record<string, unknown> = {}) =>
  request({ text, source: "bo", target: "en", timeZone: "Asia/Thimphu", ...extra });

async function rejection(promise: Promise<unknown>): Promise<HttpsError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof HttpsError) return error;
    throw error;
  }
  throw new Error("expected an HttpsError");
}

function reasonOf(error: HttpsError): unknown {
  return (error.details as { reason?: unknown } | undefined)?.reason;
}

describe("handleTranslate: validation", () => {
  test("requires a signed-in user", async () => {
    const { deps } = setup();
    const error = await rejection(
      handleTranslate(request({ text: "ཀ", source: "bo", target: "en" }, null), deps),
    );
    expect(error.code).toBe("unauthenticated");
  });

  test("the kill switch turns translation off", async () => {
    const { deps } = setup({ config: { enabled: false } });
    const error = await rejection(handleTranslate(bo("ཀ"), deps));
    expect(error.code).toBe("failed-precondition");
    expect(reasonOf(error)).toBe("translation_disabled");
  });

  test.each([
    ["no data", undefined],
    ["text is not a string", { text: 5, source: "bo", target: "en" }],
    ["unknown language", { text: "x", source: "zh", target: "en" }],
    ["time zone too long", { text: "ཀ", source: "bo", target: "en", timeZone: "x".repeat(65) }],
  ])("a malformed request (%s) is bad_request", async (_name, data) => {
    const { deps } = setup();
    const error = await rejection(handleTranslate(request(data), deps));
    expect(error.code).toBe("invalid-argument");
    expect(reasonOf(error)).toBe("bad_request");
  });

  test("a direction with no route is unsupported_pair", async () => {
    const { deps } = setup();
    for (const [source, target] of [
      ["bo", "dz"],
      ["en", "en"],
    ]) {
      const error = await rejection(handleTranslate(request({ text: "ཀ", source, target }), deps));
      expect(error.code).toBe("invalid-argument");
      expect(reasonOf(error)).toBe("unsupported_pair");
    }
  });

  test("whitespace only is empty_text", async () => {
    const { deps } = setup();
    expect(reasonOf(await rejection(handleTranslate(bo(" \n\t\u200B "), deps)))).toBe("empty_text");
  });

  test("Wylie as a Tibetan source is not_tibetan_script", async () => {
    const { deps } = setup();
    expect(reasonOf(await rejection(handleTranslate(bo("bkra shis"), deps)))).toBe("not_tibetan_script");
  });

  test("Tibetan as an English source is wrong_script", async () => {
    const { deps } = setup();
    const error = await rejection(
      handleTranslate(request({ text: "བཀྲ་ཤིས", source: "en", target: "bo" }), deps),
    );
    expect(reasonOf(error)).toBe("wrong_script");
  });

  test("100 code points pass and 101 are text_too_long", async () => {
    const { deps } = setup();
    await expect(handleTranslate(bo(TIBETAN_100), deps)).resolves.toMatchObject({ cached: false });
    const error = await rejection(handleTranslate(bo(TIBETAN_100 + "ཁ"), deps));
    expect(error.code).toBe("invalid-argument");
    expect(error.details).toEqual({ reason: "text_too_long", maxChars: 100, length: 101 });
  });

  test("the limit counts normalised text", async () => {
    const { deps } = setup();
    await expect(handleTranslate(bo(`  ${TIBETAN_100}\u200B  `), deps)).resolves.toMatchObject({
      cached: false,
    });
  });

  test("raw input over 5,000 characters is text_too_long before any processing", async () => {
    const { deps } = setup();
    const error = await rejection(handleTranslate(bo(" ".repeat(5_001) + "ཀ"), deps));
    expect(reasonOf(error)).toBe("text_too_long");
  });
});

describe("handleTranslate: cache and quota", () => {
  test("a miss translates, caches, counts once and reports the quota", async () => {
    const { deps, translateFn } = setup();
    const response = await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    expect(response).toEqual({
      translation: "Tashi Delek",
      source: "bo",
      target: "en",
      cached: false,
      provider: "azure",
      quota: {
        plan: "free",
        used: 1,
        limit: 10,
        remaining: 9,
        resetsAt: "2026-10-23T18:00:00.000Z",
        maxChars: 100,
      },
    });
    expect(translateFn).toHaveBeenCalledWith(["azure", "google"], "བཀྲ་ཤིས", "bo", "en");
    expect(deps.quota.reserves).toBe(1);
    expect(deps.quota.usage.get("user-1_2026-10-23")).toEqual({ count: 1, chars: 7 });
    expect(deps.cache.entries.get(cacheKey(1, "bo", "en", "བཀྲ་ཤིས"))).toEqual({
      source: "bo",
      target: "en",
      sourceText: "བཀྲ་ཤིས",
      translation: "Tashi Delek",
      provider: "azure",
      generation: 1,
    });
    expect(deps.budget.total).toBe(7);
  });

  test("a cache hit is free, even for a user at the daily limit", async () => {
    const { deps, translateFn } = setup();
    deps.quota.usage.set("user-1_2026-10-23", { count: 10, chars: 500 });
    await deps.cache.put(
      cacheKey(1, "bo", "en", "བཀྲ་ཤིས"),
      {
        source: "bo",
        target: "en",
        sourceText: "བཀྲ་ཤིས",
        translation: "Tashi Delek",
        provider: "google",
        generation: 1,
      },
      180,
    );
    const response = await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    expect(response).toMatchObject({ cached: true, provider: "google", quota: { used: 10, remaining: 0 } });
    expect(deps.quota.reserves).toBe(0);
    expect(translateFn).not.toHaveBeenCalled();
  });

  test("the same request twice is translated once and counted once", async () => {
    const { deps, translateFn } = setup();
    await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    const second = await handleTranslate(bo(" བཀྲ་ཤིས "), deps);
    expect(second).toMatchObject({ cached: true, quota: { used: 1 } });
    expect(translateFn).toHaveBeenCalledTimes(1);
  });

  test("a miss at the daily limit is daily_limit with the reset time, and nothing is translated", async () => {
    const { deps, translateFn } = setup();
    deps.quota.usage.set("user-1_2026-10-23", { count: 10, chars: 500 });
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), deps));
    expect(error.code).toBe("resource-exhausted");
    expect(error.details).toEqual({ reason: "daily_limit", limit: 10, resetsAt: "2026-10-23T18:00:00.000Z" });
    expect(translateFn).not.toHaveBeenCalled();
  });

  test("over the global budget is service_budget, before any reservation", async () => {
    const { deps } = setup({ config: { globalDailyCharBudget: 5 } });
    await deps.budget.add(5);
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), deps));
    expect(error.code).toBe("resource-exhausted");
    expect(reasonOf(error)).toBe("service_budget");
    expect(deps.quota.reserves).toBe(0);
  });

  test("a provider failure refunds the reservation and is provider_unavailable", async () => {
    const { deps } = setup({
      router: {
        translate: () =>
          Promise.reject(new AllProvidersFailedError([{ provider: "azure", outcome: "auth" }])),
      },
    });
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), deps));
    expect(error.code).toBe("unavailable");
    expect(reasonOf(error)).toBe("provider_unavailable");
    expect(deps.quota.refunds).toBe(1);
    expect(deps.quota.usage.get("user-1_2026-10-23")).toEqual({ count: 0, chars: 0 });
    expect(deps.cache.entries.size).toBe(0);
  });

  test("a failed cache write still returns the translation, counted once, and logs an error", async () => {
    const { deps } = setup();
    deps.cache.failPut = new Error("firestore down");
    const response = await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    expect(response).toMatchObject({ translation: "Tashi Delek", cached: false, quota: { used: 1 } });
    expect(deps.quota.refunds).toBe(0);
    expect(deps.log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
  });

  test("a failed cache read is treated as a miss", async () => {
    const { deps } = setup();
    deps.cache.get = () => Promise.reject(new Error("firestore slow"));
    await expect(handleTranslate(bo("བཀྲ་ཤིས"), deps)).resolves.toMatchObject({ cached: false });
  });

  test("the 21st request in a minute is rate_limited", async () => {
    const { deps } = setup();
    for (let i = 0; i < 20; i++) await handleTranslate(bo("བཀྲ་ཤིས"), deps);
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), deps));
    expect(error.code).toBe("resource-exhausted");
    expect(error.details).toEqual({ reason: "rate_limited", retryAfterSec: 60 });
  });

  test("an unknown time zone counts against the UTC day", async () => {
    const { deps } = setup();
    const response = await handleTranslate(bo("བཀྲ་ཤིས", { timeZone: "Not/AZone" }), deps);
    expect(response.quota.resetsAt).toBe("2026-10-23T00:00:00.000Z");
    expect(deps.quota.usage.get("user-1_2026-10-22")).toEqual({ count: 1, chars: 7 });
  });

  test("an unexpected failure is internal and logged", async () => {
    const { deps } = setup();
    deps.quota.reserve = () => Promise.reject(new Error("transaction aborted"));
    const error = await rejection(handleTranslate(bo("བཀྲ་ཤིས"), deps));
    expect(error.code).toBe("internal");
    expect(deps.log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
  });

  test("logs never contain the text", async () => {
    const { deps } = setup();
    const secret = "ངའི་གསང་བའི་ཚིག";
    await handleTranslate(bo(secret), deps);
    await handleTranslate(bo(secret), deps);
    deps.cache.failPut = new Error("down");
    await handleTranslate(bo(`${secret}་ཀ`), deps);
    await handleTranslate(bo("bkra shis"), deps).catch(() => undefined);
    expect(deps.log.entries.length).toBeGreaterThan(0);
    const logged = JSON.stringify(deps.log.entries);
    expect(logged).not.toContain(secret);
    expect(logged).not.toContain("bkra shis");
    expect(logged).not.toContain("Tashi Delek");
  });
});

describe("handleStatus", () => {
  test("reports the plan, today's usage and the open directions", async () => {
    const { deps } = setup({ config: { routes: { ...DEFAULT_CONFIG.routes, "en-dz": [] } } });
    deps.quota.usage.set("user-1_2026-10-23", { count: 3, chars: 120 });
    expect(await handleStatus(request({ timeZone: "Asia/Thimphu" }), deps)).toEqual({
      enabled: true,
      plan: "free",
      used: 3,
      limit: 10,
      remaining: 7,
      resetsAt: "2026-10-23T18:00:00.000Z",
      maxChars: 100,
      pairs: ["bo-en", "en-bo", "dz-en"],
    });
  });

  test("works without data and requires a signed-in user", async () => {
    const { deps } = setup();
    await expect(handleStatus(request(undefined), deps)).resolves.toMatchObject({ used: 0, remaining: 10 });
    expect((await rejection(handleStatus(request({}, null), deps))).code).toBe("unauthenticated");
  });

  test("never more remaining than zero after the limit", async () => {
    const { deps } = setup({ config: { free: { dailyLimit: 2, maxChars: 100 } } });
    deps.quota.usage.set("user-1_2026-10-23", { count: 5, chars: 0 });
    expect(await handleStatus(request({ timeZone: "Asia/Thimphu" }), deps)).toMatchObject({ remaining: 0 });
  });
});
