import { describe, expect, test, vi } from "vitest";

import {
  createConfigSource,
  DEFAULT_CONFIG,
  parseConfig,
  routeFor,
  supportedPairs,
} from "../../src/config/runtimeConfig.js";
import { fakeLogger } from "./fakes.js";

describe("parseConfig", () => {
  test("a missing doc gives the defaults", () => {
    const log = fakeLogger();
    expect(parseConfig(undefined, log)).toEqual(DEFAULT_CONFIG);
    expect(log.entries).toEqual([]);
  });

  test("defaults route Tibetan to Azure then Google and Dzongkha to Google", () => {
    expect(DEFAULT_CONFIG.routes).toEqual({
      "bo-en": ["azure", "google"],
      "en-bo": ["azure", "google"],
      "dz-en": ["google"],
      "en-dz": ["google"],
    });
    expect(DEFAULT_CONFIG.free).toEqual({ dailyLimit: 10, maxChars: 100 });
    expect(DEFAULT_CONFIG.pro).toEqual({ dailyLimit: 300, maxChars: 500 });
  });

  test("guests get 3 a day of 100 characters by default", () => {
    expect(DEFAULT_CONFIG.guest).toEqual({ dailyLimit: 3, maxChars: 100 });
  });

  test("a partial guest section keeps the other guest default", () => {
    const config = parseConfig({ guest: { dailyLimit: 5 } }, fakeLogger());
    expect(config.guest).toEqual({ dailyLimit: 5, maxChars: 100 });
    expect(config.free).toEqual(DEFAULT_CONFIG.free);
  });

  test("an invalid guest section is logged and replaced by the default", () => {
    const log = fakeLogger();
    expect(parseConfig({ guest: { dailyLimit: "5" } }, log).guest).toEqual(DEFAULT_CONFIG.guest);
    expect(log.entries).toContainEqual(
      expect.objectContaining({
        level: "error",
        message: expect.stringContaining('config: invalid "guest"') as unknown,
      }),
    );
  });

  test("a partial section keeps the other defaults", () => {
    const config = parseConfig({ free: { dailyLimit: 5 } }, fakeLogger());
    expect(config.free).toEqual({ dailyLimit: 5, maxChars: 100 });
    expect(config.pro).toEqual(DEFAULT_CONFIG.pro);
  });

  test("routes override per pair and unknown providers are dropped with a warning", () => {
    const log = fakeLogger();
    const config = parseConfig({ routes: { "bo-en": ["deepl", "azure"] } }, log);
    expect(config.routes["bo-en"]).toEqual(["azure"]);
    expect(config.routes["dz-en"]).toEqual(["google"]);
    expect(log.entries).toContainEqual(expect.objectContaining({ level: "warn" }));
  });

  test("unknown or same-language pairs are dropped with a warning", () => {
    const log = fakeLogger();
    const config = parseConfig({ routes: { "xx-en": ["azure"], "bo-bo": ["azure"] } }, log);
    expect(Object.keys(config.routes).sort()).toEqual(["bo-en", "dz-en", "en-bo", "en-dz"]);
    expect(log.entries.filter((e) => e.level === "warn")).toHaveLength(2);
  });

  test("a mistyped field falls back alone and keeps the kill switch off", () => {
    const log = fakeLogger();
    const config = parseConfig(
      { enabled: false, globalDailyCharBudget: "300000", routes: { "bo-en": ["google"] } },
      log,
    );
    expect(config.enabled).toBe(false);
    expect(config.globalDailyCharBudget).toBe(DEFAULT_CONFIG.globalDailyCharBudget);
    expect(config.routes["bo-en"]).toEqual(["google"]);
    expect(log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
  });

  test("an enabled flag that is not a boolean turns translation off", () => {
    for (const enabled of ["false", "true", 0, null]) {
      const log = fakeLogger();
      expect(parseConfig({ enabled }, log).enabled).toBe(false);
      expect(log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
    }
  });

  test("a route that is not a list of providers is ignored with an error", () => {
    const log = fakeLogger();
    const config = parseConfig({ routes: { "bo-en": "azure", "dz-en": [] } }, log);
    expect(config.routes["bo-en"]).toEqual(DEFAULT_CONFIG.routes["bo-en"]);
    expect(config.routes["dz-en"]).toEqual([]);
    expect(log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
  });

  test("an invalid doc gives the defaults and logs an error", () => {
    for (const raw of [{ free: { dailyLimit: "ten" } }, "on", { cache: { ttlDays: -1 } }]) {
      const log = fakeLogger();
      expect(parseConfig(raw, log)).toEqual(DEFAULT_CONFIG);
      expect(log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
    }
  });
});

describe("routes", () => {
  test("an empty route disables the pair", () => {
    const config = parseConfig({ routes: { "en-dz": [] } }, fakeLogger());
    expect(routeFor(config, "en-dz")).toEqual([]);
    expect(supportedPairs(config)).toEqual(["bo-en", "en-bo", "dz-en"]);
  });

  test("a pair with no route has none", () => {
    expect(routeFor(DEFAULT_CONFIG, "bo-dz")).toEqual([]);
  });
});

describe("createConfigSource", () => {
  test("reads the doc once per minute", async () => {
    let now = 0;
    const read = vi.fn(() => Promise.resolve<unknown>({ free: { dailyLimit: 3 } }));
    const source = createConfigSource(read, { now: () => now, log: fakeLogger() });
    expect((await source.get()).free.dailyLimit).toBe(3);
    now = 59_999;
    await source.get();
    expect(read).toHaveBeenCalledTimes(1);
    now = 60_000;
    await source.get();
    expect(read).toHaveBeenCalledTimes(2);
  });

  test("concurrent callers share one read", async () => {
    const read = vi.fn(() => Promise.resolve<unknown>(undefined));
    const source = createConfigSource(read, { now: () => 0, log: fakeLogger() });
    await Promise.all([source.get(), source.get(), source.get()]);
    expect(read).toHaveBeenCalledTimes(1);
  });

  test("a failed read keeps the last good config, or the defaults when there is none", async () => {
    let now = 0;
    const log = fakeLogger();
    const read = vi
      .fn<() => Promise<unknown>>()
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({ enabled: false })
      .mockRejectedValueOnce(new Error("offline"));
    const source = createConfigSource(read, { now: () => now, log });
    expect(await source.get()).toEqual(DEFAULT_CONFIG);
    now += 60_000;
    expect((await source.get()).enabled).toBe(false);
    now += 60_000;
    expect((await source.get()).enabled).toBe(false);
    expect(log.entries.filter((e) => e.level === "error")).toHaveLength(2);
  });
});
