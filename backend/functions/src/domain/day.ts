import { DateTime, IANAZone } from "luxon";

/** The quota day a request falls in, in the client's time zone. */
export interface DayWindow {
  /** The IANA zone used: the client's, or `UTC` when it sent none or an unknown one. */
  zone: string;
  /** Local date, `YYYY-MM-DD`. */
  dayKey: string;
  /** The next local midnight, when the daily limit resets. */
  resetsAt: Date;
}

export function dayWindow(timeZone: string | undefined, now: Date): DayWindow {
  const zone = timeZone && IANAZone.isValidZone(timeZone) ? timeZone : "UTC";
  const local = DateTime.fromJSDate(now, { zone });
  const dayKey = local.toISODate();
  if (dayKey === null) throw new Error(`Cannot compute a date in zone ${zone}`);
  return { zone, dayKey, resetsAt: local.startOf("day").plus({ days: 1 }).toJSDate() };
}
