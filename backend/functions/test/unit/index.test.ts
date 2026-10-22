import { describe, expect, test } from "vitest";

import { getTranslationStatus, translate } from "../../src/index.js";

interface Endpoint {
  region?: string[];
  availableMemoryMb?: number;
  cpu?: number;
  concurrency?: number;
  maxInstances?: number;
  timeoutSeconds?: number;
  serviceAccountEmail?: string;
  secretEnvironmentVariables?: { key: string }[];
  callableTrigger?: unknown;
}

const endpointOf = (fn: unknown) => (fn as { __endpoint: Endpoint }).__endpoint;

describe("deployed functions", () => {
  test("translate is a callable in asia-south1 with the Azure key bound", () => {
    const endpoint = endpointOf(translate);
    expect(endpoint).toMatchObject({
      region: ["asia-south1"],
      availableMemoryMb: 512,
      cpu: 1,
      concurrency: 80,
      maxInstances: 10,
      timeoutSeconds: 30,
      serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
      callableTrigger: {},
    });
    expect(endpoint.secretEnvironmentVariables?.map((s) => s.key)).toEqual(["AZURE_TRANSLATOR_KEY"]);
  });

  test("getTranslationStatus is a callable in asia-south1 with no secrets", () => {
    const endpoint = endpointOf(getTranslationStatus);
    expect(endpoint).toMatchObject({ region: ["asia-south1"], maxInstances: 5, callableTrigger: {} });
    expect(endpoint.secretEnvironmentVariables ?? []).toEqual([]);
  });
});
