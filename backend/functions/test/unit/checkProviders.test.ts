import { describe, expect, test } from "vitest";

import { checkProviders, formatReport, suggestRoutes } from "../../scripts/check-providers-lib.js";
import { failWith, fakeProvider } from "./fakes.js";

describe("checkProviders", () => {
  test("tries every provider on every direction and reports what happened", async () => {
    const azure = fakeProvider("azure", (input) =>
      input.from === "dz" || input.to === "dz"
        ? failWith("azure", "unsupported_language")()
        : Promise.resolve("ok"),
    );
    const google = fakeProvider("google", ok(), { configured: false });
    const rows = await checkProviders({ azure, google });

    expect(rows).toContainEqual({ provider: "azure", pair: "bo-en", status: "ok", output: "ok" });
    expect(rows).toContainEqual({ provider: "azure", pair: "dz-en", status: "unsupported_language" });
    expect(rows).toContainEqual({ provider: "google", pair: "bo-en", status: "not_configured" });
    expect(rows).toHaveLength(8);
    expect(google.calls).toHaveLength(0);
    expect(azure.calls.map((c) => `${c.from}-${c.to}`).sort()).toEqual(["bo-en", "dz-en", "en-bo", "en-dz"]);
  });

  test("suggests routes from the providers that answered, Azure first for Tibetan, Google first for Dzongkha", () => {
    const rows = [
      { provider: "azure", pair: "bo-en", status: "ok", output: "a" },
      { provider: "google", pair: "bo-en", status: "ok", output: "b" },
      { provider: "azure", pair: "dz-en", status: "unsupported_language" },
      { provider: "google", pair: "dz-en", status: "ok", output: "c" },
    ] as const;
    expect(suggestRoutes([...rows])).toEqual({ "bo-en": ["azure", "google"], "dz-en": ["google"] });
  });

  test("the report lists each row", () => {
    const report = formatReport([
      { provider: "azure", pair: "bo-en", status: "ok", output: "Tashi Delek" },
      { provider: "google", pair: "bo-en", status: "auth" },
    ]);
    expect(report).toContain("azure");
    expect(report).toContain("Tashi Delek");
    expect(report).toContain("auth");
  });
});

function ok() {
  return () => Promise.resolve("ok");
}
