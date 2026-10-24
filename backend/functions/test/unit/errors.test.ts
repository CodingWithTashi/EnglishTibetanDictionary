import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, test } from "vitest";

import { toHttpsError, TranslationError } from "../../src/service/errors.js";
import { fakeLogger } from "./fakes.js";

describe("toHttpsError", () => {
  test("a refusal keeps its code and message, with the reason and details in details", () => {
    const log = fakeLogger();
    const error = toHttpsError(
      new TranslationError("resource-exhausted", "daily_limit", "Daily limit reached.", { limit: 10 }),
      log,
    );
    expect(error).toBeInstanceOf(HttpsError);
    expect(error).toMatchObject({
      code: "resource-exhausted",
      message: "Daily limit reached.",
      details: { reason: "daily_limit", limit: 10 },
    });
    expect(log.entries).toEqual([]);
  });

  test("an HttpsError passes through unchanged and is not logged", () => {
    const log = fakeLogger();
    const original = new HttpsError("permission-denied", "no");
    expect(toHttpsError(original, log)).toBe(original);
    expect(log.entries).toEqual([]);
  });

  test("anything else is internal, with a generic message, and logged with the context", () => {
    const log = fakeLogger();
    const error = toHttpsError(new Error("transaction aborted"), log, { uid: "user-1" });
    expect(error).toMatchObject({ code: "internal", message: "Something went wrong. Please try again." });
    expect(error.details).toBeUndefined();
    expect(log.entries).toEqual([
      {
        level: "error",
        message: "unexpected failure",
        data: { uid: "user-1", error: "transaction aborted" },
      },
    ]);
  });

  test("a thrown non-Error is logged as text", () => {
    const log = fakeLogger();
    expect(toHttpsError("socket hang up", log).code).toBe("internal");
    expect(log.entries[0]?.data).toEqual({ error: "socket hang up" });
  });
});
