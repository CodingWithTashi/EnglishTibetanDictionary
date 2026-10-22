import * as functionsLogger from "firebase-functions/logger";

import type { Logger } from "./logger.js";

/** Structured Cloud Logging entries (jsonPayload) through the Functions logger. */
export const cloudLogger: Logger = {
  info: (message, data) => {
    functionsLogger.info(message, data ?? {});
  },
  warn: (message, data) => {
    functionsLogger.warn(message, data ?? {});
  },
  error: (message, data) => {
    functionsLogger.error(message, data ?? {});
  },
};
