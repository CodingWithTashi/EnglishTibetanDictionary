import { z } from "zod";

import { isLangCode, type PairKey } from "../domain/languages.js";
import { isProviderId, type ProviderId } from "../providers/types.js";
import { errorMessage, type Logger } from "../util/logger.js";

export interface PlanLimits {
  /** Translations a day that are not served from cache. */
  readonly dailyLimit: number;
  /** Characters (code points) per translation. */
  readonly maxChars: number;
}

/** Settings read from the Firestore doc `config/translation`; every field is optional there. */
export interface RuntimeConfig {
  /** Kill switch for translation. */
  readonly enabled: boolean;
  /** Providers to try, in order, per direction. An empty or missing route means the direction is off. */
  readonly routes: Readonly<Partial<Record<PairKey, readonly ProviderId[]>>>;
  readonly free: PlanLimits;
  readonly pro: PlanLimits;
  readonly cache: {
    /** Part of every cache key: bumping it invalidates the whole cache. */
    readonly generation: number;
    readonly ttlDays: number;
  };
  /** Characters a UTC day sent to providers across all users, a cap on the bill. */
  readonly globalDailyCharBudget: number;
  /** Requests a minute per user, per function instance. */
  readonly rateLimitPerMinute: number;
}

export const DEFAULT_CONFIG: RuntimeConfig = {
  enabled: true,
  routes: {
    "bo-en": ["azure", "google"],
    "en-bo": ["azure", "google"],
    "dz-en": ["google"],
    "en-dz": ["google"],
  },
  free: { dailyLimit: 10, maxChars: 100 },
  pro: { dailyLimit: 300, maxChars: 500 },
  cache: { generation: 1, ttlDays: 180 },
  globalDailyCharBudget: 300_000,
  rateLimitPerMinute: 20,
};

/** The longest text any plan may send; the request schema caps raw input above it. */
export const MAX_CHARS_CEILING = 5_000;

const limitsSchema = z
  .object({
    dailyLimit: z.int().min(0).max(100_000),
    maxChars: z.int().min(1).max(MAX_CHARS_CEILING),
  })
  .partial();

const cacheSchema = z
  .object({
    generation: z.int().min(1),
    ttlDays: z.int().min(1).max(3_650),
  })
  .partial();

function asPairKey(key: string): PairKey | undefined {
  const [source, target, ...rest] = key.split("-");
  if (rest.length > 0 || !isLangCode(source) || !isLangCode(target) || source === target) return undefined;
  return `${source}-${target}`;
}

/**
 * One field of the doc: absent → undefined; invalid → logged, undefined (the caller uses the default). Fields
 * are checked one by one so a typo in one (the Firestore console makes new fields strings) leaves the others.
 */
function field<T>(
  doc: Record<string, unknown>,
  name: string,
  schema: z.ZodType<T>,
  log: Logger,
): T | undefined {
  if (!(name in doc)) return undefined;
  const parsed = schema.safeParse(doc[name]);
  if (parsed.success) return parsed.data;
  log.error(`config: invalid "${name}" in config/translation, using the default`, {
    issues: z.prettifyError(parsed.error),
  });
  return undefined;
}

function mergeRoutes(raw: Record<string, unknown> | undefined, log: Logger): RuntimeConfig["routes"] {
  const routes: Partial<Record<PairKey, readonly ProviderId[]>> = { ...DEFAULT_CONFIG.routes };
  for (const [key, providers] of Object.entries(raw ?? {})) {
    const pair = asPairKey(key);
    if (pair === undefined) {
      log.warn("config: ignoring route for an unknown direction", { pair: key });
      continue;
    }
    if (!Array.isArray(providers) || !providers.every((p) => typeof p === "string")) {
      log.error("config: a route must be a list of provider ids, using the default", { pair });
      continue;
    }
    const known = providers.filter(isProviderId);
    if (known.length !== providers.length) {
      log.warn("config: ignoring unknown providers", { pair, providers });
    }
    routes[pair] = known;
  }
  return routes;
}

/**
 * Merges the doc onto the defaults field by field; an invalid field is logged and replaced by its default, so
 * translation keeps working. The exception is `enabled`: if it is present but not a boolean, translation is
 * turned off, because the kill switch must never fail open.
 */
export function parseConfig(raw: unknown, log: Logger): RuntimeConfig {
  if (raw === undefined || raw === null) return DEFAULT_CONFIG;
  if (typeof raw !== "object" || Array.isArray(raw)) {
    log.error("config: config/translation is not a map, using defaults");
    return DEFAULT_CONFIG;
  }
  const doc = raw as Record<string, unknown>;
  const enabled =
    field(doc, "enabled", z.boolean(), log) ?? ("enabled" in doc ? false : DEFAULT_CONFIG.enabled);
  return {
    enabled,
    routes: mergeRoutes(field(doc, "routes", z.record(z.string(), z.unknown()), log), log),
    free: { ...DEFAULT_CONFIG.free, ...field(doc, "free", limitsSchema, log) },
    pro: { ...DEFAULT_CONFIG.pro, ...field(doc, "pro", limitsSchema, log) },
    cache: { ...DEFAULT_CONFIG.cache, ...field(doc, "cache", cacheSchema, log) },
    globalDailyCharBudget:
      field(doc, "globalDailyCharBudget", z.int().min(0), log) ?? DEFAULT_CONFIG.globalDailyCharBudget,
    rateLimitPerMinute:
      field(doc, "rateLimitPerMinute", z.int().min(1).max(10_000), log) ?? DEFAULT_CONFIG.rateLimitPerMinute,
  };
}

export function routeFor(config: RuntimeConfig, pair: PairKey): readonly ProviderId[] {
  return config.routes[pair] ?? [];
}

/** Directions that have at least one provider. */
export function supportedPairs(config: RuntimeConfig): PairKey[] {
  return (Object.keys(config.routes) as PairKey[]).filter((pair) => routeFor(config, pair).length > 0);
}

export interface ConfigSource {
  get(): Promise<RuntimeConfig>;
}

/**
 * Caches the config for `ttlMs` per instance; concurrent callers share one read. When a read fails, the last
 * good config (or the defaults) is used until the next refresh.
 */
export function createConfigSource(
  read: () => Promise<unknown>,
  options: { log: Logger; ttlMs?: number; now?: () => number },
): ConfigSource {
  const { log, ttlMs = 60_000, now = Date.now } = options;
  let cached: { config: RuntimeConfig; fetchedAt: number } | undefined;
  let lastGood: RuntimeConfig | undefined;
  let inflight: Promise<RuntimeConfig> | undefined;

  async function refresh(): Promise<RuntimeConfig> {
    const fetchedAt = now();
    let config: RuntimeConfig;
    try {
      config = parseConfig(await read(), log);
      lastGood = config;
    } catch (error) {
      log.error("config: read failed", { error: errorMessage(error) });
      config = lastGood ?? DEFAULT_CONFIG;
    }
    cached = { config, fetchedAt };
    return config;
  }

  return {
    get() {
      if (cached && now() - cached.fetchedAt < ttlMs) return Promise.resolve(cached.config);
      inflight ??= refresh().finally(() => {
        inflight = undefined;
      });
      return inflight;
    },
  };
}
