export type LogLevel = "info" | "warn" | "error";

/** Structured logging. Callers pass ids, counts and outcomes, never user text. */
export interface Logger {
  info(message: string, data?: Record<string, unknown>): void;
  warn(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
