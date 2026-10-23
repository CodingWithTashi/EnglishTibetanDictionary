import { HttpsError } from "firebase-functions/v2/https";

import { ProviderError } from "../../src/providers/types.js";
import type {
  ProviderErrorKind,
  ProviderId,
  TranslateInput,
  TranslationProvider,
} from "../../src/providers/types.js";
import type { BudgetStore } from "../../src/store/budgetStore.js";
import type { PutResult, StoredSubscription, SubscriptionStore } from "../../src/store/subscriptionStore.js";
import { DailyLimitError, type QuotaStore, type Usage } from "../../src/store/quotaStore.js";
import type { CacheEntry, TranslationCache } from "../../src/store/translationCache.js";
import type { Logger, LogLevel } from "../../src/util/logger.js";

export interface LogEntry {
  level: LogLevel;
  message: string;
  data?: Record<string, unknown>;
}

/** The HttpsError a call is refused with; fails the test if it resolves or throws anything else. */
export async function rejection(promise: Promise<unknown>): Promise<HttpsError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof HttpsError) return error;
    throw error;
  }
  throw new Error("expected an HttpsError");
}

export function reasonOf(error: HttpsError): unknown {
  return (error.details as { reason?: unknown } | undefined)?.reason;
}

/** A logger that records what it is given. */
export function fakeLogger(): Logger & { entries: LogEntry[] } {
  const entries: LogEntry[] = [];
  const at =
    (level: LogLevel) =>
    (message: string, data?: Record<string, unknown>): void => {
      entries.push(data === undefined ? { level, message } : { level, message, data });
    };
  return { entries, info: at("info"), warn: at("warn"), error: at("error") };
}

/** A provider whose replies are scripted; `calls` records every input it got. */
export function fakeProvider(
  id: ProviderId,
  reply: (input: TranslateInput, call: number) => Promise<string>,
  options: { configured?: boolean } = {},
): TranslationProvider & { calls: TranslateInput[] } {
  const calls: TranslateInput[] = [];
  return {
    id,
    calls,
    isConfigured: () => options.configured ?? true,
    translate(input) {
      calls.push(input);
      return reply(input, calls.length);
    },
  };
}

export function failWith(
  id: ProviderId,
  kind: ProviderErrorKind,
  retryAfterMs?: number,
): () => Promise<never> {
  return () =>
    Promise.reject(
      new ProviderError(id, kind, "scripted failure", retryAfterMs === undefined ? {} : { retryAfterMs }),
    );
}

/** In-memory stores with the same contracts as the Firestore ones. */
export function memoryCache(): TranslationCache & { entries: Map<string, CacheEntry>; failPut?: Error } {
  const entries = new Map<string, CacheEntry>();
  const cache: TranslationCache & { entries: Map<string, CacheEntry>; failPut?: Error } = {
    entries,
    get(key) {
      const entry = entries.get(key);
      return Promise.resolve(
        entry ? { translation: entry.translation, provider: entry.provider, tier: "l2" } : null,
      );
    },
    put(key, entry) {
      if (cache.failPut) return Promise.reject(cache.failPut);
      entries.set(key, entry);
      return Promise.resolve();
    },
  };
  return cache;
}

export function memoryQuota(): QuotaStore & { usage: Map<string, Usage>; refunds: number; reserves: number } {
  const usage = new Map<string, Usage>();
  const store = {
    usage,
    refunds: 0,
    reserves: 0,
    read(uid: string, day: string) {
      return Promise.resolve({ ...(usage.get(`${uid}_${day}`) ?? { count: 0, chars: 0 }) });
    },
    reserve(uid: string, day: string, limit: number, chars: number) {
      store.reserves++;
      const used = usage.get(`${uid}_${day}`) ?? { count: 0, chars: 0 };
      if (used.count >= limit) return Promise.reject(new DailyLimitError(limit, used.count));
      const next = { count: used.count + 1, chars: used.chars + chars };
      usage.set(`${uid}_${day}`, next);
      return Promise.resolve({ ...next });
    },
    refund(uid: string, day: string, chars: number) {
      store.refunds++;
      const used = usage.get(`${uid}_${day}`) ?? { count: 0, chars: 0 };
      usage.set(`${uid}_${day}`, { count: used.count - 1, chars: used.chars - chars });
      return Promise.resolve();
    },
    deleteAll(uid: string) {
      const keys = [...usage.keys()].filter((key) => key.startsWith(`${uid}_`));
      for (const key of keys) usage.delete(key);
      return Promise.resolve(keys.length);
    },
  };
  return store;
}

export function memoryBudget(): BudgetStore & { total: number } {
  const budget = {
    total: 0,
    add(chars: number) {
      budget.total += chars;
      return Promise.resolve();
    },
    isOver(limit: number) {
      return Promise.resolve(budget.total >= limit);
    },
  };
  return budget;
}

/** The subscription store's contract in memory; `failGet` makes reads reject. */
export function memorySubscriptions(
  options: { now?: () => Date } = {},
): SubscriptionStore & { docs: Map<string, StoredSubscription>; failGet?: Error } {
  const { now = () => new Date() } = options;
  const docs = new Map<string, StoredSubscription>();
  const store: SubscriptionStore & { docs: Map<string, StoredSubscription>; failGet?: Error } = {
    docs,
    get(uid) {
      if (store.failGet) return Promise.reject(store.failGet);
      const doc = docs.get(uid);
      return Promise.resolve(doc ? { ...doc } : null);
    },
    putIfNewer(uid, record, source, putOptions = {}): Promise<PutResult> {
      const stored = docs.get(uid);
      if (!stored && putOptions.onlyIfExists === true) return Promise.resolve("absent");
      if (stored && stored.fetchedAt.getTime() >= record.fetchedAt.getTime()) return Promise.resolve("stale");
      docs.set(uid, { ...record, updatedAt: now(), source });
      return Promise.resolve("written");
    },
    delete(uid) {
      docs.delete(uid);
      return Promise.resolve();
    },
  };
  return store;
}
