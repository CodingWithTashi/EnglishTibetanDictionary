import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";

import { addDays } from "./time.js";

export interface Usage {
  /** Translations counted today (cache hits and failures are not counted). */
  count: number;
  chars: number;
}

export class DailyLimitError extends Error {
  constructor(
    readonly limit: number,
    readonly used: number,
  ) {
    super(`daily limit of ${limit} reached`);
    this.name = "DailyLimitError";
  }
}

export interface QuotaStore {
  read(uid: string, dayKey: string): Promise<Usage>;
  /** Counts one translation if the user is under `limit`, else throws `DailyLimitError`. */
  reserve(uid: string, dayKey: string, limit: number, chars: number): Promise<Usage>;
  /** Gives back a reservation whose translation failed. */
  refund(uid: string, dayKey: string, chars: number): Promise<void>;
}

const USAGE_TTL_DAYS = 7;

function usageOf(data: FirebaseFirestore.DocumentData | undefined): Usage {
  const count: unknown = data?.count;
  const chars: unknown = data?.chars;
  return { count: typeof count === "number" ? count : 0, chars: typeof chars === "number" ? chars : 0 };
}

/** One doc per user and local day, `dailyUsage/{uid}_{day}`, deleted by TTL a week later. */
export function createQuotaStore(db: Firestore, options: { now?: () => Date } = {}): QuotaStore {
  const { now = () => new Date() } = options;
  const ref = (uid: string, dayKey: string) =>
    db.collection("dailyUsage").doc(`${encodeURIComponent(uid)}_${dayKey}`);

  return {
    async read(uid, dayKey) {
      return usageOf((await ref(uid, dayKey).get()).data());
    },

    reserve(uid, dayKey, limit, chars) {
      const doc = ref(uid, dayKey);
      return db.runTransaction(async (tx) => {
        const used = usageOf((await tx.get(doc)).data());
        if (used.count >= limit) throw new DailyLimitError(limit, used.count);
        const next = { count: used.count + 1, chars: used.chars + chars };
        const at = now();
        tx.set(doc, {
          uid,
          day: dayKey,
          ...next,
          updatedAt: Timestamp.fromDate(at),
          expireAt: Timestamp.fromDate(addDays(at, USAGE_TTL_DAYS)),
        });
        return next;
      });
    },

    async refund(uid, dayKey, chars) {
      await ref(uid, dayKey).update({
        count: FieldValue.increment(-1),
        chars: FieldValue.increment(-chars),
        updatedAt: Timestamp.fromDate(now()),
      });
    },
  };
}
