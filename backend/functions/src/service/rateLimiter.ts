import { LRUCache } from "lru-cache";

export type RateDecision = { ok: true } | { ok: false; retryAfterSec: number };

export interface RateLimiter {
  /** Records a request for `uid` unless it already made `perMinute` in the last 60 s. */
  take(uid: string, perMinute: number): RateDecision;
}

const WINDOW_MS = 60_000;

/**
 * A sliding-window limit per user, in this instance's memory (best effort across instances: the daily quota
 * and the global budget bound the cost). Memory is capped by evicting the least recently seen users.
 */
export function createRateLimiter(options: { now?: () => number; maxUsers?: number } = {}): RateLimiter {
  const { now = Date.now, maxUsers = 10_000 } = options;
  const recent = new LRUCache<string, number[]>({ max: maxUsers });

  return {
    take(uid, perMinute) {
      const at = now();
      const kept = (recent.get(uid) ?? []).filter((t) => t > at - WINDOW_MS);
      const oldest = kept[0];
      if (kept.length >= perMinute && oldest !== undefined) {
        recent.set(uid, kept);
        return { ok: false, retryAfterSec: Math.ceil((oldest + WINDOW_MS - at) / 1_000) };
      }
      kept.push(at);
      recent.set(uid, kept);
      return { ok: true };
    },
  };
}
