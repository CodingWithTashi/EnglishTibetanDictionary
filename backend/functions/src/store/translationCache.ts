import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { LRUCache } from "lru-cache";

import type { LangCode } from "../domain/languages.js";
import { isProviderId, type ProviderId } from "../providers/types.js";
import { errorMessage, type Logger } from "../util/logger.js";
import { addDays, DAY_MS } from "./time.js";

export interface CachedTranslation {
  translation: string;
  provider: ProviderId;
}

export interface CacheHit extends CachedTranslation {
  /** `l1`: this instance's memory; `l2`: Firestore. */
  tier: "l1" | "l2";
}

export interface CacheEntry extends CachedTranslation {
  source: LangCode;
  target: LangCode;
  sourceText: string;
  generation: number;
}

export interface TranslationCache {
  get(key: string, ttlDays: number): Promise<CacheHit | null>;
  put(key: string, entry: CacheEntry, ttlDays: number): Promise<void>;
}

/** A hit on an entry closer than this to its expiry pushes the expiry out again. */
const EXTEND_WITHIN_MS = 30 * DAY_MS;

/**
 * Two tiers: a per-instance LRU in front of the shared `translations` collection. Firestore TTL deletes expired
 * docs eventually (within days), so reads also check `expireAt`.
 */
export function createTranslationCache(
  db: Firestore,
  options: { log: Logger; now?: () => Date; l1Max?: number; l1TtlMs?: number },
): TranslationCache {
  const { log, now = () => new Date(), l1Max = 5_000, l1TtlMs = 3_600_000 } = options;
  const memory = new LRUCache<string, CachedTranslation>({ max: l1Max, ttl: l1TtlMs });
  const collection = db.collection("translations");

  return {
    async get(key, ttlDays) {
      const remembered = memory.get(key);
      if (remembered) return { ...remembered, tier: "l1" };

      const snapshot = await collection.doc(key).get();
      if (!snapshot.exists) return null;
      const translation: unknown = snapshot.get("translation");
      const provider: unknown = snapshot.get("provider");
      const expireAt: unknown = snapshot.get("expireAt");
      if (
        typeof translation !== "string" ||
        translation === "" ||
        !isProviderId(provider) ||
        !(expireAt instanceof Timestamp)
      ) {
        log.warn("cache: ignoring a malformed translations doc", { key: key.slice(0, 12) });
        return null;
      }
      const at = now();
      if (expireAt.toMillis() <= at.getTime()) return null;
      if (expireAt.toMillis() - at.getTime() < EXTEND_WITHIN_MS) {
        await snapshot.ref
          .update({ expireAt: Timestamp.fromDate(addDays(at, ttlDays)) })
          .catch((error: unknown) => {
            log.warn("cache: could not extend an entry", {
              key: key.slice(0, 12),
              error: errorMessage(error),
            });
          });
      }
      const hit = { translation, provider };
      memory.set(key, hit);
      return { ...hit, tier: "l2" };
    },

    async put(key, entry, ttlDays) {
      const at = now();
      await collection.doc(key).set({
        ...entry,
        createdAt: Timestamp.fromDate(at),
        expireAt: Timestamp.fromDate(addDays(at, ttlDays)),
      });
      memory.set(key, { translation: entry.translation, provider: entry.provider });
    },
  };
}
