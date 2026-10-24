import type { LangCode } from "../domain/languages.js";
import { errorMessage, type Logger } from "../util/logger.js";
import { ProviderError, type ProviderErrorKind, type ProviderId, type TranslationProvider } from "./types.js";

export type AttemptOutcome = ProviderErrorKind | "not_configured" | "circuit_open" | "deadline_exceeded";

export interface Attempt {
  provider: ProviderId;
  outcome: AttemptOutcome;
}

export class AllProvidersFailedError extends Error {
  constructor(readonly attempts: Attempt[]) {
    super(`no provider translated the text: ${attempts.map((a) => `${a.provider}=${a.outcome}`).join(", ")}`);
    this.name = "AllProvidersFailedError";
  }
}

export interface RoutedTranslation {
  translation: string;
  provider: ProviderId;
}

export interface Router {
  /** Tries the route's providers in order; throws `AllProvidersFailedError` when none answers. */
  translate(
    route: readonly ProviderId[],
    text: string,
    from: LangCode,
    to: LangCode,
  ): Promise<RoutedTranslation>;
}

export interface RouterOptions {
  providers: Partial<Record<ProviderId, TranslationProvider>>;
  log: Logger;
  attemptTimeoutMs?: number;
  deadlineMs?: number;
  breakerThreshold?: number;
  breakerCooldownMs?: number;
  baseRetryWaitMs?: number;
  maxRetryWaitMs?: number;
  sleep?: (ms: number) => Promise<void>;
  now?: () => number;
  random?: () => number;
}

const RETRYABLE = new Set<ProviderErrorKind>(["transient", "rate_limited"]);

/**
 * Failures that say the provider itself is unhealthy. `bad_response` and `unsupported_language` can be caused by
 * one user's input, and must not let a single user switch a provider off for everyone on the instance.
 */
const TRIPS_BREAKER = new Set<ProviderErrorKind>(["transient", "rate_limited", "auth"]);

/**
 * Per-instance circuit breaker: after `threshold` failures in a row a provider is skipped for `cooldownMs`, then
 * one call is let through as a trial; a success closes it.
 */
function createBreaker(threshold: number, cooldownMs: number, now: () => number) {
  const state = new Map<ProviderId, { failures: number; openedAt: number }>();
  return {
    allows(id: ProviderId): boolean {
      const s = state.get(id);
      if (!s || s.failures < threshold) return true;
      if (now() - s.openedAt < cooldownMs) return false;
      s.openedAt = now(); // half-open: this caller is the trial, concurrent ones keep skipping
      return true;
    },
    success(id: ProviderId): void {
      state.delete(id);
    },
    failure(id: ProviderId): void {
      const s = state.get(id) ?? { failures: 0, openedAt: 0 };
      s.failures += 1;
      if (s.failures >= threshold) s.openedAt = now();
      state.set(id, s);
    },
  };
}

export function createRouter(options: RouterOptions): Router {
  const {
    providers,
    log,
    attemptTimeoutMs = 8_000,
    deadlineMs = 20_000,
    breakerThreshold = 5,
    breakerCooldownMs = 30_000,
    baseRetryWaitMs = 250,
    maxRetryWaitMs = 2_000,
    sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)),
    now = Date.now,
    random = Math.random,
  } = options;
  const breaker = createBreaker(breakerThreshold, breakerCooldownMs, now);

  /** One call, abandoned after `timeoutMs` even if the provider ignores the abort signal. */
  async function attemptOnce(
    provider: TranslationProvider,
    text: string,
    from: LangCode,
    to: LangCode,
    timeoutMs: number,
  ): Promise<string> {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_resolve, reject) => {
      timer = setTimeout(() => {
        controller.abort();
        reject(new ProviderError(provider.id, "transient", `timed out after ${timeoutMs} ms`));
      }, timeoutMs);
    });
    try {
      return await Promise.race([provider.translate({ text, from, to, signal: controller.signal }), timeout]);
    } catch (error) {
      if (error instanceof ProviderError) throw error;
      throw new ProviderError(provider.id, "bad_response", `provider threw: ${errorMessage(error)}`, {
        cause: error,
      });
    } finally {
      clearTimeout(timer);
    }
  }

  function retryWait(error: ProviderError): number {
    const wait =
      error.kind === "rate_limited" && error.retryAfterMs !== undefined
        ? error.retryAfterMs
        : baseRetryWaitMs * (0.5 + random());
    return Math.min(wait, maxRetryWaitMs);
  }

  return {
    async translate(route, text, from, to) {
      const deadline = now() + deadlineMs;
      const attempts: Attempt[] = [];

      for (const id of route) {
        const provider = providers[id];
        if (!provider?.isConfigured()) {
          attempts.push({ provider: id, outcome: "not_configured" });
          continue;
        }
        if (!breaker.allows(id)) {
          attempts.push({ provider: id, outcome: "circuit_open" });
          continue;
        }
        for (let attempt = 1; attempt <= 2; attempt++) {
          const remaining = deadline - now();
          if (remaining <= 0) {
            attempts.push({ provider: id, outcome: "deadline_exceeded" });
            throw new AllProvidersFailedError(attempts);
          }
          try {
            const translation = await attemptOnce(
              provider,
              text,
              from,
              to,
              Math.min(attemptTimeoutMs, remaining),
            );
            breaker.success(id);
            return { translation, provider: id };
          } catch (caught) {
            const error = caught as ProviderError;
            attempts.push({ provider: id, outcome: error.kind });
            if (TRIPS_BREAKER.has(error.kind)) breaker.failure(id);
            const details = {
              provider: id,
              kind: error.kind,
              status: error.status,
              attempt,
              reason: error.message,
            };
            if (error.kind === "auth") log.error("provider rejected our credentials", details);
            else log.warn("provider attempt failed", details);
            if (attempt === 2 || !RETRYABLE.has(error.kind)) break;
            await sleep(Math.min(retryWait(error), Math.max(0, deadline - now())));
          }
        }
      }
      throw new AllProvidersFailedError(attempts);
    },
  };
}
