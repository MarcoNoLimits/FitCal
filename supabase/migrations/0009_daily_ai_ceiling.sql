-- ─────────────────────────────────────────────────────────────────────────────
-- 0009 — Project-wide daily ceiling on paid AI calls
--
-- Per-user quotas stop one user from overspending, but not many users at once (for example
-- farmed guest accounts). fitcal.claim_ai_call counts every paid AI call the gateway makes
-- today (scans, text scans, recalculations, premium included) and returns false once the
-- count passes p_ceiling; the gateway then answers 503 without calling a provider.
-- The counter is per UTC day, like fitcal.scan_quota.
-- ─────────────────────────────────────────────────────────────────────────────

create table fitcal.daily_ai_calls (
  day   date primary key,
  calls integer not null default 0
);

alter table fitcal.daily_ai_calls enable row level security;
-- No client policies: only the gateway (service_role) touches this table.
revoke all on fitcal.daily_ai_calls from public, anon, authenticated;
grant all on fitcal.daily_ai_calls to service_role;

-- Counts one AI call and returns true while today's count is within p_ceiling.
-- Calls refused past the ceiling are counted too, so the dashboard shows the real demand.
create or replace function fitcal.claim_ai_call(p_ceiling integer)
returns boolean
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_calls int;
begin
  insert into fitcal.daily_ai_calls (day, calls)
  values (current_date, 1)
  on conflict (day) do update set calls = fitcal.daily_ai_calls.calls + 1
  returning calls into v_calls;

  return v_calls <= greatest(coalesce(p_ceiling, 0), 0);
end;
$$;

revoke all on function fitcal.claim_ai_call(integer) from public, anon, authenticated;
grant execute on function fitcal.claim_ai_call(integer) to service_role;
