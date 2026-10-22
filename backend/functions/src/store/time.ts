export const DAY_MS = 86_400_000;

export function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * DAY_MS);
}

/** `YYYY-MM-DD` of the UTC day. */
export function utcDayKey(date: Date): string {
  return date.toISOString().slice(0, 10);
}
