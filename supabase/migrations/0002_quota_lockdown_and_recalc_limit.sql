-- 0002: close a quota bypass and rate-limit /v1/recalculate.
--
-- 1. Signed-in users (guests included) could INSERT/UPDATE/DELETE their own rows in
--    fitcal.scan_quota and fitcal.user_meta through the Data API: reset `used` to 0, raise
--    `bonus`, or move `first_install_date` forward to keep the week-one allowance forever.
--    The app only ever goes through the security-definer RPCs (get_scan_quota, consume_scan)
--    and the gateway, so clients keep SELECT on their own rows and lose every write.
-- 2. /v1/recalculate called the AI with no limit. fitcal.recalc_usage counts calls per user
--    per day; fitcal.consume_recalc (service_role only) increments and checks the cap.

-- ─── 1. Quota tables become read-only for clients ────────────────────────────

revoke insert, update, delete on fitcal.scan_quota, fitcal.user_meta from authenticated, anon;

drop policy if exists "own quota" on fitcal.scan_quota;
drop policy if exists "own user_meta" on fitcal.user_meta;
create policy "read own quota"     on fitcal.scan_quota for select using (user_id = auth.uid());
create policy "read own user_meta" on fitcal.user_meta  for select using (user_id = auth.uid());

-- ─── 2. Recalculate rate limit ───────────────────────────────────────────────

create table fitcal.recalc_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  day     date not null default current_date,
  used    integer not null default 0,
  primary key (user_id, day)
);

alter table fitcal.recalc_usage enable row level security;
-- No client policies: only the gateway (service_role) touches this table.
revoke all on fitcal.recalc_usage from public, anon, authenticated;
grant all on fitcal.recalc_usage to service_role;

-- Counts one recalculation and returns true while the user is within p_daily_cap for today.
create or replace function fitcal.consume_recalc(p_user_id uuid, p_daily_cap integer)
returns boolean
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_cap int;
  v_used int;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'consume_recalc is restricted to service_role' using errcode = '42501';
  end if;
  if p_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  v_cap := least(greatest(coalesce(p_daily_cap, 0), 0), 500);

  -- The row lock taken by ON CONFLICT serialises concurrent calls for the same day.
  insert into fitcal.recalc_usage (user_id, day, used)
  values (p_user_id, current_date, 1)
  on conflict (user_id, day) do update set used = fitcal.recalc_usage.used + 1
  returning used into v_used;

  return v_used <= v_cap;
end;
$$;

revoke all on function fitcal.consume_recalc(uuid, integer) from public, anon, authenticated;
grant execute on function fitcal.consume_recalc(uuid, integer) to service_role;

-- Account deletion also clears recalc_usage (same body as 0001 plus one line).
create or replace function fitcal.delete_account_data(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = fitcal, public
as $$
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'delete_account_data is restricted to service_role' using errcode = '42501';
  end if;
  if p_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  delete from fitcal.meals where user_id = p_user_id;
  delete from fitcal.water_intake where user_id = p_user_id;
  delete from fitcal.scan_quota where user_id = p_user_id;
  delete from fitcal.recalc_usage where user_id = p_user_id;
  -- analytics_events is ON DELETE SET NULL, so remove it explicitly.
  delete from fitcal.analytics_events where user_id = p_user_id;
  delete from fitcal.entitlements where user_id = p_user_id;
  delete from fitcal.feedback where user_id = p_user_id;
  delete from fitcal.profiles where user_id = p_user_id;
  delete from fitcal.user_meta where user_id = p_user_id;

  return not exists (select 1 from storage.objects where owner_id = p_user_id::text);
end;
$$;
