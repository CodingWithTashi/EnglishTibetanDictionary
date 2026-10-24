import type { Auth } from "firebase-admin/auth";

export interface UserDeleter {
  /** Deletes the Firebase Auth user; one that is already gone counts as deleted. */
  deleteUser(uid: string): Promise<void>;
}

function codeOf(error: unknown): unknown {
  return typeof error === "object" && error !== null ? (error as { code?: unknown }).code : undefined;
}

export function createUserDeleter(auth: Pick<Auth, "deleteUser">): UserDeleter {
  return {
    async deleteUser(uid) {
      try {
        await auth.deleteUser(uid);
      } catch (error) {
        if (codeOf(error) !== "auth/user-not-found") throw error;
      }
    },
  };
}
