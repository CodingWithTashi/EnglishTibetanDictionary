export type Plan = "free" | "pro";

/** Which plan a user is on. */
export type PlanResolver = (uid: string) => Promise<Plan>;

/**
 * Everyone is on the free plan until the billing phase verifies Play subscriptions server-side and replaces
 * this with a resolver that reads the user's entitlement.
 */
export const resolvePlan: PlanResolver = () => Promise.resolve("free");
