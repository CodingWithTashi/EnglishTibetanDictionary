import { describe, expect, test, vi } from "vitest";

import { createUserDeleter } from "../../src/auth/userDeleter.js";

describe("createUserDeleter", () => {
  test("deletes the user", async () => {
    const deleteUser = vi.fn(() => Promise.resolve());
    await createUserDeleter({ deleteUser }).deleteUser("user-1");
    expect(deleteUser).toHaveBeenCalledWith("user-1");
  });

  test("a user that is already gone counts as deleted", async () => {
    const deleteUser = vi.fn(() =>
      Promise.reject(Object.assign(new Error("gone"), { code: "auth/user-not-found" })),
    );
    await expect(createUserDeleter({ deleteUser }).deleteUser("user-1")).resolves.toBeUndefined();
  });

  test("any other error rejects", async () => {
    const deleteUser = vi.fn(() =>
      Promise.reject(Object.assign(new Error("down"), { code: "auth/internal-error" })),
    );
    await expect(createUserDeleter({ deleteUser }).deleteUser("user-1")).rejects.toThrow("down");
  });

  test("a rejection that is not an error object rejects too", async () => {
    const deleteUser = vi.fn<(uid: string) => Promise<void>>().mockRejectedValue("down");
    await expect(createUserDeleter({ deleteUser }).deleteUser("user-1")).rejects.toBe("down");
  });
});
