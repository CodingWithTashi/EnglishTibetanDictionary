import { createHash } from "node:crypto";

import type { LangCode } from "../domain/languages.js";

/** The `translations` doc id for a normalised text in one direction and cache generation. */
export function cacheKey(generation: number, source: LangCode, target: LangCode, text: string): string {
  return createHash("sha256")
    .update(`${generation}\u0000${source}\u0000${target}\u0000${text}`)
    .digest("hex");
}
