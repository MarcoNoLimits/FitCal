// Run with: deno test supabase/functions/analyze-meal/allowance_test.ts
import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { dailyAllowance, isWeekOne, WEEK_ONE_MS } from "./allowance.ts";

Deno.test("guests get 3 scans in week one and after", () => {
  assertEquals(dailyAllowance(true, false), 3);
  assertEquals(dailyAllowance(false, false), 3);
});

Deno.test("permanent accounts get 6 in week one and 4 after", () => {
  assertEquals(dailyAllowance(true, true), 6);
  assertEquals(dailyAllowance(false, true), 4);
});

Deno.test("week one is the first seven days after install", () => {
  const now = Date.parse("2026-10-21T12:00:00Z");
  assertEquals(isWeekOne("2026-10-20T12:00:00Z", now), true);
  assertEquals(isWeekOne(new Date(now - WEEK_ONE_MS + 1).toISOString(), now), true);
  assertEquals(isWeekOne(new Date(now - WEEK_ONE_MS).toISOString(), now), false);
  assertEquals(isWeekOne(undefined, now), false);
  assertEquals(isWeekOne("not a date", now), false);
});
