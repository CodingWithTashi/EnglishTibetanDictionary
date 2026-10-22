import { type LangCode, usesTibetanScript } from "./languages.js";

// Alternation, not a class: a class holding U+200D (zero-width joiner) reads as a joined sequence.
const ZERO_WIDTH = /\u200B|\u200C|\u200D|\uFEFF/g;
const HORIZONTAL_SPACE = /[^\S\n]+/g;
const LETTER = /\p{L}/u;

/**
 * The text that is counted, cached and sent to a provider: NFC, no zero-width characters, LF line breaks,
 * single spaces, trimmed lines, at most one blank line in a row, trimmed.
 */
export function normalize(text: string): string {
  const lines = text
    .normalize("NFC")
    .replace(ZERO_WIDTH, "")
    .replace(/\r\n?/g, "\n")
    .split("\n")
    .map((line) => line.replace(HORIZONTAL_SPACE, " ").trim());
  const kept: string[] = [];
  for (const line of lines) {
    if (line === "" && kept.at(-1) === "") continue;
    kept.push(line);
  }
  return kept.join("\n").trim();
}

/** Characters as the quota counts them: Unicode code points. */
export function codePointLength(text: string): number {
  return Array.from(text).length;
}

/** Tibetan consonants, vowel signs and subjoined letters (U+0F40–U+0FBC); not punctuation or digits. */
function isTibetanLetter(char: string): boolean {
  const code = char.codePointAt(0) ?? 0;
  return code >= 0x0f40 && code <= 0x0fbc;
}

export type ScriptCheck = "ok" | "not_tibetan_script" | "wrong_script";

/**
 * Tibetan and Dzongkha sources must contain Tibetan letters (romanised input such as Wylie is not translated).
 * An English source must not be Tibetan only.
 */
export function checkScript(text: string, source: LangCode): ScriptCheck {
  let tibetan = false;
  let other = false;
  for (const char of text) {
    if (isTibetanLetter(char)) tibetan = true;
    else if (LETTER.test(char)) other = true;
  }
  if (usesTibetanScript(source)) return tibetan ? "ok" : "not_tibetan_script";
  return tibetan && !other ? "wrong_script" : "ok";
}
