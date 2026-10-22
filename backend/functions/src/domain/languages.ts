/** Languages the app translates between. Tibetan (`bo`) and Dzongkha (`dz`) are written in Tibetan script. */
export const LANG_CODES = ["bo", "dz", "en"] as const;

export type LangCode = (typeof LANG_CODES)[number];

/** A translation direction such as `"bo-en"`. */
export type PairKey = `${LangCode}-${LangCode}`;

export function isLangCode(value: unknown): value is LangCode {
  return typeof value === "string" && (LANG_CODES as readonly string[]).includes(value);
}

export function pairKey(source: LangCode, target: LangCode): PairKey {
  return `${source}-${target}`;
}

export function usesTibetanScript(lang: LangCode): boolean {
  return lang === "bo" || lang === "dz";
}
