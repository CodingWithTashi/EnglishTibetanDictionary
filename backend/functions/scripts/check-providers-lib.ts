import { DEFAULT_CONFIG } from "../src/config/runtimeConfig.js";
import type { LangCode, PairKey } from "../src/domain/languages.js";
import {
  PROVIDER_IDS,
  ProviderError,
  type ProviderErrorKind,
  type ProviderId,
  type TranslationProvider,
} from "../src/providers/types.js";

/** One short sample per direction (about 80 characters in all). */
export const SAMPLES: readonly { pair: PairKey; from: LangCode; to: LangCode; text: string }[] = [
  { pair: "bo-en", from: "bo", to: "en", text: "བཀྲ་ཤིས་བདེ་ལེགས།" },
  { pair: "en-bo", from: "en", to: "bo", text: "Thank you" },
  { pair: "dz-en", from: "dz", to: "en", text: "སྐུ་གཟུགས་བཟང་པོ།" },
  { pair: "en-dz", from: "en", to: "dz", text: "Thank you" },
];

export interface CheckRow {
  provider: ProviderId;
  pair: PairKey;
  status: "ok" | "not_configured" | "error" | ProviderErrorKind;
  output?: string;
}

/** Makes one real call per provider and direction. */
export async function checkProviders(
  providers: Partial<Record<ProviderId, TranslationProvider>>,
  options: { timeoutMs?: number } = {},
): Promise<CheckRow[]> {
  const rows: CheckRow[] = [];
  for (const id of PROVIDER_IDS) {
    const provider = providers[id];
    for (const { pair, from, to, text } of SAMPLES) {
      if (!provider?.isConfigured()) {
        rows.push({ provider: id, pair, status: "not_configured" });
        continue;
      }
      try {
        const signal = AbortSignal.timeout(options.timeoutMs ?? 10_000);
        rows.push({
          provider: id,
          pair,
          status: "ok",
          output: await provider.translate({ text, from, to, signal }),
        });
      } catch (error) {
        rows.push({ provider: id, pair, status: error instanceof ProviderError ? error.kind : "error" });
      }
    }
  }
  return rows;
}

/** Routes for `config/translation`: providers that answered, in the default order, then any others. */
export function suggestRoutes(rows: CheckRow[]): Partial<Record<PairKey, ProviderId[]>> {
  const routes: Partial<Record<PairKey, ProviderId[]>> = {};
  for (const pair of new Set(rows.map((r) => r.pair))) {
    const working = rows.filter((r) => r.pair === pair && r.status === "ok").map((r) => r.provider);
    const preferred = DEFAULT_CONFIG.routes[pair] ?? [];
    const ordered = [
      ...preferred.filter((p) => working.includes(p)),
      ...working.filter((p) => !preferred.includes(p)),
    ];
    if (ordered.length > 0) routes[pair] = ordered;
  }
  return routes;
}

export function formatReport(rows: CheckRow[]): string {
  return rows
    .map((r) => `${r.provider.padEnd(8)}${r.pair.padEnd(7)}${r.status.padEnd(22)}${r.output ?? ""}`)
    .join("\n");
}
