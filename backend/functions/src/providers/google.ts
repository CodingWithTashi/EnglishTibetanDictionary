import { errorMessage } from "../util/logger.js";
import { isRecord, kindForStatus, postJson } from "./http.js";
import { ProviderError, type Fetch, type TranslationProvider } from "./types.js";

export interface GoogleOptions {
  projectId: string;
  /** An OAuth token with the cloud-translation scope (the function's service account in production). */
  getAccessToken: () => Promise<string>;
  /**
   * Project billed for the call (`x-goog-user-project`). Only for local user credentials, which Google refuses
   * without one; the deployed service account bills its own project and must not send it.
   */
  quotaProject?: string;
  fetch?: Fetch;
}

/** Google Cloud Translation, v3 `translateText` with the NMT model. */
export function createGoogleProvider(options: GoogleOptions): TranslationProvider {
  const { projectId, getAccessToken, quotaProject, fetch: fetchImpl = fetch } = options;
  const url = `https://translation.googleapis.com/v3/projects/${encodeURIComponent(projectId)}/locations/global:translateText`;

  return {
    id: "google",
    isConfigured: () => projectId !== "",
    async translate({ text, from, to, signal }) {
      let token: string;
      try {
        token = await getAccessToken();
      } catch (error) {
        throw new ProviderError("google", "transient", `no access token: ${errorMessage(error)}`, {
          cause: error,
        });
      }

      const reply = await postJson("google", fetchImpl, url, {
        headers: {
          Authorization: `Bearer ${token}`,
          ...(quotaProject ? { "x-goog-user-project": quotaProject } : {}),
        },
        // text/plain: the default (HTML) escapes quotes and ampersands in the output.
        body: { contents: [text], sourceLanguageCode: from, targetLanguageCode: to, mimeType: "text/plain" },
        signal,
      });

      if (reply.status !== 200) {
        const error = isRecord(reply.body) && isRecord(reply.body.error) ? reply.body.error : {};
        const message = typeof error.message === "string" ? error.message : "";
        const kind =
          reply.status === 400 && /language/i.test(message)
            ? "unsupported_language"
            : kindForStatus(reply.status);
        throw new ProviderError("google", kind, `HTTP ${reply.status}: ${message}`, {
          status: reply.status,
          retryAfterMs: reply.retryAfterMs,
        });
      }

      const translated: unknown = isRecord(reply.body)
        ? (reply.body.translations as { translatedText?: unknown }[] | undefined)?.[0]?.translatedText
        : undefined;
      if (typeof translated !== "string" || translated.trim() === "") {
        throw new ProviderError("google", "bad_response", "no translation in the reply", { status: 200 });
      }
      return translated;
    },
  };
}
