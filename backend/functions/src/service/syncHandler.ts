import { RevenueCatError } from "../billing/revenueCat.js";
import type { StoredSubscription } from "../store/subscriptionStore.js";
import type { Logger } from "../util/logger.js";
import { toHttpsError, TranslationError } from "./errors.js";
import { isProActive, type Plan } from "./plan.js";
import type { RateLimiter } from "./rateLimiter.js";
import { requireUser, type CallableLike } from "./translateHandler.js";

export interface SyncDeps {
  /** Re-reads the caller from RevenueCat; throws `RevenueCatError` when it can't. */
  refresh: (uid: string) => Promise<StoredSubscription | null>;
  rateLimiter: RateLimiter;
  log: Logger;
  now: () => Date;
}

const SYNC_PER_MINUTE = 6;

/**
 * `syncSubscription`: the app calls it right after a purchase, restore or plan switch, and when its RevenueCat state
 * says Pro but the server says free. Refreshes the caller's record and returns the plan it gives.
 */
export async function handleSyncSubscription(request: CallableLike, deps: SyncDeps): Promise<{ plan: Plan }> {
  try {
    const user = requireUser(request);
    if (user.isGuest) {
      throw new TranslationError("failed-precondition", "guest_account", "Sign in with Google to subscribe.");
    }
    const rate = deps.rateLimiter.take(user.uid, SYNC_PER_MINUTE);
    if (!rate.ok) {
      throw new TranslationError("resource-exhausted", "rate_limited", "Too many requests.", {
        retryAfterSec: rate.retryAfterSec,
      });
    }
    let record: StoredSubscription | null;
    try {
      record = await deps.refresh(user.uid);
    } catch (error) {
      if (error instanceof RevenueCatError) {
        throw new TranslationError("unavailable", "billing_unavailable", "Billing is unavailable right now.");
      }
      throw error;
    }
    const plan: Plan = isProActive(record, deps.now()) ? "pro" : "free";
    deps.log.info("subscription synced", { uid: user.uid, plan });
    return { plan };
  } catch (error) {
    throw toHttpsError(error, deps.log, { uid: request.auth?.uid });
  }
}
