import type { LangCode } from "../domain/languages.js";

export const PROVIDER_IDS = ["azure", "google"] as const;

export type ProviderId = (typeof PROVIDER_IDS)[number];

export function isProviderId(value: unknown): value is ProviderId {
  return typeof value === "string" && (PROVIDER_IDS as readonly string[]).includes(value);
}

export interface TranslateInput {
  text: string;
  from: LangCode;
  to: LangCode;
  signal: AbortSignal;
}

/** A machine-translation service. Implementations throw only `ProviderError`. */
export interface TranslationProvider {
  readonly id: ProviderId;
  /** False when its credentials are missing, so the router skips it. */
  isConfigured(): boolean;
  translate(input: TranslateInput): Promise<string>;
}

/**
 * - `unsupported_language`: the provider does not translate this direction.
 * - `auth`: bad or missing credentials, or the API is not enabled.
 * - `rate_limited`: throttled or out of quota.
 * - `transient`: network failure, timeout or 5xx; worth one retry.
 * - `bad_response`: any other rejection, or a reply without a usable translation.
 */
export type ProviderErrorKind =
  "unsupported_language" | "auth" | "rate_limited" | "transient" | "bad_response";

export class ProviderError extends Error {
  readonly status: number | undefined;
  readonly retryAfterMs: number | undefined;

  constructor(
    readonly provider: ProviderId,
    readonly kind: ProviderErrorKind,
    message: string,
    options: { status?: number; retryAfterMs?: number; cause?: unknown } = {},
  ) {
    super(`${provider}: ${message}`, { cause: options.cause });
    this.name = "ProviderError";
    this.status = options.status;
    this.retryAfterMs = options.retryAfterMs;
  }
}

export type Fetch = typeof fetch;
