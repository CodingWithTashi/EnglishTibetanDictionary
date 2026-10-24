import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { z } from "zod";

import type { ProEntitlement } from "../billing/revenueCat.js";

/** Which path wrote a record: the RevenueCat webhook or the app's `syncSubscription`. */
export type SyncSource = "webhook" | "sync";

/** `subscriptions/{uid}`: the user's `pro` entitlement as of RevenueCat's answer at `fetchedAt`. */
export interface StoredSubscription extends ProEntitlement {
  fetchedAt: Date;
  updatedAt: Date;
  source: SyncSource;
}

export type PutResult = "written" | "stale" | "absent";

export interface SubscriptionStore {
  get(uid: string): Promise<StoredSubscription | null>;
  /**
   * Writes the record in a transaction unless the stored `fetchedAt` is the same or newer ("stale"), so a slow read
   * never overwrites a newer one. With `onlyIfExists`, a missing record stays missing ("absent").
   */
  putIfNewer(
    uid: string,
    record: ProEntitlement & { fetchedAt: Date },
    source: SyncSource,
    options?: { onlyIfExists?: boolean },
  ): Promise<PutResult>;
  delete(uid: string): Promise<void>;
}

const timestamp = z.instanceof(Timestamp).transform((t) => t.toDate());

const docSchema = z.object({
  active: z.boolean(),
  expiresAt: timestamp.nullable(),
  product: z.enum(["monthly", "yearly"]).nullable(),
  willRenew: z.boolean(),
  billingIssue: z.boolean(),
  sandbox: z.boolean(),
  store: z.string(),
  fetchedAt: timestamp,
  updatedAt: timestamp,
  source: z.enum(["webhook", "sync"]),
});

/** A stored record; a doc that doesn't match throws, so it is never mistaken for "no subscription". */
function parse(uid: string, data: FirebaseFirestore.DocumentData | undefined): StoredSubscription | null {
  if (data === undefined) return null;
  const parsed = docSchema.safeParse(data);
  if (!parsed.success) throw new Error(`malformed subscriptions doc for ${uid}`);
  return parsed.data;
}

/** One doc per user, `subscriptions/{uid}`, written only by the Functions. No TTL: it lives as long as the account. */
export function createSubscriptionStore(
  db: Firestore,
  options: { now?: () => Date } = {},
): SubscriptionStore {
  const { now = () => new Date() } = options;
  const ref = (uid: string) => db.collection("subscriptions").doc(encodeURIComponent(uid));

  return {
    async get(uid) {
      return parse(uid, (await ref(uid).get()).data());
    },

    putIfNewer(uid, record, source, putOptions = {}) {
      const doc = ref(uid);
      return db.runTransaction(async (tx): Promise<PutResult> => {
        const snapshot = await tx.get(doc);
        if (!snapshot.exists && putOptions.onlyIfExists === true) return "absent";
        const stored: unknown = snapshot.get("fetchedAt");
        if (stored instanceof Timestamp && stored.toMillis() >= record.fetchedAt.getTime()) return "stale";
        tx.set(doc, {
          active: record.active,
          expiresAt: record.expiresAt ? Timestamp.fromDate(record.expiresAt) : null,
          product: record.product,
          willRenew: record.willRenew,
          billingIssue: record.billingIssue,
          sandbox: record.sandbox,
          store: record.store,
          fetchedAt: Timestamp.fromDate(record.fetchedAt),
          updatedAt: Timestamp.fromDate(now()),
          source,
        });
        return "written";
      });
    },

    async delete(uid) {
      await ref(uid).delete();
    },
  };
}
