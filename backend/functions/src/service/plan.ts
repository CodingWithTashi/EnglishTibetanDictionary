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
 * Guests are on the guest plan and everyone signed in is free, until the billing phase verifies Play
 * subscriptions server-side and this also reads the user's entitlement.
 */
export const resolvePlan: PlanResolver = (user) => Promise.resolve(user.isGuest ? "guest" : "free");
