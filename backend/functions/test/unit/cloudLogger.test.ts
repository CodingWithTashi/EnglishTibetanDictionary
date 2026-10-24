import * as functionsLogger from "firebase-functions/logger";
import { beforeEach, describe, expect, test, vi } from "vitest";

import { cloudLogger } from "../../src/util/cloudLogger.js";

vi.mock("firebase-functions/logger", () => ({ info: vi.fn(), warn: vi.fn(), error: vi.fn() }));

const LEVELS = ["info", "warn", "error"] as const;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("cloudLogger", () => {
  test.each(LEVELS)(
    "%s goes to the Functions logger at that level, with the fields as the payload",
    (level) => {
      cloudLogger[level]("translate", { uid: "user-1", outcome: "cached", chars: 7 });
      expect(vi.mocked(functionsLogger[level])).toHaveBeenCalledExactlyOnceWith("translate", {
        uid: "user-1",
        outcome: "cached",
        chars: 7,
      });
      for (const other of LEVELS.filter((l) => l !== level)) {
        expect(vi.mocked(functionsLogger[other])).not.toHaveBeenCalled();
      }
    },
  );

  test.each(LEVELS)("%s without fields sends an empty payload", (level) => {
    cloudLogger[level]("config: read failed");
    expect(vi.mocked(functionsLogger[level])).toHaveBeenCalledExactlyOnceWith("config: read failed", {});
  });
});
