import { describe, expect, test } from "vitest";

import { isGuest, resolvePlan } from "../../src/service/plan.js";

describe("isGuest", () => {
  test.each([
    ["anonymous, no identities", { sign_in_provider: "anonymous", identities: {} }, true],
    ["anonymous, identities missing", { sign_in_provider: "anonymous" }, true],
    [
      "anonymous token minted after a Google link",
      { sign_in_provider: "anonymous", identities: { "google.com": ["1"] } },
      false,
    ],
    [
      "google",
      { sign_in_provider: "google.com", identities: { "google.com": ["1"], email: ["a@b.c"] } },
      false,
    ],
    ["no firebase claim", undefined, false],
  ])("%s", (_name, firebase, expected) => {
    expect(isGuest({ uid: "u", token: firebase === undefined ? {} : { firebase } })).toBe(expected);
  });

  test("no token at all is not a guest", () => {
    expect(isGuest({ uid: "u" })).toBe(false);
  });
});

describe("resolvePlan", () => {
  test("maps guests to guest and everyone else to free", async () => {
    expect(await resolvePlan({ uid: "u", isGuest: true })).toBe("guest");
    expect(await resolvePlan({ uid: "u", isGuest: false })).toBe("free");
  });
});
