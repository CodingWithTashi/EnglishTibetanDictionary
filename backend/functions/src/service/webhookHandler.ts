import { timingSafeEqual } from "node:crypto";

import { z } from "zod";

import { errorMessage, type Logger } from "../util/logger.js";

/** The parts of an HTTP request the webhook reads. */
export interface WebhookRequest {
  method: string;
  /** A request header, looked up case-insensitively. */
  header(name: string): string | undefined;
  body: unknown;
}

export interface WebhookReply {
  status: 200 | 400 | 401 | 405 | 500;
  body: string;
}

export interface WebhookDeps {
  /** The value RevenueCat sends as `Authorization`, from Secret Manager. */
  authSecret: () => string;
  /** Re-reads one customer from RevenueCat into `subscriptions/{uid}`. */
  refresh: (uid: string) => Promise<unknown>;
  log: Logger;
}

const MAX_UIDS = 10;
const MAX_UID_LENGTH = 128;
const ANONYMOUS_PREFIX = "$RCAnonymousID:";
const ID_FIELDS = ["app_user_id", "original_app_user_id"];
const ID_LIST_FIELDS = ["aliases", "transferred_from", "transferred_to"];

const bodySchema = z.looseObject({
  event: z.looseObject({ id: z.string(), type: z.string(), environment: z.string().optional() }),
});

const reply = (status: WebhookReply["status"], body: string): WebhookReply => ({ status, body });

const BEARER_PREFIX = "Bearer ";

/** True only for the configured secret, raw or after a `Bearer ` prefix; an empty secret matches nothing. */
function authorized(header: string | undefined, secret: string): boolean {
  if (!header || !secret) return false;
  const value = header.startsWith(BEARER_PREFIX) ? header.slice(BEARER_PREFIX.length) : header;
  const given = Buffer.from(value);
  const expected = Buffer.from(secret);
  return given.length === expected.length && timingSafeEqual(given, expected);
}

/** A Firebase uid can't be anonymous to RevenueCat, empty, longer than 128 characters or contain a slash. */
function plausibleUid(id: unknown): id is string {
  return (
    typeof id === "string" &&
    id.length > 0 &&
    id.length <= MAX_UID_LENGTH &&
    !id.includes("/") &&
    !id.startsWith(ANONYMOUS_PREFIX)
  );
}

/** Every plausible app user id the event names, without duplicates, at most 10. */
export function webhookUids(event: Record<string, unknown>): string[] {
  const ids = new Set<string>();
  const lists = ID_LIST_FIELDS.map((field) => event[field]).filter((value): value is unknown[] =>
    Array.isArray(value),
  );
  for (const id of [...ID_FIELDS.map((field) => event[field]), ...lists.flat()]) {
    if (plausibleUid(id)) ids.add(id);
  }
  return [...ids].slice(0, MAX_UIDS);
}

/**
 * RevenueCat's webhook. The payload is never trusted: each event only re-reads the customers it names, so a forged
 * call can at most cause a refresh. Any failed refresh answers 500 so RevenueCat retries; the refreshes are
 * idempotent. Logs never carry the header, prices, emails or store tokens.
 */
export async function handleWebhook(request: WebhookRequest, deps: WebhookDeps): Promise<WebhookReply> {
  if (request.method !== "POST") return reply(405, "method not allowed");
  if (!authorized(request.header("authorization"), deps.authSecret())) {
    deps.log.warn("revenuecat webhook: bad authorization");
    return reply(401, "unauthorized");
  }
  const parsed = bodySchema.safeParse(request.body);
  if (!parsed.success) {
    deps.log.warn("revenuecat webhook: unreadable body");
    return reply(400, "bad request");
  }
  const { event } = parsed.data;
  const entry = { id: event.id, type: event.type, environment: event.environment };
  if (event.type === "TEST") {
    deps.log.info("revenuecat webhook", { ...entry, uids: 0, outcome: "test" });
    return reply(200, "ok");
  }
  const uids = webhookUids(event);
  const results = await Promise.allSettled(uids.map((uid) => deps.refresh(uid)));
  const failed = results.filter((result) => result.status === "rejected");
  if (failed.length > 0) {
    deps.log.error("revenuecat webhook", {
      ...entry,
      uids: uids.length,
      outcome: "failed",
      failures: failed.length,
      error: errorMessage(failed[0]?.reason),
    });
    return reply(500, "retry");
  }
  deps.log.info("revenuecat webhook", { ...entry, uids: uids.length, outcome: "refreshed" });
  return reply(200, "ok");
}
