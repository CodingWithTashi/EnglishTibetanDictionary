import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp, type Firestore } from "firebase-admin/firestore";

export const PROJECT_ID = "demo-translation";

function emulatorHost(): string {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  if (!host) throw new Error("FIRESTORE_EMULATOR_HOST is not set: run through `npm run test:emulator`");
  return host;
}

/** Admin Firestore pointed at the emulator (the Admin SDK reads FIRESTORE_EMULATOR_HOST). */
export function testDb(): Firestore {
  emulatorHost();
  return getFirestore(getApps()[0] ?? initializeApp({ projectId: PROJECT_ID }));
}

export async function clearFirestore(): Promise<void> {
  const response = await fetch(
    `http://${emulatorHost()}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" },
  );
  if (!response.ok) throw new Error(`clearing the emulator failed: HTTP ${response.status}`);
}

export const DAY_MS = 86_400_000;

/** A Timestamp field of a document, as a Date. */
export async function dateAt(db: Firestore, path: string, field: string): Promise<Date> {
  const value: unknown = (await db.doc(path).get()).get(field);
  if (!(value instanceof Timestamp)) throw new Error(`${path}.${field} is not a Timestamp`);
  return value.toDate();
}
