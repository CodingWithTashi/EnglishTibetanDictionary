import type { StoredSubscription, SubscriptionStore } from "../store/subscriptionStore.js";
import { errorMessage, type Logger } from "../util/logger.js";
import type { CallableAuth } from "./translateHandler.js";

export type Plan = "guest" | "free" | "pro";

/** Who is asking, as far as the plan is concerned. */
export interface PlanUser {
  uid: string;
  /** Signed in anonymously, with no linked identity. */
  isGuest: boolean;
}

/** Which plan a user is on. */
export type PlanResolver = (user: PlanUser) => Promise<Plan>;

/**
 * An anonymous user with no linked identity. A token minted just before a Google link can still say
 * `anonymous` as its sign-in provider, but it already lists the Google identity, so it counts as signed in.
 */
export function isGuest(auth: CallableAuth): boolean {
  const firebase = auth.token?.firebase;
  return firebase?.sign_in_provider === "anonymous" && Object.keys(firebase.identities ?? {}).length === 0;
}

/**
 * How long Pro outlasts its stored expiry: the gap between a period ending and its RENEWAL webhook. Because expiry
 * is checked on every read, Pro still ends on time when an EXPIRATION webhook never arrives.
 */
export const PRO_LEEWAY_MS = 15 * 60_000;

/** A record that is active and not past its expiry plus the leeway. No expiry is never Pro: only subscriptions sell. */
export function isProActive(
  sub: Pick<StoredSubscription, "active" | "expiresAt"> | null,
  now: Date,
): boolean {
  if (!sub?.active || sub.expiresAt === null) return false;
  return sub.expiresAt.getTime() + PRO_LEEWAY_MS > now.getTime();
}

/**
 * Guests are on the guest plan; a signed-in user is Pro while `subscriptions/{uid}` says so, else free. A failed
 * read is logged and counts as free: a Pro user then only notices past the free limit that day.
 */
export function createPlanResolver(deps: {
  subscriptions: Pick<SubscriptionStore, "get">;
  now: () => Date;
  log: Logger;
}): PlanResolver {
  return async (user) => {
    if (user.isGuest) return "guest";
    try {
      return isProActive(await deps.subscriptions.get(user.uid), deps.now()) ? "pro" : "free";
    } catch (error) {
      deps.log.error("subscription read failed, treating the user as free", {
        uid: user.uid,
        error: errorMessage(error),
      });
      return "free";
    }
  };
}
