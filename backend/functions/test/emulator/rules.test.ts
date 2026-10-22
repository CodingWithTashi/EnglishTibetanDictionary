import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { addDoc, collection, deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, describe, test } from "vitest";

import { PROJECT_ID } from "./helpers.js";

let env: RulesTestEnvironment;

beforeAll(async () => {
  const [host = "127.0.0.1", port = "8080"] = (process.env.FIRESTORE_EMULATOR_HOST ?? "").split(":");
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(port),
      rules: readFileSync(resolve(import.meta.dirname, "../../../firestore.rules"), "utf8"),
    },
  });
});

afterAll(async () => {
  await env.cleanup();
});

const signedOut = () => env.unauthenticatedContext().firestore();
const signedIn = () => env.authenticatedContext("user-1").firestore();

describe("legacy feedback (Message), written by app versions before 3.0", () => {
  test("anyone can send feedback", async () => {
    await assertSucceeds(addDoc(collection(signedOut(), "Message"), { message: "great app" }));
    await assertSucceeds(addDoc(collection(signedIn(), "Message"), { message: "great app" }));
  });

  test.each([
    ["too short", { message: "hi!" }],
    ["too long", { message: "x".repeat(5_001) }],
    ["not a string", { message: 12345 }],
    ["extra fields", { message: "great app", extra: 1 }],
    ["no message", {}],
  ])("rejects feedback that is %s", async (_name, data) => {
    await assertFails(addDoc(collection(signedOut(), "Message"), data));
  });

  test("feedback cannot be read, changed or deleted by clients", async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "Message/m1"), { message: "existing" });
    });
    await assertFails(getDoc(doc(signedOut(), "Message/m1")));
    await assertFails(updateDoc(doc(signedOut(), "Message/m1"), { message: "changed text" }));
    await assertFails(deleteDoc(doc(signedOut(), "Message/m1")));
  });
});

describe("server-only data", () => {
  test.each([
    "translations/k1",
    "dailyUsage/user-1_2026-10-22",
    "budget/2026-10-22/shards/0",
    "config/translation",
  ])("%s is closed to every client", async (path) => {
    for (const db of [signedOut(), signedIn()]) {
      await assertFails(getDoc(doc(db, path)));
      await assertFails(setDoc(doc(db, path), { count: 0 }));
    }
  });
});
