import { describe, expect, test, vi } from "vitest";

import { createAzureProvider } from "../../src/providers/azure.js";
import { createGoogleProvider } from "../../src/providers/google.js";
import {
  ProviderError,
  type ProviderErrorKind,
  type TranslationProvider,
} from "../../src/providers/types.js";

function respond(status: number, body: unknown, headers: Record<string, string> = {}) {
  return vi.fn<typeof fetch>(() =>
    Promise.resolve(
      new Response(typeof body === "string" ? body : JSON.stringify(body), {
        status,
        headers: { "content-type": "application/json", ...headers },
      }),
    ),
  );
}

function input(text: string, from: "bo" | "dz" | "en", to: "bo" | "dz" | "en") {
  return { text, from, to, signal: new AbortController().signal };
}

async function failure(provider: TranslationProvider, text = "x"): Promise<ProviderError> {
  try {
    await provider.translate(input(text, "bo", "en"));
  } catch (error) {
    if (error instanceof ProviderError) return error;
    throw error;
  }
  throw new Error("expected a ProviderError");
}

function requestOf(fetchMock: ReturnType<typeof respond>) {
  const [target, init = {}] = fetchMock.mock.calls[0] ?? ["about:blank"];
  const href = typeof target === "string" ? target : target instanceof URL ? target.href : target.url;
  const body: unknown = typeof init.body === "string" ? JSON.parse(init.body) : undefined;
  return { url: new URL(href), init, body };
}

const azureOptions = {
  key: "azure-key",
  region: "centralindia",
  endpoint: "https://api.cognitive.microsofttranslator.com/",
};

describe("Azure provider", () => {
  test("posts the text with key and region headers and returns the translation", async () => {
    const fetchMock = respond(200, [{ translations: [{ text: "Tashi Delek", to: "en" }] }]);
    const azure = createAzureProvider({ ...azureOptions, fetch: fetchMock });

    await expect(azure.translate(input("བཀྲ་ཤིས", "bo", "en"))).resolves.toBe("Tashi Delek");

    const { url, init, body } = requestOf(fetchMock);
    expect(url.origin + url.pathname).toBe("https://api.cognitive.microsofttranslator.com/translate");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      "api-version": "3.0",
      from: "bo",
      to: "en",
      textType: "plain",
    });
    expect(init.method).toBe("POST");
    expect(body).toEqual([{ Text: "བཀྲ་ཤིས" }]);
    const headers = new Headers(init.headers);
    expect(headers.get("Ocp-Apim-Subscription-Key")).toBe("azure-key");
    expect(headers.get("Ocp-Apim-Subscription-Region")).toBe("centralindia");
    expect(headers.get("X-ClientTraceId")).toMatch(/^[0-9a-f-]{36}$/);
  });

  test("omits the region header when no region is set", async () => {
    const fetchMock = respond(200, [{ translations: [{ text: "ok", to: "en" }] }]);
    await createAzureProvider({ ...azureOptions, region: "", fetch: fetchMock }).translate(
      input("ཀ", "bo", "en"),
    );
    expect(new Headers(requestOf(fetchMock).init.headers).has("Ocp-Apim-Subscription-Region")).toBe(false);
  });

  test("trims whitespace pasted around the key", async () => {
    const fetchMock = respond(200, [{ translations: [{ text: "ok", to: "en" }] }]);
    await createAzureProvider({ ...azureOptions, key: " azure-key\n", fetch: fetchMock }).translate(
      input("ཀ", "bo", "en"),
    );
    expect(new Headers(requestOf(fetchMock).init.headers).get("Ocp-Apim-Subscription-Key")).toBe("azure-key");
    expect(createAzureProvider({ ...azureOptions, key: "unset\n" }).isConfigured()).toBe(false);
  });

  test("is configured only with a real key", () => {
    expect(createAzureProvider({ ...azureOptions, key: "" }).isConfigured()).toBe(false);
    expect(createAzureProvider({ ...azureOptions, key: "unset" }).isConfigured()).toBe(false);
    expect(createAzureProvider(azureOptions).isConfigured()).toBe(true);
    expect(createAzureProvider(azureOptions).id).toBe("azure");
  });

  test.each<[string, number, unknown, Record<string, string>, ProviderErrorKind]>([
    ["bad key", 401, { error: { code: 401000, message: "denied" } }, {}, "auth"],
    ["unsupported language", 400, { error: { code: 400036, message: "bad to" } }, {}, "unsupported_language"],
    ["other bad request", 400, { error: { code: 400050, message: "too long" } }, {}, "bad_response"],
    [
      "throttled",
      429,
      { error: { code: 429001, message: "slow down" } },
      { "retry-after": "1" },
      "rate_limited",
    ],
    ["server error", 503, "unavailable", {}, "transient"],
    ["empty list", 200, [], {}, "bad_response"],
    ["empty text", 200, [{ translations: [{ text: "", to: "en" }] }], {}, "bad_response"],
    ["not JSON", 200, "<html>", {}, "bad_response"],
  ])("%s → %s", async (_name, status, body, headers, kind) => {
    const error = await failure(
      createAzureProvider({ ...azureOptions, fetch: respond(status, body, headers) }),
    );
    expect(error.kind).toBe(kind);
    expect(error.provider).toBe("azure");
  });

  test("reads Retry-After seconds", async () => {
    const error = await failure(
      createAzureProvider({ ...azureOptions, fetch: respond(429, {}, { "retry-after": "1" }) }),
    );
    expect(error.retryAfterMs).toBe(1_000);
    expect(error.status).toBe(429);
  });

  test("a network failure is transient", async () => {
    const fetchMock = vi.fn(() => Promise.reject(new TypeError("fetch failed")));
    expect((await failure(createAzureProvider({ ...azureOptions, fetch: fetchMock }))).kind).toBe(
      "transient",
    );
  });
});

const googleOptions = { projectId: "demo", getAccessToken: () => Promise.resolve("token-1") };

describe("Google provider", () => {
  test("posts a plain-text v3 request with a bearer token and returns the translation", async () => {
    const fetchMock = respond(200, { translations: [{ translatedText: "Hello" }] });
    const google = createGoogleProvider({ ...googleOptions, fetch: fetchMock });

    await expect(google.translate(input("སྐུ་གཟུགས་བཟང་པོ།", "dz", "en"))).resolves.toBe("Hello");

    const { url, init, body } = requestOf(fetchMock);
    expect(url.toString()).toBe(
      "https://translation.googleapis.com/v3/projects/demo/locations/global:translateText",
    );
    expect(body).toEqual({
      contents: ["སྐུ་གཟུགས་བཟང་པོ།"],
      sourceLanguageCode: "dz",
      targetLanguageCode: "en",
      mimeType: "text/plain",
    });
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer token-1");
    expect(new Headers(init.headers).has("x-goog-user-project")).toBe(false);
    expect(google.id).toBe("google");
    expect(google.isConfigured()).toBe(true);
  });

  test("bills a quota project when one is set (local user credentials need it)", async () => {
    const fetchMock = respond(200, { translations: [{ translatedText: "Hello" }] });
    await createGoogleProvider({ ...googleOptions, quotaProject: "demo", fetch: fetchMock }).translate(
      input("ཀ", "bo", "en"),
    );
    expect(new Headers(requestOf(fetchMock).init.headers).get("x-goog-user-project")).toBe("demo");
  });

  test("is not configured without a project", () => {
    expect(createGoogleProvider({ ...googleOptions, projectId: "" }).isConfigured()).toBe(false);
  });

  test.each<[string, number, unknown, ProviderErrorKind]>([
    [
      "permission denied",
      403,
      { error: { code: 403, message: "denied", status: "PERMISSION_DENIED" } },
      "auth",
    ],
    [
      "unsupported language",
      400,
      { error: { code: 400, message: "Source language: bo is not supported", status: "INVALID_ARGUMENT" } },
      "unsupported_language",
    ],
    ["other bad request", 400, { error: { code: 400, message: "Too many contents" } }, "bad_response"],
    ["quota", 429, { error: { code: 429, message: "quota", status: "RESOURCE_EXHAUSTED" } }, "rate_limited"],
    ["server error", 500, { error: { code: 500, message: "internal" } }, "transient"],
    ["empty translation", 200, { translations: [{ translatedText: "" }] }, "bad_response"],
  ])("%s → %s", async (_name, status, body, kind) => {
    const error = await failure(createGoogleProvider({ ...googleOptions, fetch: respond(status, body) }));
    expect(error.kind).toBe(kind);
    expect(error.provider).toBe("google");
  });

  test("an aborted request is transient", async () => {
    const fetchMock = vi.fn(() => Promise.reject(new DOMException("aborted", "AbortError")));
    expect((await failure(createGoogleProvider({ ...googleOptions, fetch: fetchMock }))).kind).toBe(
      "transient",
    );
  });

  test("a token failure is transient and makes no request", async () => {
    const fetchMock = respond(200, {});
    const google = createGoogleProvider({
      projectId: "demo",
      getAccessToken: () => Promise.reject(new Error("metadata server timeout")),
      fetch: fetchMock,
    });
    expect((await failure(google)).kind).toBe("transient");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
