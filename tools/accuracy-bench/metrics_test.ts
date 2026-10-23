import { assertAlmostEquals, assertEquals } from "jsr:@std/assert@1";
import { type DishResult, parseDishMetadata, sampleDishes, summarize } from "./metrics.ts";

Deno.test("parses dish rows and drops tiny dishes", () => {
  const csv = [
    "dish_1,500.0,300.0,20.0,50.0,30.0,2,ingr_1,rice,100,130,0.3,28,2.7",
    "dish_2,10.0,50.0,0,2,0",          // under 20 kcal
    "not_a_dish,1,2,3,4,5",
  ].join("\n");
  const dishes = parseDishMetadata(csv);
  assertEquals(dishes.map((d) => d.id), ["dish_1"]);
  assertEquals(dishes[0].protein, 30);
});

Deno.test("seeded sampling is repeatable", () => {
  const xs = Array.from({ length: 50 }, (_, i) => i);
  assertEquals(sampleDishes(xs, 5, 7), sampleDishes(xs, 5, 7));
});

Deno.test("metrics: error, bias and within-20%", () => {
  const ref = { id: "d", calories: 500, mass: 300, fat: 20, carbs: 50, protein: 30 };
  const results: DishResult[] = [
    { id: "a", reference: ref, estimate: { ...ref, calories: 400 }, items: [], latencyMs: 0 },  // -20%
    { id: "b", reference: ref, estimate: { ...ref, calories: 450 }, items: [], latencyMs: 0 },  // -10%
  ];
  const m = summarize(results).metrics.calories;
  assertAlmostEquals(m.mae, 75);
  assertAlmostEquals(m.bias, -15);
  assertAlmostEquals(m.within20, 100);
});
