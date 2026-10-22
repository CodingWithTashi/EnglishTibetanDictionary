import { errorMessage } from "../util/logger.js";
import { ProviderError, type ProviderErrorKind, type ProviderId, type Fetch } from "./types.js";

/** A provider's HTTP reply: status, `Retry-After` in ms, and the parsed JSON body (undefined if not JSON). */
export interface JsonReply {
  status: number;
  retryAfterMs: number | undefined;
  body: unknown;
}

/** POSTs JSON. A network failure or abort becomes a `transient` ProviderError. */
export async function postJson(
  provider: ProviderId,
  fetchImpl: Fetch,
  url: string,
  init: { headers: Record<string, string>; body: unknown; signal: AbortSignal },
): Promise<JsonReply> {
  let response: Response;
  try {
    response = await fetchImpl(url, {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=UTF-8", ...init.headers },
      body: JSON.stringify(init.body),
      signal: init.signal,
    });
  } catch (error) {
    throw new ProviderError(provider, "transient", `request failed: ${errorMessage(error)}`, {
      cause: error,
    });
  }
  const text = await response.text().catch(() => "");
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    body = undefined;
  }
  return { status: response.status, retryAfterMs: retryAfterMs(response.headers.get("retry-after")), body };
}

function retryAfterMs(header: string | null): number | undefined {
  if (header === null) return undefined;
  const seconds = Number(header);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds * 1_000 : undefined;
}

/** The kind of failure an HTTP status means, before provider-specific refinement of a 400. */
export function kindForStatus(status: number): ProviderErrorKind {
  if (status === 401 || status === 403) return "auth";
  if (status === 429) return "rate_limited";
  if (status === 408 || status >= 500) return "transient";
  return "bad_response";
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
