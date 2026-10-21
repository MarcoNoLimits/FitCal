-- 0007: weight history for the Progress screen.
--   One weigh-in per user and day; a second one on the same day replaces the first
--   (the app upserts on the primary key). Written on every plan check-in and whenever the
--   weight changes in Settings. Clients write their own rows directly (RLS "own weight").

create table fitcal.weight_entries (
  user_id    uuid not null default auth.uid() references auth.users(id) on delete cascade,
  day        date not null,
  weight_kg  real not null
    constraint weight_entries_weight_check check (weight_kg between 20 and 400),
  updated_at timestamptz not null default now(),
  primary key (user_id, day)
);

alter table fitcal.weight_entries enable row level security;

create policy "own weight" on fitcal.weight_entries for all
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

grant select, insert, update, delete on fitcal.weight_entries to authenticated;
grant all on fitcal.weight_entries to service_role;

-- Account deletion also clears weight_entries (same body as 0002 plus one line).
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
  delete from fitcal.weight_entries where user_id = p_user_id;
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
