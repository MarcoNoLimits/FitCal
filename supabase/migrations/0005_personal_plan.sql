-- 0005: optional personal plan (Settings → Personal plan).
--   activity_level: scales maintenance calories.
--   targets_mode:   where daily targets come from: 'none' (no targets), 'plan' or 'custom'.
--   plan:           {pace, targetWeightKg, startDate, startWeightKg, lastCheckInDate,
--                    customGoalsBeforePlan} while targets_mode = 'plan'.
-- All nullable: profiles saved by older app builds have none of these, and the app infers
-- 'none' or 'custom' from the stored goals. Clients write these directly (RLS "own profile"),
-- so values and size are bounded here.

alter table fitcal.profiles
  add column activity_level text
    constraint profiles_activity_level_check
    check (activity_level in ('Sedentary', 'Light', 'Moderate', 'Very Active')),
  add column targets_mode text
    constraint profiles_targets_mode_check
    check (targets_mode in ('none', 'plan', 'custom')),
  add column plan jsonb
    constraint profiles_plan_shape
    check (plan is null or (jsonb_typeof(plan) = 'object' and octet_length(plan::text) <= 4096));
