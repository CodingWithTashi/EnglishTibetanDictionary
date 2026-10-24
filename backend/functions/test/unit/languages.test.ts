import { describe, expect, test } from "vitest";

import { isLangCode, pairKey } from "../../src/domain/languages.js";

describe("languages", () => {
  test("pairKey joins source and target", () => {
    expect(pairKey("bo", "en")).toBe("bo-en");
    expect(pairKey("en", "dz")).toBe("en-dz");
  });

  test("isLangCode accepts only the app's languages", () => {
    expect(isLangCode("bo")).toBe(true);
    expect(isLangCode("dz")).toBe(true);
    expect(isLangCode("en")).toBe(true);
    expect(isLangCode("zh")).toBe(false);
    expect(isLangCode("BO")).toBe(false);
  });
});
