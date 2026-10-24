import { afterEach, describe, expect, test, vi } from "vitest";

import { AllProvidersFailedError, createRouter, type RouterOptions } from "../../src/providers/router.js";
import { failWith, fakeLogger, fakeProvider } from "./fakes.js";

const ok = (text: string) => () => Promise.resolve(text);

function router(options: Partial<RouterOptions> & Pick<RouterOptions, "providers">) {
  return createRouter({ log: fakeLogger(), sleep: () => Promise.resolve(), random: () => 0.5, ...options });
}

async function failureOf(promise: Promise<unknown>): Promise<AllProvidersFailedError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof AllProvidersFailedError) return error;
    throw error;
  }
  throw new Error("expected AllProvidersFailedError");
}

afterEach(() => {
  vi.useRealTimers();
});

describe("router", () => {
  test("uses the first provider that answers and does not call the rest", async () => {
    const azure = fakeProvider("azure", ok("Tashi Delek"));
    const google = fakeProvider("google", ok("unused"));
    const result = await router({ providers: { azure, google } }).translate(
      ["azure", "google"],
      "ཀ",
      "bo",
      "en",
    );
    expect(result).toEqual({ translation: "Tashi Delek", provider: "azure" });
    expect(azure.calls).toHaveLength(1);
    expect(azure.calls[0]).toMatchObject({ text: "ཀ", from: "bo", to: "en" });
    expect(google.calls).toHaveLength(0);
  });

  test("retries a transient failure once, then falls back", async () => {
    const azure = fakeProvider("azure", failWith("azure", "transient"));
    const google = fakeProvider("google", ok("Hello"));
    const result = await router({ providers: { azure, google } }).translate(
      ["azure", "google"],
      "ཀ",
      "bo",
      "en",
    );
    expect(result.provider).toBe("google");
    expect(azure.calls).toHaveLength(2);
  });

  test("a retry that succeeds returns the first provider's answer", async () => {
    const azure = fakeProvider("azure", (_input, call) =>
      call === 1 ? failWith("azure", "transient")() : Promise.resolve("second try"),
    );
    const result = await router({ providers: { azure } }).translate(["azure"], "ཀ", "bo", "en");
    expect(result).toEqual({ translation: "second try", provider: "azure" });
  });

  test("an unsupported language falls back at once and never opens the breaker", async () => {
    const google = fakeProvider("google", failWith("google", "unsupported_language"));
    const azure = fakeProvider("azure", ok("Hello"));
    const r = router({ providers: { azure, google } });
    for (let i = 0; i < 8; i++) await r.translate(["google", "azure"], "ཀ", "bo", "en");
    expect(google.calls).toHaveLength(8);
  });

  test("failures caused by the input never open the breaker", async () => {
    const azure = fakeProvider("azure", failWith("azure", "bad_response"));
    const google = fakeProvider("google", ok("Hello"));
    const r = router({ providers: { azure, google } });
    for (let i = 0; i < 8; i++) await r.translate(["azure", "google"], "ཀ", "bo", "en");
    expect(azure.calls).toHaveLength(8);
  });

  test("skips an unconfigured provider without calling it", async () => {
    const azure = fakeProvider("azure", ok("unused"), { configured: false });
    const google = fakeProvider("google", ok("Hello"));
    const result = await router({ providers: { azure, google } }).translate(
      ["azure", "google"],
      "ཀ",
      "bo",
      "en",
    );
    expect(result.provider).toBe("google");
    expect(azure.calls).toHaveLength(0);
  });

  test("five failures in a row open the breaker for 30 s, then one trial is allowed", async () => {
    let now = 0;
    const azure = fakeProvider("azure", failWith("azure", "auth"));
    const google = fakeProvider("google", ok("Hello"));
    const r = router({ providers: { azure, google }, now: () => now });
    for (let i = 0; i < 5; i++) await r.translate(["azure", "google"], "ཀ", "bo", "en");
    expect(azure.calls).toHaveLength(5);

    await r.translate(["azure", "google"], "ཀ", "bo", "en");
    expect(azure.calls).toHaveLength(5);

    now = 30_000;
    await r.translate(["azure", "google"], "ཀ", "bo", "en");
    await r.translate(["azure", "google"], "ཀ", "bo", "en");
    expect(azure.calls).toHaveLength(6);
  });

  test("a success closes the breaker", async () => {
    let now = 0;
    let failing = true;
    const azure = fakeProvider("azure", () =>
      failing ? failWith("azure", "auth")() : Promise.resolve("ok"),
    );
    const r = router({ providers: { azure }, now: () => now });
    for (let i = 0; i < 5; i++) await r.translate(["azure"], "ཀ", "bo", "en").catch(() => undefined);
    now = 30_000;
    failing = false;
    await r.translate(["azure"], "ཀ", "bo", "en");
    await expect(r.translate(["azure"], "ཀ", "bo", "en")).resolves.toMatchObject({ provider: "azure" });
    expect(azure.calls).toHaveLength(7);
  });

  test("aborts a provider that hangs for 8 s", async () => {
    vi.useFakeTimers();
    const signals: AbortSignal[] = [];
    const hung = fakeProvider("azure", (input) => {
      signals.push(input.signal);
      return new Promise<string>(() => undefined);
    });
    const google = fakeProvider("google", ok("Hello"));
    const pending = router({ providers: { azure: hung, google } }).translate(
      ["azure", "google"],
      "ཀ",
      "bo",
      "en",
    );

    await vi.advanceTimersByTimeAsync(7_999);
    expect(signals[0]?.aborted).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(signals[0]?.aborted).toBe(true);
    await vi.advanceTimersByTimeAsync(8_000);

    await expect(pending).resolves.toEqual({ translation: "Hello", provider: "google" });
    expect(hung.calls).toHaveLength(2);
  });

  test("stops at the overall deadline", async () => {
    let now = 0;
    const azure = fakeProvider("azure", () => {
      now += 20_000;
      return failWith("azure", "bad_response")();
    });
    const google = fakeProvider("google", ok("unused"));
    const error = await failureOf(
      router({ providers: { azure, google }, now: () => now }).translate(
        ["azure", "google"],
        "ཀ",
        "bo",
        "en",
      ),
    );
    expect(google.calls).toHaveLength(0);
    expect(error.attempts).toEqual([
      { provider: "azure", outcome: "bad_response" },
      { provider: "google", outcome: "deadline_exceeded" },
    ]);
  });

  test("throws AllProvidersFailedError listing every attempt", async () => {
    const azure = fakeProvider("azure", failWith("azure", "auth"));
    const google = fakeProvider("google", failWith("google", "transient"));
    const error = await failureOf(
      router({ providers: { azure, google } }).translate(["azure", "google"], "ཀ", "bo", "en"),
    );
    expect(error.attempts).toEqual([
      { provider: "azure", outcome: "auth" },
      { provider: "google", outcome: "transient" },
      { provider: "google", outcome: "transient" },
    ]);
  });

  test("an empty route fails without calling anything", async () => {
    const error = await failureOf(router({ providers: {} }).translate([], "ཀ", "bo", "en"));
    expect(error.attempts).toEqual([]);
  });

  test("waits Retry-After before retrying, capped at 2 s", async () => {
    const waits: number[] = [];
    const azure = fakeProvider("azure", failWith("azure", "rate_limited", 5_000));
    await router({
      providers: { azure },
      sleep: (ms) => {
        waits.push(ms);
        return Promise.resolve();
      },
    })
      .translate(["azure"], "ཀ", "bo", "en")
      .catch(() => undefined);
    expect(waits).toEqual([2_000]);
  });

  test("waits a jittered 250 ms before retrying a transient failure", async () => {
    const waits: number[] = [];
    const azure = fakeProvider("azure", failWith("azure", "transient"));
    await router({
      providers: { azure },
      random: () => 0,
      sleep: (ms) => {
        waits.push(ms);
        return Promise.resolve();
      },
    })
      .translate(["azure"], "ཀ", "bo", "en")
      .catch(() => undefined);
    expect(waits).toEqual([125]);
  });

  test("a provider bug (non-ProviderError) counts as a bad response", async () => {
    const azure = fakeProvider("azure", () => Promise.reject(new TypeError("boom")));
    const error = await failureOf(router({ providers: { azure } }).translate(["azure"], "ཀ", "bo", "en"));
    expect(error.attempts).toEqual([{ provider: "azure", outcome: "bad_response" }]);
  });
});
