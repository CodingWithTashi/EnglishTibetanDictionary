import type { UserDeleter } from "../auth/userDeleter.js";
import type { QuotaStore } from "../store/quotaStore.js";
import type { Logger } from "../util/logger.js";
import { toHttpsError, TranslationError } from "./errors.js";
import { requireUser, type CallableLike } from "./translateHandler.js";

export interface AccountDeps {
  quota: Pick<QuotaStore, "deleteAll">;
  users: UserDeleter;
  log: Logger;
}

/**
 * Deletes the caller's usage docs, then their Auth user. Usage goes first so a failed user delete can be
 * retried and still leaves nothing behind. A guest has no account to delete: signing out discards it.
 */
export async function handleDeleteAccount(
  request: CallableLike,
  deps: AccountDeps,
): Promise<{ deleted: true }> {
  try {
    const user = requireUser(request);
    if (user.isGuest) {
      throw new TranslationError("failed-precondition", "guest_account", "Guests have no account to delete.");
    }
    const usageDocs = await deps.quota.deleteAll(user.uid);
    await deps.users.deleteUser(user.uid);
    deps.log.info("account deleted", { uid: user.uid, usageDocs });
    return { deleted: true };
  } catch (error) {
    throw toHttpsError(error, deps.log, { uid: request.auth?.uid });
  }
}
