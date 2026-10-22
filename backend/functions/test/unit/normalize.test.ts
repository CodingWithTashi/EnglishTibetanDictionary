import { describe, expect, test } from "vitest";

import { checkScript, codePointLength, normalize } from "../../src/domain/normalize.js";

describe("normalize", () => {
  test("collapses spaces and tabs, trims lines, keeps at most one blank line, converts CRLF", () => {
    expect(normalize("  a\r\n\r\n\r\nb\t\t c ")).toBe("a\n\nb c");
  });

  test("strips zero-width characters inside Tibetan", () => {
    expect(normalize("བཀྲ\u200B་ཤིས\uFEFF")).toBe("བཀྲ་ཤིས");
    expect(normalize("a\u200Cb\u200Dc")).toBe("abc");
  });

  test("composes to NFC", () => {
    expect(normalize("e\u0301")).toBe("\u00E9");
  });

  test("returns an empty string for whitespace-only input", () => {
    expect(normalize(" \n\t\u200B ")).toBe("");
  });
});

describe("codePointLength", () => {
  test("counts Unicode code points", () => {
    expect(codePointLength("བཀྲ་ཤིས")).toBe(7);
    expect(codePointLength("😀a")).toBe(2);
  });
});

describe("checkScript", () => {
  test("rejects Latin-only text as a Tibetan source", () => {
    expect(checkScript("bkra shis", "bo")).toBe("not_tibetan_script");
    expect(checkScript("ku zu zang po", "dz")).toBe("not_tibetan_script");
  });

  test("accepts Tibetan text with some Latin in it", () => {
    expect(checkScript("ང་ Tashi", "bo")).toBe("ok");
    expect(checkScript("སྐུ་གཟུགས་བཟང་པོ།", "dz")).toBe("ok");
  });

  test("rejects Tibetan-only text as an English source", () => {
    expect(checkScript("བཀྲ་ཤིས", "en")).toBe("wrong_script");
  });

  test("accepts English text", () => {
    expect(checkScript("Hello 123", "en")).toBe("ok");
    expect(checkScript("Tashi བཀྲ་ཤིས", "en")).toBe("ok");
  });

  test("Tibetan punctuation alone is not Tibetan text", () => {
    expect(checkScript("། ༄", "bo")).toBe("not_tibetan_script");
  });
});
