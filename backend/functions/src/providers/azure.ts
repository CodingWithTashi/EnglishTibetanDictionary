import { randomUUID } from "node:crypto";

import { isRecord, kindForStatus, postJson } from "./http.js";
import { ProviderError, type Fetch, type TranslationProvider } from "./types.js";

export interface AzureOptions {
  key: string;
  /** The Translator resource's region; empty for a global single-service resource. */
  region: string;
  endpoint: string;
  fetch?: Fetch;
}

/** Azure error codes for a language it does not translate or does not recognise. */
const UNSUPPORTED_LANGUAGE_CODES = new Set([400019, 400023, 400035, 400036]);

/** Azure Translator, Text Translation v3.0. */
export function createAzureProvider(options: AzureOptions): TranslationProvider {
  const { fetch: fetchImpl = fetch } = options;
  // Secrets set from a file or a pipe often end in a newline, which is not valid in an HTTP header.
  const key = options.key.trim();
  const region = options.region.trim();
  const endpoint = options.endpoint.trim().replace(/\/+$/, "");
  const configured = key !== "" && key !== "unset";

  return {
    id: "azure",
    isConfigured: () => configured,
    async translate({ text, from, to, signal }) {
      if (!configured) throw new ProviderError("azure", "auth", "no key configured");
      const params = new URLSearchParams({ "api-version": "3.0", from, to, textType: "plain" });
      const headers: Record<string, string> = {
        "Ocp-Apim-Subscription-Key": key,
        "X-ClientTraceId": randomUUID(),
      };
      if (region !== "") headers["Ocp-Apim-Subscription-Region"] = region;

      const reply = await postJson("azure", fetchImpl, `${endpoint}/translate?${params.toString()}`, {
        headers,
        body: [{ Text: text }],
        signal,
      });

      if (reply.status !== 200) {
        const error = isRecord(reply.body) && isRecord(reply.body.error) ? reply.body.error : {};
        const code = typeof error.code === "number" ? error.code : undefined;
        const kind =
          reply.status === 400 && code !== undefined && UNSUPPORTED_LANGUAGE_CODES.has(code)
            ? "unsupported_language"
            : kindForStatus(reply.status);
        throw new ProviderError("azure", kind, `HTTP ${reply.status} (code ${code ?? "none"})`, {
          status: reply.status,
          retryAfterMs: reply.retryAfterMs,
        });
      }

      const translated: unknown = Array.isArray(reply.body)
        ? (reply.body[0] as { translations?: { text?: unknown }[] } | undefined)?.translations?.[0]?.text
        : undefined;
      if (typeof translated !== "string" || translated.trim() === "") {
        throw new ProviderError("azure", "bad_response", "no translation in the reply", { status: 200 });
      }
      return translated;
    },
  };
}
