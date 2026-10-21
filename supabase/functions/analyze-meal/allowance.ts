// Daily free-scan allowance (AGENTS.md 5.1, owner decision 2026-10-21).
// Pure so it can be unit-tested and kept in step with the client's
// ScanQuotaManager.getDailyFreeLimit(). The server stays authoritative.

export const BASE_DAILY_ALLOWANCE = 3;
export const WEEK_ONE_DAILY_ALLOWANCE = 5;
export const ACCOUNT_BONUS = 1;
export const WEEK_ONE_MS = 7 * 86400 * 1000;

/** True while the install is less than seven days old. Unknown or bad dates are not week one. */
export function isWeekOne(firstInstallDate: unknown, nowMs: number): boolean {
  if (typeof firstInstallDate !== "string") return false;
  const installMs = Date.parse(firstInstallDate);
  return !Number.isNaN(installMs) && nowMs - installMs < WEEK_ONE_MS;
}

/**
 * Guests (anonymous users) get the base allowance from day one, with no week-one bonus.
 * Permanent accounts get the week-one allowance for their first seven days, plus the account bonus.
 * Guests 3; accounts 5 + 1 in week one, 3 + 1 after.
 */
export function dailyAllowance(weekOne: boolean, permanent: boolean): number {
  if (!permanent) return BASE_DAILY_ALLOWANCE;
  return (weekOne ? WEEK_ONE_DAILY_ALLOWANCE : BASE_DAILY_ALLOWANCE) + ACCOUNT_BONUS;
}
