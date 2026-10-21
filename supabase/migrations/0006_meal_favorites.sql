-- 0006: let a logged meal be starred as a favourite, so the dashboard can offer it for one-tap
-- logging again. Existing rows get false. Clients write this column directly through the
-- existing "own meals" policy; RLS is unchanged.

alter table fitcal.meals
  add column favorite boolean not null default false;
