import { assert, assertEquals } from "jsr:@std/assert@1";
import { FoodIndex, groundItem, type ReferenceRow } from "./food_match.ts";

const rows: ReferenceRow[] = JSON.parse(
  await Deno.readTextFile(new URL("./reference_foods.json", import.meta.url)),
).foods;
const index = new FoodIndex(rows);

Deno.test("finds the USDA food for common descriptions", () => {
  const cases: [string, number][] = [
    ["Rice, white, long-grain, regular, enriched, cooked", 168878],
    ["Chicken, broilers or fryers, breast, meat only, cooked, roasted", 171477],
    ["Oil, olive, salad or cooking", 171413],
  ];
  for (const [desc, id] of cases) {
    const m = index.match(desc);
    assertEquals(m?.food.fdcId, id, desc);
    assert(m!.score > 0.9);
  }
});

Deno.test("a looser description still lands on the right food family", () => {
  const m = index.match("Chicken breast, roasted");
  assert(m !== null);
  assert(m.food.description.toLowerCase().startsWith("chicken"), m.food.description);
});

Deno.test("never grounds to a different main food", () => {
  const m = index.match("Salmon, Atlantic, farmed, cooked");
  assert(m === null || m.food.description.toLowerCase().startsWith("fish, salmon") || m.food.description.toLowerCase().includes("salmon"));
});

Deno.test("grounding scales per-100 g values to the grams; weak matches keep the AI values", () => {
  const base = { item: "Rice", weight_est_g: 200, calories: 300, protein_g: 5, carbs_g: 60, fat_g: 1 };
  const grounded = groundItem({ ...base, usda_description: "Rice, white, long-grain, regular, enriched, cooked" }, index);
  assertEquals(grounded.calories, 260);
  assertEquals(grounded.carbs_g, 56.4);
  assertEquals(grounded.grounded?.fdcId, 168878);
  const kept = groundItem({ ...base, usda_description: "Zzz unknown thing" }, index);
  assertEquals(kept.calories, 300);
  assertEquals(kept.grounded, undefined);
  assertEquals(groundItem(base, index).calories, 300);
});
