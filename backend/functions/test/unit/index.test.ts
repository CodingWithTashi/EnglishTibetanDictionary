import type { DecodedIdToken } from "firebase-admin/auth";
import type { CallableRequest } from "firebase-functions/v2/https";
import { describe, expect, test } from "vitest";

import { deleteAccount, getTranslationStatus, translate } from "../../src/index.js";
import { reasonOf, rejection } from "./fakes.js";

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

  test("deleteAccount is a callable in asia-south1 with no secrets", () => {
    const endpoint = endpointOf(deleteAccount);
    expect(endpoint).toMatchObject({
      region: ["asia-south1"],
      maxInstances: 2,
      serviceAccountEmail: "translation-fn@englishtibetandictionary.iam.gserviceaccount.com",
      callableTrigger: {},
    });
    expect(endpoint.secretEnvironmentVariables ?? []).toEqual([]);
  });

  test("getTranslationStatus is a callable in asia-south1 with no secrets", () => {
    const endpoint = endpointOf(getTranslationStatus);
    expect(endpoint).toMatchObject({ region: ["asia-south1"], maxInstances: 5, callableTrigger: {} });
    expect(endpoint.secretEnvironmentVariables ?? []).toEqual([]);
  });
});

// The refusals return before any Firestore or Auth call, so they run here, with no emulator. `.run()` skips
// the framework's App Check and ID-token checks; the handler's own checks still run.
describe("deleteAccount wiring", () => {
  const request = (auth?: CallableRequest["auth"]): CallableRequest => ({
    data: null,
    rawRequest: {} as CallableRequest["rawRequest"],
    acceptsStreaming: false,
    ...(auth ? { auth } : {}),
  });

  test("without a signed-in user is unauthenticated", async () => {
    const error = await rejection(deleteAccount.run(request()));
    expect(error.code).toBe("unauthenticated");
    expect(reasonOf(error)).toBe("unauthenticated");
  });

  test("as a guest is guest_account", async () => {
    const guest = {
      uid: "guest-1",
      token: { firebase: { sign_in_provider: "anonymous", identities: {} } } as unknown as DecodedIdToken,
      rawToken: "test-id-token",
    };
    const error = await rejection(deleteAccount.run(request(guest)));
    expect(error.code).toBe("failed-precondition");
    expect(reasonOf(error)).toBe("guest_account");
  });
});
