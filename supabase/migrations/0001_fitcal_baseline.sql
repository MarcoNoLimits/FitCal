-- ─────────────────────────────────────────────────────────────────────────────
-- 0001 — FitCal baseline for its own Supabase project (uedtovphilwstzdpzjrk)
--
-- FitCal moved off the shared project (mpsqdaptkkasjoepamwl, also used by the CRM
-- and yinyang apps) on 2026-10-11. This single migration reproduces the final state
-- of the old migrations, kept for reference in supabase/legacy-shared-project/.
--
-- Everything lives in schema `fitcal`; Dashboard → Data API → Exposed schemas must
-- list it. Clients reach their own rows through RLS; analytics, feedback, rewards,
-- entitlements and account deletion go through the analyze-meal gateway only
-- (service_role RPCs). Analytics/feedback dashboards: schema fitcal_analytics.
-- ─────────────────────────────────────────────────────────────────────────────

create schema if not exists fitcal;
grant usage on schema fitcal to anon, authenticated, service_role;

-- ─── Tables ──────────────────────────────────────────────────────────────────

create table fitcal.user_meta (
  user_id            uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  first_install_date timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

create table fitcal.scan_quota (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  day     date not null default current_date,
  used    integer not null default 0,
  bonus   integer not null default 0,
  primary key (user_id, day)
);

create table fitcal.meals (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name       text not null,
  calories   integer not null,
  protein_g  real not null,
  carbs_g    real not null,
  fat_g      real not null,
  eaten_at   timestamptz not null default now(),
  photo_url  text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index meals_user_eaten_idx on fitcal.meals (user_id, eaten_at desc);

create table fitcal.profiles (
  user_id            uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  weight             real,
  height             real,
  cal_goal           integer,
  protein_goal       integer,
  carbs_goal         integer,
  fat_goal           integer,
  age                integer,
  gender             text,
  goal_type          text,
  default_plate_size real,
  updated_at         timestamptz not null default now()
);

create table fitcal.water_intake (
  user_id   uuid not null default auth.uid() references auth.users(id) on delete cascade,
  day       date not null,
  amount_ml integer not null default 0,
  primary key (user_id, day)
);

create table fitcal.entitlements (
  user_id        uuid not null references auth.users(id) on delete cascade,
  entitlement_id text not null,
  status         text not null check (status in ('active', 'expired')),
  updated_at     timestamptz not null default now(),
  primary key (user_id, entitlement_id)
);

create table fitcal.analytics_events (
  id          bigint generated always as identity primary key,
  user_id     uuid default auth.uid() references auth.users(id) on delete set null,
  event       text not null,
  payload     jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now(),
  session_id  text,
  app_version text,
  platform    text,
  client_ts   timestamptz,
  source      text not null default 'client'
);
create index analytics_events_user_idx on fitcal.analytics_events (user_id, created_at desc);
create index analytics_events_event_time_idx on fitcal.analytics_events (event, created_at desc);
create index analytics_events_time_idx on fitcal.analytics_events (created_at);

create table fitcal.feedback (
  id            bigint generated always as identity primary key,
  user_id       uuid references auth.users(id) on delete cascade,
  kind          text not null check (kind in ('scan_accuracy', 'bug', 'idea', 'other')),
  rating        smallint check (rating between 1 and 5),
  message       text check (length(message) <= 2000),
  contact_email text check (length(contact_email) <= 254),
  context       jsonb not null default '{}'::jsonb,
  app_version   text,
  platform      text,
  status        text not null default 'new' check (status in ('new', 'read', 'done', 'wontfix')),
  created_at    timestamptz not null default now()
);
create index feedback_created_idx on fitcal.feedback (created_at desc);
create index feedback_user_idx on fitcal.feedback (user_id, created_at desc);

-- ─── Row level security and grants ───────────────────────────────────────────
-- Own-row tables: full CRUD on your own rows. No TRUNCATE (it bypasses RLS).

alter table fitcal.user_meta        enable row level security;
alter table fitcal.scan_quota       enable row level security;
alter table fitcal.meals            enable row level security;
alter table fitcal.profiles         enable row level security;
alter table fitcal.water_intake     enable row level security;
alter table fitcal.entitlements     enable row level security;
alter table fitcal.analytics_events enable row level security;
alter table fitcal.feedback         enable row level security;

create policy "own user_meta" on fitcal.user_meta    for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own quota"     on fitcal.scan_quota   for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own meals"     on fitcal.meals        for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own profile"   on fitcal.profiles     for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own water"     on fitcal.water_intake for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "read own entitlements" on fitcal.entitlements for select using (user_id = auth.uid());
-- analytics_events and feedback: no client policies; only the gateway writes them.

revoke all on all tables in schema fitcal from public, anon, authenticated;
grant select, insert, update, delete on fitcal.user_meta, fitcal.scan_quota, fitcal.meals,
  fitcal.profiles, fitcal.water_intake to authenticated;
grant select on fitcal.entitlements to authenticated;
grant all on all tables in schema fitcal to service_role;
grant usage, select on all sequences in schema fitcal to service_role;

-- ─── Functions ───────────────────────────────────────────────────────────────

-- Quota read; a signed-in caller always gets their own row, whatever p_user_id says.
create or replace function fitcal.get_scan_quota(p_allowance integer default 3, p_user_id uuid default auth.uid())
returns jsonb
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_target_user_id uuid;
  v_result jsonb;
begin
  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitcal.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  select jsonb_build_object(
    'used',               coalesce(sq.used, 0),
    'bonus',              coalesce(sq.bonus, 0),
    'remaining',          greatest(0, (p_allowance + coalesce(sq.bonus, 0)) - coalesce(sq.used, 0)),
    'first_install_date', to_char(um.first_install_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  )
  into v_result
  from (select 1) dummy
  left join fitcal.scan_quota sq
    on sq.user_id = v_target_user_id and sq.day = current_date
  left join fitcal.user_meta um
    on um.user_id = v_target_user_id;

  return v_result;
end;
$$;

-- Counts one scan. The allowance is clamped at 6 (5 in week one + 1 account bonus).
create or replace function fitcal.consume_scan(p_user_id uuid default auth.uid(), p_allowance integer default 3)
returns boolean
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_target_user_id uuid;
  v_result boolean;
  v_effective_allowance int;
begin
  v_effective_allowance := least(greatest(coalesce(p_allowance, 0), 0), 6);

  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitcal.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  insert into fitcal.scan_quota (user_id, day, used)
  values (v_target_user_id, current_date, 1)
  on conflict (user_id, day) do update set used = fitcal.scan_quota.used + 1;

  select (sq.used <= v_effective_allowance + sq.bonus)
  into v_result
  from fitcal.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_result, true);
end;
$$;

create or replace function fitcal.grant_bonus_scan(p_amount integer default 1, p_user_id uuid default auth.uid())
returns integer
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_target_user_id uuid;
  v_new_bonus int;
  v_capped_amount int;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'grant_bonus_scan is restricted to service_role' using errcode = '42501';
  end if;

  v_capped_amount := least(greatest(coalesce(p_amount, 0), 0), 50);
  v_target_user_id := coalesce(p_user_id, auth.uid());

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitcal.scan_quota (user_id, day, bonus)
  values (v_target_user_id, current_date, v_capped_amount)
  on conflict (user_id, day)
  do update set bonus = fitcal.scan_quota.bonus + v_capped_amount;

  select sq.bonus into v_new_bonus
  from fitcal.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_new_bonus, v_capped_amount);
end;
$$;

-- Rewarded-ad bonus, capped per day. Returns null when the cap is already reached.
create or replace function fitcal.claim_reward_bonus(p_user_id uuid, p_amount integer, p_daily_cap integer)
returns integer
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_amount int;
  v_cap int;
  v_new_bonus int;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'claim_reward_bonus is restricted to service_role' using errcode = '42501';
  end if;
  if p_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  v_amount := least(greatest(coalesce(p_amount, 0), 0), 5);
  v_cap := least(greatest(coalesce(p_daily_cap, 0), 0), 50);
  if v_amount = 0 then
    return null;
  end if;

  -- The row lock taken by ON CONFLICT serialises concurrent claims for the same day.
  insert into fitcal.scan_quota (user_id, day, bonus)
  values (p_user_id, current_date, least(v_amount, v_cap))
  on conflict (user_id, day)
  do update set bonus = least(fitcal.scan_quota.bonus + v_amount, v_cap)
    where fitcal.scan_quota.bonus < v_cap
  returning bonus into v_new_bonus;

  return v_new_bonus;
end;
$$;

-- Erases all of a user's FitCal data. Returns true when the login can be deleted too
-- (always, now that this project only hosts FitCal, unless they own storage objects).
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
  -- analytics_events is ON DELETE SET NULL, so remove it explicitly.
  delete from fitcal.analytics_events where user_id = p_user_id;
  delete from fitcal.entitlements where user_id = p_user_id;
  delete from fitcal.feedback where user_id = p_user_id;
  delete from fitcal.profiles where user_id = p_user_id;
  delete from fitcal.user_meta where user_id = p_user_id;

  return not exists (select 1 from storage.objects where owner_id = p_user_id::text);
end;
$$;

-- Analytics ingestion (gateway only). Invalid entries are skipped, never fatal.
create or replace function fitcal.log_events(
  p_user_id     uuid,
  p_events      jsonb,
  p_app_version text default null,
  p_platform    text default null,
  p_source      text default 'client'
)
returns integer
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_inserted integer;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'log_events is restricted to service_role' using errcode = '42501';
  end if;
  if jsonb_typeof(p_events) is distinct from 'array' then
    return 0;
  end if;

  insert into fitcal.analytics_events
    (user_id, event, payload, session_id, app_version, platform, client_ts, source)
  select
    p_user_id,
    e->>'event',
    case when jsonb_typeof(e->'props') = 'object' and length((e->'props')::text) <= 4096
         then e->'props' else '{}'::jsonb end,
    left(e->>'session_id', 64),
    left(p_app_version, 32),
    left(p_platform, 16),
    -- Device clocks drift; keep the client time only when it is plausible.
    case when (e->>'client_ts') ~ '^[0-9]{12,14}$'
              and to_timestamp((e->>'client_ts')::bigint / 1000.0)
                  between now() - interval '30 days' and now() + interval '1 day'
         then to_timestamp((e->>'client_ts')::bigint / 1000.0) end,
    case when p_source = 'server' then 'server' else 'client' end
  from (select value as e from jsonb_array_elements(p_events) limit 200) batch
  where (e->>'event') ~ '^[a-z][a-z0-9_]{1,63}$';

  get diagnostics v_inserted = row_count;

  -- Opportunistic retention, about once per hundred calls.
  if random() < 0.01 then
    delete from fitcal.analytics_events
     where (event = 'diagnostic' and created_at < now() - interval '90 days')
        or created_at < now() - interval '400 days';
  end if;

  return v_inserted;
end;
$$;

-- Feedback (gateway only). Returns the new id, or null past 30 per user per day.
create or replace function fitcal.submit_feedback(
  p_user_id       uuid,
  p_kind          text,
  p_rating        smallint default null,
  p_message       text default null,
  p_contact_email text default null,
  p_context       jsonb default '{}'::jsonb,
  p_app_version   text default null,
  p_platform      text default null
)
returns bigint
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_id bigint;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'submit_feedback is restricted to service_role' using errcode = '42501';
  end if;

  if (select count(*) from fitcal.feedback
       where user_id = p_user_id and created_at > now() - interval '1 day') >= 30 then
    return null;
  end if;

  insert into fitcal.feedback (user_id, kind, rating, message, contact_email, context, app_version, platform)
  values (
    p_user_id,
    p_kind,
    p_rating,
    nullif(left(trim(p_message), 2000), ''),
    nullif(left(trim(p_contact_email), 254), ''),
    case when jsonb_typeof(p_context) = 'object' and length(p_context::text) <= 2048
         then p_context else '{}'::jsonb end,
    left(p_app_version, 32),
    left(p_platform, 16)
  )
  returning id into v_id;

  if random() < 0.02 then
    delete from fitcal.feedback where created_at < now() - interval '24 months';
  end if;

  return v_id;
end;
$$;

revoke all on all functions in schema fitcal from public, anon, authenticated;
grant execute on function fitcal.get_scan_quota(integer, uuid) to authenticated;
grant execute on function fitcal.consume_scan(uuid, integer) to authenticated;
grant execute on all functions in schema fitcal to service_role;

-- New objects in fitcal stay private unless granted explicitly.
alter default privileges for role postgres in schema fitcal revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema fitcal revoke execute on functions from public, anon, authenticated;

-- ─── Dashboards (SQL editor only; schema not exposed) ────────────────────────

create schema if not exists fitcal_analytics;
revoke all on schema fitcal_analytics from public, anon, authenticated;
grant usage on schema fitcal_analytics to service_role;

-- Event time: client time when trustworthy, else server receipt time.
create view fitcal_analytics.events as
select id, user_id, event, payload, session_id, app_version, platform, source,
       coalesce(client_ts, created_at) as ts, created_at
  from fitcal.analytics_events;

create view fitcal_analytics.daily_kpis as
with e as (select *, (ts at time zone 'utc')::date as day from fitcal_analytics.events),
first_seen as (select user_id, min(day) as day from e where user_id is not null group by user_id)
select
  e.day,
  count(distinct e.user_id)                                                   as active_users,
  (select count(*) from first_seen f where f.day = e.day)                     as new_users,
  count(*) filter (where event = 'scan_started')                              as scans_started,
  count(*) filter (where event = 'scan_succeeded')                            as scans_succeeded,
  count(*) filter (where event = 'scan_failed')                               as scans_failed,
  count(*) filter (where event = 'meal_logged')                               as meals_logged,
  count(*) filter (where event = 'meal_logged' and (payload->>'edited')::boolean) as meals_corrected,
  count(*) filter (where event = 'quota_exhausted')                           as quota_walls,
  count(*) filter (where event = 'ad_impression')                             as ad_impressions,
  round(sum((payload->>'revenue')::numeric) filter (where event = 'ad_revenue'), 4) as ad_revenue_usd,
  count(*) filter (where event = 'account_created')                           as accounts_created,
  count(*) filter (where event = 'diagnostic')                                as errors
from e
group by e.day
order by e.day desc;

create view fitcal_analytics.scan_funnel as
with e as (select *, (ts at time zone 'utc')::date as day from fitcal_analytics.events)
select
  day,
  count(distinct user_id) filter (where event = 'screen_view' and payload->>'screen' = 'camera') as opened_camera,
  count(distinct user_id) filter (where event = 'photo_captured')  as captured_photo,
  count(distinct user_id) filter (where event = 'scan_succeeded')  as got_result,
  count(distinct user_id) filter (where event = 'meal_logged')     as logged_meal,
  count(*) filter (where event = 'review_abandoned')               as reviews_abandoned,
  count(*) filter (where event = 'scan_cancelled')                 as scans_cancelled
from e
group by day
order by day desc;

create view fitcal_analytics.provider_health as
select
  (created_at at time zone 'utc')::date                                  as day,
  event,
  payload->>'provider'                                                   as provider,
  count(*)                                                               as calls,
  count(*) filter (where (payload->>'ok')::boolean)                      as ok,
  count(*) filter (where (payload->>'fallback')::boolean)                as used_fallback,
  percentile_cont(0.5)  within group (order by (payload->>'latency_ms')::numeric) as p50_ms,
  percentile_cont(0.95) within group (order by (payload->>'latency_ms')::numeric) as p95_ms
from fitcal.analytics_events
where source = 'server' and event in ('vlm_analyze', 'vlm_recalculate')
group by 1, 2, 3
order by 1 desc, 2, 3;

create view fitcal_analytics.ai_accuracy as
select
  (ts at time zone 'utc')::date                                     as day,
  count(*)                                                          as meals_logged,
  round(avg(((payload->>'edited')::boolean)::int) * 100, 1)         as pct_edited,
  round(avg((payload->>'items_removed')::numeric), 2)               as avg_items_removed,
  round(avg((payload->>'items_added')::numeric), 2)                 as avg_items_added,
  round(avg((payload->>'items_swapped')::numeric), 2)               as avg_items_swapped,
  round(avg((payload->>'weights_changed')::numeric), 2)             as avg_weights_changed,
  round(avg((payload->>'weight_change_pct')::numeric), 1)           as avg_weight_change_pct,
  round(avg((payload->>'review_seconds')::numeric), 1)              as avg_review_seconds
from fitcal_analytics.events
where event = 'meal_logged'
group by 1
order by 1 desc;

create view fitcal_analytics.retention as
with days as (
  select distinct user_id, (ts at time zone 'utc')::date as day
    from fitcal_analytics.events where user_id is not null
),
cohort as (select user_id, min(day) as first_day from days group by user_id)
select
  date_trunc('week', c.first_day)::date                                       as cohort_week,
  count(distinct c.user_id)                                                    as users,
  round(100.0 * count(distinct d.user_id) filter (where d.day = c.first_day + 1)  / count(distinct c.user_id), 1) as d1_pct,
  round(100.0 * count(distinct d.user_id) filter (where d.day = c.first_day + 7)  / count(distinct c.user_id), 1) as d7_pct,
  round(100.0 * count(distinct d.user_id) filter (where d.day = c.first_day + 30) / count(distinct c.user_id), 1) as d30_pct
from cohort c
left join days d on d.user_id = c.user_id
group by 1
order by 1 desc;

create view fitcal_analytics.top_errors as
select
  coalesce(payload->>'tag', event)                                as tag,
  left(coalesce(payload->>'message', payload->>'error', '?'), 160) as message,
  count(*)                                          as occurrences,
  count(distinct user_id)                           as users,
  max(created_at)                                   as last_seen
from fitcal.analytics_events
where event in ('diagnostic', 'scan_failed') and created_at > now() - interval '7 days'
group by 1, 2
order by occurrences desc
limit 50;

create view fitcal_analytics.feedback_inbox as
select id, created_at, kind, status, rating, message, contact_email, context, app_version, user_id
  from fitcal.feedback
 where kind <> 'scan_accuracy'
 order by created_at desc;

create view fitcal_analytics.scan_ratings as
with r as (
  select (created_at at time zone 'utc')::date as day, rating, context
    from fitcal.feedback where kind = 'scan_accuracy'
),
reasons as (
  select day, reason,
         row_number() over (partition by day order by count(*) desc) as rn
    from r, jsonb_array_elements_text(coalesce(r.context->'reasons', '[]'::jsonb)) as reason
   group by day, reason
)
select
  r.day,
  count(*)                                                         as ratings,
  count(*) filter (where rating >= 4)                              as thumbs_up,
  count(*) filter (where rating <= 2)                              as thumbs_down,
  round(100.0 * count(*) filter (where rating >= 4) / count(*), 1) as pct_positive,
  (select reason from reasons where reasons.day = r.day and rn = 1) as top_reason
from r
group by r.day
order by r.day desc;

revoke all on all tables in schema fitcal_analytics from public, anon, authenticated;
grant select on all tables in schema fitcal_analytics to service_role;

-- ─── Public brand assets (logo used in auth emails) ──────────────────────────
insert into storage.buckets (id, name, public)
values ('brand', 'brand', true)
on conflict (id) do update set public = true;
