import { describe, expect, test, vi } from "vitest";

import { handleWebhook, webhookUids, type WebhookRequest } from "../../src/service/webhookHandler.js";
import { fakeLogger } from "./fakes.js";

const SECRET = "whsec-0123456789abcdef0123456789abcdef";

function setup(options: { failFor?: string[]; secret?: string } = {}) {
  const refreshed: string[] = [];
  const refresh = vi.fn((uid: string) => {
    refreshed.push(uid);
    return options.failFor?.includes(uid)
      ? Promise.reject(new Error("revenuecat down"))
      : Promise.resolve(null);
  });
  const log = fakeLogger();
  return { deps: { authSecret: () => options.secret ?? SECRET, refresh, log }, refreshed, log };
}

/** `authorization: null` sends no Authorization header. */
function post(body: unknown, authorization: string | null = SECRET, method = "POST"): WebhookRequest {
  return {
    method,
    header: (name) => (name.toLowerCase() === "authorization" ? (authorization ?? undefined) : undefined),
    body,
  };
}

const event = (fields: Record<string, unknown>) => ({
  api_version: "1.0",
  event: {
    id: "evt-1",
    type: "INITIAL_PURCHASE",
    environment: "SANDBOX",
    app_user_id: "user-1",
    original_app_user_id: "user-1",
    aliases: ["user-1"],
    price: 0.58,
    currency: "INR",
    subscriber_attributes: { $email: { value: "someone@example.com" } },
    store: "PLAY_STORE",
    ...fields,
  },
});

describe("handleWebhook", () => {
  test("a purchase refreshes the buyer and answers 200", async () => {
    const { deps, refreshed } = setup();
    expect(await handleWebhook(post(event({})), deps)).toEqual({ status: 200, body: "ok" });
    expect(refreshed).toEqual(["user-1"]);
  });

  test("a method other than POST is 405", async () => {
    const { deps, refreshed } = setup();
    expect((await handleWebhook(post(event({}), SECRET, "GET"), deps)).status).toBe(405);
    expect(refreshed).toEqual([]);
  });

  test.each([
    ["missing", null],
    ["wrong", "whsec-wrong"],
    ["longer", `${SECRET}x`],
    ["shorter", SECRET.slice(0, -1)],
    ["differently cased", SECRET.toUpperCase()],
    ["as a bearer token", `Bearer ${SECRET}`],
  ])(
    "a %s Authorization header is 401, with no refresh and the header never logged",
    async (_name, header) => {
      const { deps, refreshed, log } = setup();
      expect((await handleWebhook(post(event({}), header), deps)).status).toBe(401);
      expect(refreshed).toEqual([]);
      const logged = JSON.stringify(log.entries);
      expect(logged).not.toContain(SECRET);
      if (header) expect(logged).not.toContain(header);
    },
  );

  test("an empty configured secret accepts nothing", async () => {
    const { deps, refreshed } = setup({ secret: "" });
    expect((await handleWebhook(post(event({}), ""), deps)).status).toBe(401);
    expect(refreshed).toEqual([]);
  });

  test.each([
    ["no body", undefined],
    ["no event", { api_version: "1.0" }],
    ["an event with no type", { event: { id: "evt-1" } }],
    ["an event with no id", { event: { type: "RENEWAL" } }],
    ["a string", "event"],
  ])("%s is 400", async (_name, body) => {
    const { deps, refreshed } = setup();
    expect((await handleWebhook(post(body), deps)).status).toBe(400);
    expect(refreshed).toEqual([]);
  });

  test("a TEST event is 200, logged, with no refresh", async () => {
    const { deps, refreshed, log } = setup();
    expect((await handleWebhook(post(event({ type: "TEST" })), deps)).status).toBe(200);
    expect(refreshed).toEqual([]);
    expect(log.entries).toContainEqual(
      expect.objectContaining({
        data: expect.objectContaining({ type: "TEST", outcome: "test" }) as unknown,
      }),
    );
  });

  test("a transfer refreshes both sides", async () => {
    const { deps, refreshed } = setup();
    const transfer = event({
      type: "TRANSFER",
      app_user_id: undefined,
      original_app_user_id: undefined,
      aliases: undefined,
      transferred_from: ["old-user"],
      transferred_to: ["new-user"],
    });
    expect((await handleWebhook(post(transfer), deps)).status).toBe(200);
    expect(refreshed.sort()).toEqual(["new-user", "old-user"]);
  });

  test("one failed refresh is 500 after every refresh has run", async () => {
    const { deps, refreshed } = setup({ failFor: ["user-1"] });
    const both = event({ aliases: ["user-1", "user-2"] });
    expect((await handleWebhook(post(both), deps)).status).toBe(500);
    expect(refreshed.sort()).toEqual(["user-1", "user-2"]);
  });

  test("the log line has ids, type, environment, uid count and outcome, and nothing from the purchase", async () => {
    const { deps, log } = setup();
    await handleWebhook(post(event({ aliases: ["user-1", "user-2"] })), deps);
    expect(log.entries).toContainEqual({
      level: "info",
      message: "revenuecat webhook",
      data: { id: "evt-1", type: "INITIAL_PURCHASE", environment: "SANDBOX", uids: 2, outcome: "refreshed" },
    });
    const logged = JSON.stringify(log.entries);
    for (const text of ["someone@example.com", "0.58", "INR"]) expect(logged).not.toContain(text);
  });
});

describe("webhookUids", () => {
  test("collects every id field, deduplicated", () => {
    expect(
      webhookUids({
        app_user_id: "a",
        original_app_user_id: "b",
        aliases: ["a", "c"],
        transferred_from: ["d"],
        transferred_to: ["e", "a"],
      }).sort(),
    ).toEqual(["a", "b", "c", "d", "e"]);
  });

  test("skips anonymous and implausible ids", () => {
    expect(
      webhookUids({
        app_user_id: "$RCAnonymousID:8a7b6c",
        aliases: ["", "a/b", "x".repeat(129), "x".repeat(128), 42, null],
      }),
    ).toEqual(["x".repeat(128)]);
  });

  test("caps at 10 uids", () => {
    const aliases = Array.from({ length: 12 }, (_, i) => `user-${i}`);
    expect(webhookUids({ aliases })).toHaveLength(10);
  });

  test("ignores fields that are not strings or lists", () => {
    expect(webhookUids({ app_user_id: 7, aliases: "user-1", transferred_to: { 0: "x" } })).toEqual([]);
  });
});
