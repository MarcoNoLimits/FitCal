-- 0004: store each logged meal's ingredient list so a meal can be reopened from the dashboard
-- and corrected later. Array of {item, weight_est_g, calories, protein_g, carbs_g, fat_g, confidence}.
-- Existing rows get '[]'; the app edits those as a single portion.
-- Clients write this column directly (RLS "own meals"), so its shape and size are bounded here.

alter table fitcal.meals
  add column items jsonb not null default '[]'::jsonb;

alter table fitcal.meals
  add constraint meals_items_shape check (
    jsonb_typeof(items) = 'array'
    and jsonb_array_length(items) <= 50
    and octet_length(items::text) <= 32768
  );
