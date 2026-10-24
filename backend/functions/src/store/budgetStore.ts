import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";

import { addDays, utcDayKey } from "./time.js";

export interface BudgetStore {
  /** Records characters sent to a provider. */
  add(chars: number): Promise<void>;
  /** Whether today's (UTC) characters have reached `limit`. Approximate: totals are re-read every 30 s. */
  isOver(limit: number): Promise<boolean>;
}

const BUDGET_TTL_DAYS = 7;

/**
 * A sharded counter per UTC day, `budget/{day}/shards/{n}`, so busy days do not contend on one document. Each
 * instance caches the total and adds its own writes to it.
 */
export function createBudgetStore(
  db: Firestore,
  options: { now?: () => Date; shards?: number; cacheMs?: number; random?: () => number } = {},
): BudgetStore {
  const { now = () => new Date(), shards = 10, cacheMs = 30_000, random = Math.random } = options;
  let cached: { day: string; total: number; fetchedAt: number } | undefined;

  const shardsOf = (day: string) => db.collection("budget").doc(day).collection("shards");

  return {
    async add(chars) {
      const at = now();
      const day = utcDayKey(at);
      const shard = Math.min(shards - 1, Math.floor(random() * shards));
      await shardsOf(day)
        .doc(String(shard))
        .set(
          {
            chars: FieldValue.increment(chars),
            requests: FieldValue.increment(1),
            expireAt: Timestamp.fromDate(addDays(at, BUDGET_TTL_DAYS)),
          },
          { merge: true },
        );
      if (cached?.day === day) cached.total += chars;
    },

    async isOver(limit) {
      const at = now();
      const day = utcDayKey(at);
      if (cached?.day !== day || at.getTime() - cached.fetchedAt >= cacheMs) {
        const snapshot = await shardsOf(day).get();
        const total = snapshot.docs.reduce((sum, doc) => {
          const chars: unknown = doc.get("chars");
          return sum + (typeof chars === "number" ? chars : 0);
        }, 0);
        cached = { day, total, fetchedAt: at.getTime() };
      }
      return cached.total >= limit;
    },
  };
}
