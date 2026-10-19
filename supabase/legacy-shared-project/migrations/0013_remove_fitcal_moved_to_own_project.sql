-- FitCal moved to its own Supabase project (uedtovphilwstzdpzjrk) on 2026-10-11.
-- Removes its test guests and schemas from this shared project. fitcal/fitter were
-- removed from Exposed schemas first, so PostgREST is unaffected.
delete from auth.users where is_anonymous;
drop schema if exists fitcal_analytics cascade;
drop schema if exists fitcal cascade;
drop schema if exists fitter cascade;
notify pgrst, 'reload schema';
