-- ─────────────────────────────────────────────────────────────────────────────
-- 0008 — AdMob server-side verification (SSV) claims and abandoned-guest cleanup
--
-- 1. fitcal.reward_ssv_claims + fitcal.claim_ssv_reward: the gateway's GET /v1/reward/ssv
--    route records every verified AdMob callback by transaction_id and grants the rewarded
--    bonus through the existing fitcal.claim_reward_bonus, so the +2 per ad and the daily cap
--    are unchanged. A repeated transaction_id (AdMob retries, replays) never grants twice.
--    fitcal.recent_ssv_reward lets POST /v1/reward/ad-earned confirm an SSV grant to the app
--    once REWARD_REQUIRE_SSV is on.
-- 2. pg_cron job fitcal-cleanup-anonymous-users (daily, 03:17 UTC): deletes anonymous auth
--    users older than 30 days that never saved a meal, sent an event, scanned in the last
--    30 days, left feedback or own storage objects. It also purges SSV claims older than
--    30 days (AdMob retries arrive within hours, so dedup only needs a short window).
--
-- Not applied automatically: the owner applies it (Dashboard → Database → Extensions must
-- allow pg_cron; `create extension` below enables it on Supabase).
-- ─────────────────────────────────────────────────────────────────────────────

-- ─── 1. SSV claims ───────────────────────────────────────────────────────────

create table fitcal.reward_ssv_claims (
  transaction_id text primary key check (length(transaction_id) between 1 and 128),
  user_id        uuid not null references auth.users(id) on delete cascade,
  status         text not null check (status in ('granted', 'capped', 'recorded')),
  granted        integer not null default 0,
  bonus_after    integer,
  ad_unit        text check (length(ad_unit) <= 128),
  reward_item    text check (length(reward_item) <= 128),
  reward_amount  integer,
  created_at     timestamptz not null default now()
);
create index reward_ssv_claims_user_time_idx on fitcal.reward_ssv_claims (user_id, created_at desc);
create index reward_ssv_claims_time_idx on fitcal.reward_ssv_claims (created_at);

alter table fitcal.reward_ssv_claims enable row level security;
-- No client policies: only the gateway (service_role) touches this table.
revoke all on fitcal.reward_ssv_claims from public, anon, authenticated;
grant all on fitcal.reward_ssv_claims to service_role;

-- Records one verified AdMob SSV callback and, when p_grant is true, grants the rewarded
-- bonus. Idempotent per transaction_id: the primary key makes a second call a no-op that
-- reports 'duplicate' with the original outcome.
-- Returns jsonb { status: granted|capped|recorded|duplicate|unknown_user, granted, bonus }.
create or replace function fitcal.claim_ssv_reward(
  p_transaction_id text,
  p_user_id        uuid,
  p_amount         integer,
  p_daily_cap      integer,
  p_grant          boolean default true,
  p_ad_unit        text default null,
  p_reward_item    text default null,
  p_reward_amount  integer default null
)
returns jsonb
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_inserted text;
  v_existing fitcal.reward_ssv_claims%rowtype;
  v_bonus int;
  v_status text;
  v_granted int := 0;
  v_before int;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'claim_ssv_reward is restricted to service_role' using errcode = '42501';
  end if;
  if p_transaction_id is null or length(p_transaction_id) = 0 or length(p_transaction_id) > 128 then
    raise exception 'transaction_id must be 1..128 characters';
  end if;
  if p_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  if not exists (select 1 from auth.users u where u.id = p_user_id) then
    return jsonb_build_object('status', 'unknown_user', 'granted', 0, 'bonus', null);
  end if;

  -- Claim the transaction first. Concurrent callbacks with the same id serialise on the
  -- primary key; only the one that inserted goes on to grant.
  insert into fitcal.reward_ssv_claims (transaction_id, user_id, status, ad_unit, reward_item, reward_amount)
  values (p_transaction_id, p_user_id, 'recorded', left(p_ad_unit, 128), left(p_reward_item, 128), p_reward_amount)
  on conflict (transaction_id) do nothing
  returning transaction_id into v_inserted;

  if v_inserted is null then
    select * into v_existing from fitcal.reward_ssv_claims where transaction_id = p_transaction_id;
    return jsonb_build_object(
      'status', 'duplicate',
      'original_status', v_existing.status,
      'granted', 0,
      'bonus', v_existing.bonus_after
    );
  end if;

  if p_grant then
    select sq.bonus into v_before from fitcal.scan_quota sq
    where sq.user_id = p_user_id and sq.day = current_date;
    -- Same rule as POST /v1/reward/ad-earned: null means the daily cap was already reached.
    v_bonus := fitcal.claim_reward_bonus(p_user_id, p_amount, p_daily_cap);
    if v_bonus is null then
      v_status := 'capped';
      v_bonus := v_before;
    else
      v_status := 'granted';
      v_granted := greatest(v_bonus - coalesce(v_before, 0), 0);
    end if;
  else
    v_status := 'recorded';
    select sq.bonus into v_bonus from fitcal.scan_quota sq
    where sq.user_id = p_user_id and sq.day = current_date;
  end if;

  update fitcal.reward_ssv_claims
  set status = v_status, granted = v_granted, bonus_after = v_bonus
  where transaction_id = p_transaction_id;

  return jsonb_build_object('status', v_status, 'granted', v_granted, 'bonus', v_bonus);
end;
$$;

-- The newest SSV grant for a user within the last p_within_seconds, plus today's bonus.
-- Used by POST /v1/reward/ad-earned to confirm a reward to the app when SSV is required.
-- Returns jsonb { found, granted, bonus }.
create or replace function fitcal.recent_ssv_reward(p_user_id uuid, p_within_seconds integer default 120)
returns jsonb
language plpgsql
stable
security definer
set search_path = fitcal, public
as $$
declare
  v_claim fitcal.reward_ssv_claims%rowtype;
  v_bonus int;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'recent_ssv_reward is restricted to service_role' using errcode = '42501';
  end if;

  select * into v_claim from fitcal.reward_ssv_claims c
  where c.user_id = p_user_id
    and c.status in ('granted', 'capped')
    and c.created_at > now() - make_interval(secs => least(greatest(coalesce(p_within_seconds, 120), 1), 600))
  order by c.created_at desc
  limit 1;

  select sq.bonus into v_bonus from fitcal.scan_quota sq
  where sq.user_id = p_user_id and sq.day = current_date;

  return jsonb_build_object(
    'found', v_claim.transaction_id is not null,
    'granted', coalesce(v_claim.granted, 0),
    'bonus', coalesce(v_bonus, 0)
  );
end;
$$;

revoke all on function fitcal.claim_ssv_reward(text, uuid, integer, integer, boolean, text, text, integer) from public, anon, authenticated;
revoke all on function fitcal.recent_ssv_reward(uuid, integer) from public, anon, authenticated;
grant execute on function fitcal.claim_ssv_reward(text, uuid, integer, integer, boolean, text, text, integer) to service_role;
grant execute on function fitcal.recent_ssv_reward(uuid, integer) to service_role;

-- ─── 2. Abandoned-guest cleanup ──────────────────────────────────────────────

-- Deletes up to p_limit abandoned anonymous users and returns how many were deleted.
-- Cascades remove their scan_quota, user_meta, recalc_usage and other own-row data.
create or replace function fitcal.cleanup_anonymous_users(p_older_than interval default interval '30 days', p_limit integer default 5000)
returns integer
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_deleted int;
begin
  -- Execute is granted to service_role only; the cron job runs as postgres (the owner).
  with doomed as (
    select u.id
    from auth.users u
    where u.is_anonymous
      and u.created_at < now() - p_older_than
      and coalesce(u.last_sign_in_at, u.created_at) < now() - p_older_than
      and not exists (select 1 from fitcal.meals m where m.user_id = u.id)
      and not exists (select 1 from fitcal.analytics_events e where e.user_id = u.id)
      and not exists (select 1 from fitcal.feedback f where f.user_id = u.id)
      and not exists (
        select 1 from fitcal.scan_quota q
        where q.user_id = u.id and q.day >= (now() - p_older_than)::date
      )
      and not exists (select 1 from storage.objects o where o.owner_id = u.id::text)
    order by u.created_at
    limit greatest(coalesce(p_limit, 0), 0)
  )
  delete from auth.users u using doomed d where u.id = d.id;
  get diagnostics v_deleted = row_count;

  -- SSV dedup only needs to outlast AdMob's retries.
  delete from fitcal.reward_ssv_claims where created_at < now() - interval '30 days';

  return v_deleted;
end;
$$;

revoke all on function fitcal.cleanup_anonymous_users(interval, integer) from public, anon, authenticated;
grant execute on function fitcal.cleanup_anonymous_users(interval, integer) to service_role;

create extension if not exists pg_cron;

-- Re-running the migration replaces the job instead of adding a second one.
select cron.unschedule(jobid) from cron.job where jobname = 'fitcal-cleanup-anonymous-users';
select cron.schedule(
  'fitcal-cleanup-anonymous-users',
  '17 3 * * *',
  $$select fitcal.cleanup_anonymous_users();$$
);
