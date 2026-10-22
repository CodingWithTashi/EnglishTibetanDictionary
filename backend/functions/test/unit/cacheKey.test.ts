import { describe, expect, test } from "vitest";

import { cacheKey } from "../../src/store/cacheKey.js";

describe("cacheKey", () => {
  test("is a stable sha256 hex digest", () => {
    const key = cacheKey(1, "bo", "en", "བཀྲ་ཤིས");
    expect(key).toMatch(/^[0-9a-f]{64}$/);
    expect(cacheKey(1, "bo", "en", "བཀྲ་ཤིས")).toBe(key);
  });

  test("differs by generation, direction and text", () => {
    const key = cacheKey(1, "bo", "en", "བཀྲ་ཤིས");
    expect(cacheKey(2, "bo", "en", "བཀྲ་ཤིས")).not.toBe(key);
    expect(cacheKey(1, "dz", "en", "བཀྲ་ཤིས")).not.toBe(key);
    expect(cacheKey(1, "en", "bo", "བཀྲ་ཤིས")).not.toBe(key);
    expect(cacheKey(1, "bo", "en", "བཀྲ་ཤིས་")).not.toBe(key);
  });
});
