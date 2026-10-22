import { defineConfig } from "vitest/config";

// Runs inside `firebase emulators:exec`, which sets FIRESTORE_EMULATOR_HOST. The tests share one emulator and
// clear it before each test, so files run one after another.
export default defineConfig({
  test: {
    include: ["test/emulator/**/*.test.ts"],
    setupFiles: ["test/emulator/setup.ts"],
    environment: "node",
    fileParallelism: false,
    testTimeout: 20_000,
  },
});
