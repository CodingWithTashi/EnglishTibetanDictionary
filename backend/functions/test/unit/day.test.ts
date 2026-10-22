import { describe, expect, test } from "vitest";

import { dayWindow } from "../../src/domain/day.js";

describe("dayWindow", () => {
  test("uses the client's zone for the day and the next local midnight", () => {
    const w = dayWindow("Asia/Thimphu", new Date("2026-10-22T19:00:00Z"));
    expect(w).toEqual({
      zone: "Asia/Thimphu",
      dayKey: "2026-10-23",
      resetsAt: new Date("2026-10-23T18:00:00Z"),
    });
  });

  test("falls back to UTC for an unknown or missing zone", () => {
    const now = new Date("2026-10-22T23:30:00Z");
    for (const zone of ["Not/AZone", undefined, "", "../../etc"]) {
      expect(dayWindow(zone, now)).toEqual({
        zone: "UTC",
        dayKey: "2026-10-22",
        resetsAt: new Date("2026-10-23T00:00:00Z"),
      });
    }
  });

  test("handles the day DST ends", () => {
    const w = dayWindow("America/Toronto", new Date("2026-11-01T12:00:00Z"));
    expect(w.dayKey).toBe("2026-11-01");
    expect(w.resetsAt).toEqual(new Date("2026-11-02T05:00:00Z"));
  });

  test("accepts fixed-offset Etc zones", () => {
    const w = dayWindow("Etc/GMT+12", new Date("2026-10-22T11:00:00Z"));
    expect(w.zone).toBe("Etc/GMT+12");
    expect(w.dayKey).toBe("2026-10-21");
    expect(w.resetsAt).toEqual(new Date("2026-10-22T12:00:00Z"));
  });
});
