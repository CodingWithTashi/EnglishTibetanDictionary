import { z } from "zod";

import { errorMessage } from "../util/logger.js";

/** Which Pro subscription: `dictionary_pro` is monthly, `dictionary_pro_annually` yearly. */
export type ProProduct = "monthly" | "yearly";

/** The `pro` entitlement as RevenueCat reported it. */
export interface ProEntitlement {
  /** `expiresAt` is after RevenueCat's request time. */
  active: boolean;
  /** The later of the period end and the grace-period end; null for an entitlement with no expiry. */
  expiresAt: Date | null;
  /** Null for a product that is not one of the two Pro subscriptions. */
  product: ProProduct | null;
  /** No cancellation has been detected. */
  willRenew: boolean;
  billingIssue: boolean;
  sandbox: boolean;
  /** RevenueCat's store name, such as `play_store`. */
  store: string;
}

/** One read of a RevenueCat customer: when RevenueCat answered, and its `pro` entitlement (null if none). */
export interface SubscriberSnapshot {
  fetchedAt: Date;
  pro: ProEntitlement | null;
}

export type RevenueCatErrorKind = "network" | "rate_limited" | "server" | "auth" | "bad_response";

/** RevenueCat could not be read, or answered with something that isn't a subscriber. Never means "not Pro". */
export class RevenueCatError extends Error {
  constructor(
    readonly kind: RevenueCatErrorKind,
    message: string,
  ) {
    super(message);
    this.name = "RevenueCatError";
  }
}

const ENTITLEMENT = "pro";
const API = "https://api.revenuecat.com/v1/subscribers/";

const PRODUCTS: Readonly<Record<string, ProProduct>> = {
  dictionary_pro: "monthly",
  dictionary_pro_annually: "yearly",
};

/** The product for a Play id, matched exactly on the subscription id before any `:basePlan`. */
export function productOf(productIdentifier: string): ProProduct | null {
  const [subscriptionId = ""] = productIdentifier.split(":");
  return Object.hasOwn(PRODUCTS, subscriptionId) ? (PRODUCTS[subscriptionId] ?? null) : null;
}

const optionalDate = z.string().nullable().optional();

const bodySchema = z.looseObject({
  request_date_ms: z.number(),
  subscriber: z.looseObject({
    entitlements: z.record(z.string(), z.unknown()),
    subscriptions: z.record(z.string(), z.unknown()).optional(),
  }),
});

const entitlementSchema = z.looseObject({
  expires_date: optionalDate,
  grace_period_expires_date: optionalDate,
  product_identifier: z.string(),
});

const subscriptionSchema = z.looseObject({
  unsubscribe_detected_at: optionalDate,
  billing_issues_detected_at: optionalDate,
  is_sandbox: z.boolean().optional(),
  store: z.string().optional(),
});

function badResponse(what: string): RevenueCatError {
  return new RevenueCatError("bad_response", `unexpected subscriber reply: ${what}`);
}

function dateOf(value: string | null | undefined, field: string): Date | null {
  if (value === null || value === undefined) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw badResponse(`unreadable ${field}`);
  return date;
}

/** The subscription entry for the entitlement's product, keyed by the full id or the id before `:`. */
function subscriptionFor(subscriptions: Record<string, unknown>, productIdentifier: string): unknown {
  const [subscriptionId = ""] = productIdentifier.split(":");
  for (const key of [productIdentifier, subscriptionId]) {
    if (Object.hasOwn(subscriptions, key)) return subscriptions[key];
  }
  return Object.entries(subscriptions).find(([key]) => key.split(":")[0] === subscriptionId)?.[1];
}

/**
 * Maps a v1 `GET /subscribers/{id}` body. An unknown shape throws `bad_response`: it must never read as "not Pro",
 * or a RevenueCat format change would take Pro away from every subscriber.
 */
export function toSnapshot(body: unknown): SubscriberSnapshot {
  const parsed = bodySchema.safeParse(body);
  if (!parsed.success) throw badResponse("not a subscriber");
  const fetchedAt = new Date(parsed.data.request_date_ms);
  const { entitlements, subscriptions = {} } = parsed.data.subscriber;
  if (!Object.hasOwn(entitlements, ENTITLEMENT)) return { fetchedAt, pro: null };

  const entitlement = entitlementSchema.safeParse(entitlements[ENTITLEMENT]);
  if (!entitlement.success) throw badResponse("pro entitlement");
  const { product_identifier: productIdentifier } = entitlement.data;
  const expires = dateOf(entitlement.data.expires_date, "expires_date");
  const grace = dateOf(entitlement.data.grace_period_expires_date, "grace_period_expires_date");
  const expiresAt = expires && grace && grace > expires ? grace : expires;

  const entry = subscriptionFor(subscriptions, productIdentifier);
  const subscription = entry === undefined ? undefined : subscriptionSchema.safeParse(entry);
  if (subscription?.success === false) throw badResponse("subscription entry");
  const details = subscription?.data;
  return {
    fetchedAt,
    pro: {
      active: expiresAt !== null && expiresAt > fetchedAt,
      expiresAt,
      product: productOf(productIdentifier),
      // With no entry RevenueCat can't say it renews, so neither do we.
      willRenew: details !== undefined && dateOf(details.unsubscribe_detected_at, "unsubscribe") === null,
      billingIssue: dateOf(details?.billing_issues_detected_at, "billing_issues_detected_at") !== null,
      sandbox: details?.is_sandbox ?? false,
      store: details?.store ?? "unknown",
    },
  };
}

export interface RevenueCatClient {
  /** The customer's current state; throws `RevenueCatError`. Creates an empty customer for an unknown id. */
  getSubscriber(uid: string): Promise<SubscriberSnapshot>;
}

const RETRYABLE: ReadonlySet<RevenueCatErrorKind> = new Set(["network", "rate_limited", "server"]);

function kindForStatus(status: number): RevenueCatErrorKind {
  if (status === 401 || status === 403) return "auth";
  if (status === 429) return "rate_limited";
  if (status >= 500) return "server";
  return "bad_response";
}

/**
 * RevenueCat's REST API v1 with a secret key. Each request times out after `timeoutMs`; a network error, 429 or 5xx
 * is retried once after `retryDelayMs`. Error messages never carry the key or the body.
 */
export function createRevenueCatClient(options: {
  apiKey: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  retryDelayMs?: number;
}): RevenueCatClient {
  const { apiKey, fetchImpl = fetch, timeoutMs = 10_000, retryDelayMs = 500 } = options;

  async function once(uid: string): Promise<SubscriberSnapshot> {
    let status: number;
    let text: string;
    try {
      const response = await fetchImpl(API + encodeURIComponent(uid), {
        method: "GET",
        headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" },
        signal: AbortSignal.timeout(timeoutMs),
      });
      status = response.status;
      text = await response.text();
    } catch (error) {
      throw new RevenueCatError("network", `request failed: ${errorMessage(error)}`);
    }
    if (status !== 200 && status !== 201) throw new RevenueCatError(kindForStatus(status), `HTTP ${status}`);
    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      throw badResponse("not JSON");
    }
    return toSnapshot(body);
  }

  return {
    async getSubscriber(uid) {
      try {
        return await once(uid);
      } catch (error) {
        if (!(error instanceof RevenueCatError) || !RETRYABLE.has(error.kind)) throw error;
        await new Promise((resolve) => setTimeout(resolve, retryDelayMs));
        return once(uid);
      }
    },
  };
}
