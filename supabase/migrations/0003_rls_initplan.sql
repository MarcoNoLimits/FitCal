-- 0003: wrap auth.uid() in RLS policies as (select auth.uid()) so Postgres evaluates it once
-- per query instead of once per row (Supabase advisor 0003_auth_rls_initplan).
-- Same rows are visible and writable as before; only the query plan changes.

alter policy "own meals"             on fitcal.meals        using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
alter policy "own profile"           on fitcal.profiles     using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
alter policy "own water"             on fitcal.water_intake using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
alter policy "read own entitlements" on fitcal.entitlements using (user_id = (select auth.uid()));
alter policy "read own quota"        on fitcal.scan_quota   using (user_id = (select auth.uid()));
alter policy "read own user_meta"    on fitcal.user_meta    using (user_id = (select auth.uid()));
