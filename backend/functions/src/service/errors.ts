import { HttpsError, type FunctionsErrorCode } from "firebase-functions/v2/https";

import { errorMessage, type Logger } from "../util/logger.js";

/** `details.reason` of every error the functions return; the app switches on it. */
export type ErrorReason =
  | "unauthenticated"
  | "translation_disabled"
  | "bad_request"
  | "unsupported_pair"
  | "empty_text"
  | "not_tibetan_script"
  | "wrong_script"
  | "text_too_long"
  | "rate_limited"
  | "service_budget"
  | "daily_limit"
  | "provider_unavailable"
  | "guest_account"
  | "billing_unavailable";

/** An expected refusal, returned to the app as an HttpsError with `details.reason`. */
export class TranslationError extends Error {
  constructor(
    readonly code: FunctionsErrorCode,
    readonly reason: ErrorReason,
    message: string,
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = "TranslationError";
  }
}

/** Expected refusals keep their code and reason; anything else is logged and becomes `internal`. */
export function toHttpsError(error: unknown, log: Logger, context: Record<string, unknown> = {}): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof TranslationError) {
    return new HttpsError(error.code, error.message, { reason: error.reason, ...error.details });
  }
  log.error("unexpected failure", { ...context, error: errorMessage(error) });
  return new HttpsError("internal", "Something went wrong. Please try again.");
}
