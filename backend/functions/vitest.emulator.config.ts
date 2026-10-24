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
    // `npm run coverage:emulator`. Only the Firestore stores: everything else is measured by the unit run.
    coverage: {
      provider: "v8",
      include: ["src/store/**/*.ts"],
      reportsDirectory: "coverage/emulator",
      reporter: ["text", "html", "json-summary"],
      // What the suite reached on 2026-10-22, rounded down. Raise them as coverage grows; never lower them.
      thresholds: { lines: 98, branches: 92 },
    },
  },
});
