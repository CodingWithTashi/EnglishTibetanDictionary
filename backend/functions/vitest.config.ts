import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["test/unit/**/*.test.ts"],
    environment: "node",
    // `npm run coverage`. The Firestore stores are measured by the emulator run instead. src/index.ts counts
    // here, but only the emulator tests (test/emulator/callables.test.ts) call through it.
    coverage: {
      provider: "v8",
      include: ["src/**/*.ts"],
      exclude: ["src/store/**"],
      reportsDirectory: "coverage/unit",
      reporter: ["text", "html", "json-summary"],
      // What the suite reached on 2026-10-22, rounded down. Raise them as coverage grows; never lower them.
      thresholds: { lines: 95, branches: 94 },
    },
  },
});
