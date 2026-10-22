import { describe, expect, test } from "vitest";

import { createRateLimiter } from "../../src/service/rateLimiter.js";

describe("rate limiter", () => {
  test("allows perMinute requests in a sliding minute, per user", () => {
    let now = 0;
    const limiter = createRateLimiter({ now: () => now });
    for (let i = 0; i < 3; i++) expect(limiter.take("a", 3)).toEqual({ ok: true });
    expect(limiter.take("a", 3)).toEqual({ ok: false, retryAfterSec: 60 });
    expect(limiter.take("b", 3)).toEqual({ ok: true });
    now = 30_500;
    expect(limiter.take("a", 3)).toEqual({ ok: false, retryAfterSec: 30 });
    now = 60_000;
    expect(limiter.take("a", 3)).toEqual({ ok: true });
  });

  test("a refused request does not use up the window", () => {
    let now = 0;
    const limiter = createRateLimiter({ now: () => now });
    limiter.take("a", 1);
    now = 59_999;
    limiter.take("a", 1);
    now = 60_000;
    expect(limiter.take("a", 1)).toEqual({ ok: true });
  });
});
