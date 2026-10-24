import { z } from "zod";

import {
  MAX_CHARS_CEILING,
  routeFor,
  supportedPairs,
  type ConfigSource,
  type PlanLimits,
  type RuntimeConfig,
} from "../config/runtimeConfig.js";
import { dayWindow, type DayWindow } from "../domain/day.js";
import { LANG_CODES, pairKey, type LangCode, type PairKey } from "../domain/languages.js";
import { checkScript, codePointLength, normalize } from "../domain/normalize.js";
import type { RoutedTranslation, Router } from "../providers/router.js";
import type { ProviderId } from "../providers/types.js";
import type { BudgetStore } from "../store/budgetStore.js";
import { cacheKey } from "../store/cacheKey.js";
import { DailyLimitError, type QuotaStore, type Usage } from "../store/quotaStore.js";
import type { CacheHit, TranslationCache } from "../store/translationCache.js";
import { errorMessage, type Logger } from "../util/logger.js";
import { toHttpsError, TranslationError } from "./errors.js";
import { isGuest, type Plan, type PlanResolver, type PlanUser } from "./plan.js";
import type { RateLimiter } from "./rateLimiter.js";

/** The parts of a callable's auth the handlers use: the uid and the ID token's Firebase claims. */
export interface CallableAuth {
  uid: string;
  token?: { firebase?: { sign_in_provider?: string; identities?: Record<string, unknown> } };
}

/** The parts of a callable request the handlers use. */
export interface CallableLike {
  data: unknown;
  auth?: CallableAuth | undefined;
}

export interface StatusDeps {
  config: ConfigSource;
  quota: QuotaStore;
  resolvePlan: PlanResolver;
  log: Logger;
  now: () => Date;
}

export interface Deps extends StatusDeps {
  cache: TranslationCache;
  budget: BudgetStore;
  router: Router;
  rateLimiter: RateLimiter;
}

export interface QuotaInfo {
  plan: Plan;
  /** Translations counted today. */
  used: number;
  limit: number;
  remaining: number;
  /** ISO instant of the user's next local midnight. */
  resetsAt: string;
  /** Characters allowed per translation. */
  maxChars: number;
}

export interface TranslateResponse {
  translation: string;
  source: LangCode;
  target: LangCode;
  /** Served from cache: not counted against the daily limit. */
  cached: boolean;
  /** Who translated it, for attribution. */
  provider: ProviderId;
  /** Absent only on a cached reply when today's usage could not be read; a hit does not change the count. */
  quota?: QuotaInfo;
}

export interface StatusResponse extends QuotaInfo {
  enabled: boolean;
  pairs: PairKey[];
  /** The free plan's daily limit, so a guest can be told how many more signing in gives. */
  freeLimit: number;
  /** The remote paywall switch (`config/translation.billing.enabled`). */
  billingEnabled: boolean;
  /** Pro's daily fair-use limit and characters per translation, for the paywall's table. */
  proLimit: number;
  proMaxChars: number;
}

const timeZoneSchema = z.string().max(64).optional();

const translateRequestSchema = z.object({
  text: z.string(),
  source: z.enum(LANG_CODES),
  target: z.enum(LANG_CODES),
  timeZone: timeZoneSchema,
});

const statusRequestSchema = z.object({ timeZone: timeZoneSchema }).optional();

/** The signed-in user (anonymous counts); unauthenticated without one. */
export function requireUser(request: CallableLike): PlanUser {
  const auth = request.auth;
  if (!auth?.uid) throw new TranslationError("unauthenticated", "unauthenticated", "Sign-in required.");
  return { uid: auth.uid, isGuest: isGuest(auth) };
}

function quotaInfo(plan: Plan, limits: PlanLimits, usage: Usage, day: DayWindow): QuotaInfo {
  return {
    plan,
    used: usage.count,
    limit: limits.dailyLimit,
    remaining: Math.max(0, limits.dailyLimit - usage.count),
    resetsAt: day.resetsAt.toISOString(),
    maxChars: limits.maxChars,
  };
}

function limitsFor(config: RuntimeConfig, plan: Plan): PlanLimits {
  return config[plan];
}

/** Validates and normalises the text; returns it with its length in code points. */
function prepareText(raw: string, source: LangCode, maxChars: number): { text: string; length: number } {
  if (raw.length > MAX_CHARS_CEILING) {
    throw new TranslationError("invalid-argument", "text_too_long", "The text is too long.", {
      maxChars,
      length: codePointLength(raw),
    });
  }
  const text = normalize(raw);
  if (text === "")
    throw new TranslationError("invalid-argument", "empty_text", "There is no text to translate.");
  const script = checkScript(text, source);
  if (script !== "ok") {
    throw new TranslationError(
      "invalid-argument",
      script,
      "The text is not in the source language's script.",
    );
  }
  const length = codePointLength(text);
  if (length > maxChars) {
    throw new TranslationError("invalid-argument", "text_too_long", "The text is too long.", {
      maxChars,
      length,
    });
  }
  return { text, length };
}

export async function handleTranslate(request: CallableLike, deps: Deps): Promise<TranslateResponse> {
  const started = Date.now();
  const event: Record<string, unknown> = { uid: request.auth?.uid };
  try {
    const user = requireUser(request);
    const { uid } = user;
    const config = await deps.config.get();
    if (!config.enabled) {
      throw new TranslationError("failed-precondition", "translation_disabled", "Translation is turned off.");
    }
    const parsed = translateRequestSchema.safeParse(request.data);
    if (!parsed.success) throw new TranslationError("invalid-argument", "bad_request", "Malformed request.");
    const { source, target, timeZone } = parsed.data;
    const pair = pairKey(source, target);
    event.pair = pair;
    const route = routeFor(config, pair);
    if (route.length === 0) {
      throw new TranslationError("invalid-argument", "unsupported_pair", "This direction is not available.");
    }

    const plan = await deps.resolvePlan(user);
    const limits = limitsFor(config, plan);
    const { text, length } = prepareText(parsed.data.text, source, limits.maxChars);
    event.chars = length;

    const rate = deps.rateLimiter.take(uid, config.rateLimitPerMinute);
    if (!rate.ok) {
      throw new TranslationError("resource-exhausted", "rate_limited", "Too many requests.", {
        retryAfterSec: rate.retryAfterSec,
      });
    }

    const day = dayWindow(timeZone, deps.now());
    const key = cacheKey(config.cache.generation, source, target, text);
    event.key = key.slice(0, 12);

    const [hit, usage] = await Promise.all([
      deps.cache.get(key, config.cache.ttlDays).catch((error: unknown): CacheHit | null => {
        deps.log.warn("cache read failed, treating as a miss", { ...event, error: errorMessage(error) });
        return null;
      }),
      // Only a hit uses it; a miss counts through the reservation below.
      deps.quota.read(uid, day.dayKey).catch((error: unknown): Usage | null => {
        deps.log.warn("usage read failed", { ...event, error: errorMessage(error) });
        return null;
      }),
    ]);
    if (hit) {
      deps.log.info("translate", { ...event, outcome: "cached", tier: hit.tier, provider: hit.provider });
      return {
        translation: hit.translation,
        source,
        target,
        cached: true,
        provider: hit.provider,
        ...(usage ? { quota: quotaInfo(plan, limits, usage, day) } : {}),
      };
    }

    if (await deps.budget.isOver(config.globalDailyCharBudget)) {
      throw new TranslationError(
        "resource-exhausted",
        "service_budget",
        "Translation is busy. Try again later.",
      );
    }

    let reserved: Usage;
    try {
      reserved = await deps.quota.reserve(uid, day.dayKey, limits.dailyLimit, length);
    } catch (error) {
      if (error instanceof DailyLimitError) {
        throw new TranslationError("resource-exhausted", "daily_limit", "Daily limit reached.", {
          limit: error.limit,
          resetsAt: day.resetsAt.toISOString(),
          plan,
          freeLimit: config.free.dailyLimit,
        });
      }
      throw error;
    }

    let result: RoutedTranslation;
    try {
      result = await deps.router.translate(route, text, source, target);
    } catch (error) {
      await deps.quota.refund(uid, day.dayKey, length).catch((refundError: unknown) => {
        deps.log.error("refund failed", { ...event, error: errorMessage(refundError) });
      });
      deps.log.warn("all providers failed", { ...event, error: errorMessage(error) });
      throw new TranslationError(
        "unavailable",
        "provider_unavailable",
        "Translation is unavailable right now.",
      );
    }

    const [cacheWrite, budgetWrite] = await Promise.allSettled([
      deps.cache.put(
        key,
        {
          source,
          target,
          sourceText: text,
          translation: result.translation,
          provider: result.provider,
          generation: config.cache.generation,
        },
        config.cache.ttlDays,
      ),
      deps.budget.add(length),
    ]);
    if (cacheWrite.status === "rejected") {
      deps.log.error("cache write failed", { ...event, error: errorMessage(cacheWrite.reason) });
    }
    if (budgetWrite.status === "rejected") {
      deps.log.error("budget write failed", { ...event, error: errorMessage(budgetWrite.reason) });
    }

    deps.log.info("translate", {
      ...event,
      outcome: "translated",
      provider: result.provider,
      latencyMs: Date.now() - started,
    });
    return {
      translation: result.translation,
      source,
      target,
      cached: false,
      provider: result.provider,
      quota: quotaInfo(plan, limits, reserved, day),
    };
  } catch (error) {
    if (error instanceof TranslationError) {
      deps.log.info("translate", { ...event, outcome: "refused", reason: error.reason });
    }
    throw toHttpsError(error, deps.log, event);
  }
}

export async function handleStatus(request: CallableLike, deps: StatusDeps): Promise<StatusResponse> {
  try {
    const user = requireUser(request);
    const parsed = statusRequestSchema.safeParse(request.data ?? undefined);
    if (!parsed.success) throw new TranslationError("invalid-argument", "bad_request", "Malformed request.");
    const config = await deps.config.get();
    const plan = await deps.resolvePlan(user);
    const limits = limitsFor(config, plan);
    const day = dayWindow(parsed.data?.timeZone, deps.now());
    const usage = await deps.quota.read(user.uid, day.dayKey);
    return {
      enabled: config.enabled,
      ...quotaInfo(plan, limits, usage, day),
      pairs: supportedPairs(config),
      freeLimit: config.free.dailyLimit,
      billingEnabled: config.billing.enabled,
      proLimit: config.pro.dailyLimit,
      proMaxChars: config.pro.maxChars,
    };
  } catch (error) {
    throw toHttpsError(error, deps.log, { uid: request.auth?.uid });
  }
}
