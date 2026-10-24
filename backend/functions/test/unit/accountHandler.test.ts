import { describe, expect, test } from "vitest";

import { handleDeleteAccount, type AccountDeps } from "../../src/service/accountHandler.js";
import { fakeLogger, reasonOf, rejection } from "./fakes.js";

function setup(options: { failDelete?: Error; failSubscriptionDelete?: Error } = {}) {
  const calls: string[] = [];
  const deps = {
    quota: {
      deleteAll(uid: string) {
        calls.push(`deleteAll:${uid}`);
        return Promise.resolve(3);
      },
    },
    subscriptions: {
      delete(uid: string) {
        calls.push(`deleteSubscription:${uid}`);
        return options.failSubscriptionDelete
          ? Promise.reject(options.failSubscriptionDelete)
          : Promise.resolve();
      },
    },
    users: {
      deleteUser(uid: string) {
        calls.push(`deleteUser:${uid}`);
        return options.failDelete ? Promise.reject(options.failDelete) : Promise.resolve();
      },
    },
    log: fakeLogger(),
  } satisfies AccountDeps;
  return { deps, calls };
}

const google = (uid: string) => ({
  data: null,
  auth: { uid, token: { firebase: { sign_in_provider: "google.com", identities: { "google.com": ["1"] } } } },
});

describe("handleDeleteAccount", () => {
  test("a signed-in user's usage, subscription record and account are deleted, the account last", async () => {
    const { deps, calls } = setup();
    expect(await handleDeleteAccount(google("user-1"), deps)).toEqual({ deleted: true });
    expect(calls).toEqual(["deleteAll:user-1", "deleteSubscription:user-1", "deleteUser:user-1"]);
    expect(deps.log.entries).toContainEqual({
      level: "info",
      message: "account deleted",
      data: { uid: "user-1", usageDocs: 3 },
    });
  });

  test("no user is unauthenticated", async () => {
    const { deps, calls } = setup();
    const error = await rejection(handleDeleteAccount({ data: null, auth: undefined }, deps));
    expect(error.code).toBe("unauthenticated");
    expect(reasonOf(error)).toBe("unauthenticated");
    expect(calls).toEqual([]);
  });

  test("a guest is refused with guest_account and nothing is deleted", async () => {
    const { deps, calls } = setup();
    const guest = {
      data: null,
      auth: { uid: "user-1", token: { firebase: { sign_in_provider: "anonymous", identities: {} } } },
    };
    const error = await rejection(handleDeleteAccount(guest, deps));
    expect(error.code).toBe("failed-precondition");
    expect(reasonOf(error)).toBe("guest_account");
    expect(calls).toEqual([]);
  });

  test("a failing auth delete is internal and logged", async () => {
    const { deps } = setup({ failDelete: new Error("auth is down") });
    const error = await rejection(handleDeleteAccount(google("user-1"), deps));
    expect(error.code).toBe("internal");
    expect(deps.log.entries).toContainEqual(expect.objectContaining({ level: "error" }));
  });

  test("a failed subscription delete fails the call before the account is deleted", async () => {
    const { deps, calls } = setup({ failSubscriptionDelete: new Error("firestore down") });
    const error = await rejection(handleDeleteAccount(google("user-1"), deps));
    expect(error.code).toBe("internal");
    expect(calls).toEqual(["deleteAll:user-1", "deleteSubscription:user-1"]);
  });
});
