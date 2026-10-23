import { RevenueCatError, type ProEntitlement, type RevenueCatClient } from "../billing/revenueCat.js";
import type { StoredSubscription, SubscriptionStore, SyncSource } from "../store/subscriptionStore.js";
import { errorMessage, type Logger } from "../util/logger.js";

export interface RefreshDeps {
  revenueCat: RevenueCatClient;
  subscriptions: SubscriptionStore;
  log: Logger;
}

/** What a user whose `pro` entitlement is gone is stored as. */
const NO_ENTITLEMENT: ProEntitlement = {
  active: false,
  expiresAt: null,
  product: null,
  willRenew: false,
  billingIssue: false,
  sandbox: false,
  store: "none",
};

/**
 * The one write path for `subscriptions/{uid}`: re-reads the customer from RevenueCat and stores it if this read is
 * newer than the stored one. A user with no entitlement and no record gets no doc. Returns the record as stored,
 * or null when there is none. Throws `RevenueCatError` when RevenueCat can't be read.
 *
 * Re-reading instead of applying the event makes duplicate, late and out-of-order webhooks harmless, and means a
 * forged webhook can at most cause a refresh.
 */
export async function refreshSubscription(
  uid: string,
  source: SyncSource,
  deps: RefreshDeps,
): Promise<StoredSubscription | null> {
  let snapshot;
  try {
    snapshot = await deps.revenueCat.getSubscriber(uid);
  } catch (error) {
    deps.log.warn("revenuecat read failed", {
      uid,
      source,
      kind: error instanceof RevenueCatError ? error.kind : "unexpected",
      error: errorMessage(error),
    });
    throw error;
  }
  const entitlement = snapshot.pro ?? NO_ENTITLEMENT;
  const result = await deps.subscriptions.putIfNewer(
    uid,
    { ...entitlement, fetchedAt: snapshot.fetchedAt },
    source,
    { onlyIfExists: snapshot.pro === null },
  );
  deps.log.info("subscription refreshed", {
    uid,
    source,
    result,
    active: entitlement.active,
    product: entitlement.product,
  });
  return result === "absent" ? null : deps.subscriptions.get(uid);
}
