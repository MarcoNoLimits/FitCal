/**
 * FitCal accuracy benchmark: runs the production photo prompt on Nutrition5k dishes and
 * compares the estimate with lab-weighed reference values.
 *
 * Nutrition5k (Google Research, CC BY 4.0): ~5,000 cafeteria dishes, every ingredient weighed,
 * with an overhead photo. https://github.com/google-research-datasets/Nutrition5k
 *
 * Run (Deno, from the repo root):
 *   GEMINI_API_KEY=... deno run --allow-net --allow-env --allow-read --allow-write \
 *     tools/accuracy-bench/bench.ts --n 100 --seed 1
 *
 * Downloads only the sampled dishes' photos (cached in tools/accuracy-bench/.cache).
 * Cost: one Gemini Flash call per dish, roughly $0.03 per 100 dishes.
 * Writes tools/accuracy-bench/results/<timestamp>.json and prints a summary table.
 */

import { buildPhotoPrompt, callGemini, GROUNDED_SYSTEM_PROMPT, type NutritionResponse, SYSTEM_PROMPT } from "../../supabase/functions/analyze-meal/vlm.ts";
import { FoodIndex, groundItem, type ReferenceRow } from "../../supabase/functions/analyze-meal/food_match.ts";
import { encodeBase64 } from "jsr:@std/encoding@1/base64";
import { parseArgs } from "jsr:@std/cli@1/parse-args";
import { summarize, type DishResult, parseDishMetadata, type ReferenceDish, sampleDishes } from "./metrics.ts";

const BASE = "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset";
const HERE = new URL(".", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const CACHE = `${HERE}.cache`;

const args = parseArgs(Deno.args, {
  string: ["n", "seed", "model", "concurrency", "min-score"],
  boolean: ["grounded"],
  default: { n: "100", seed: "1", model: "gemini-2.5-flash", concurrency: "4", "min-score": "0.5" },
});

// --grounded: the prompt that also asks for USDA descriptions; every dish is then scored twice,
// with the model's own numbers and with USDA values, from the same answer (a paired comparison).
const foodIndex = args.grounded
  ? new FoodIndex((JSON.parse(await Deno.readTextFile(new URL("../../supabase/functions/analyze-meal/reference_foods.json", import.meta.url))) as { foods: ReferenceRow[] }).foods)
  : null;

async function cachedFetch(url: string, path: string): Promise<Uint8Array | null> {
  try {
    return await Deno.readFile(path);
  } catch {
    const resp = await fetch(url);
    if (!resp.ok) return null;
    const bytes = new Uint8Array(await resp.arrayBuffer());
    await Deno.mkdir(path.substring(0, path.lastIndexOf("/")), { recursive: true });
    await Deno.writeFile(path, bytes);
    return bytes;
  }
}

async function loadReference(): Promise<ReferenceDish[]> {
  const dishes: ReferenceDish[] = [];
  for (const cafe of ["cafe1", "cafe2"]) {
    const bytes = await cachedFetch(`${BASE}/metadata/dish_metadata_${cafe}.csv`, `${CACHE}/metadata_${cafe}.csv`);
    if (bytes) dishes.push(...parseDishMetadata(new TextDecoder().decode(bytes)));
  }
  return dishes;
}

async function estimate(dish: ReferenceDish): Promise<DishResult | null> {
  const png = await cachedFetch(
    `${BASE}/imagery/realsense_overhead/${dish.id}/rgb.png`,
    `${CACHE}/images/${dish.id}.png`,
  );
  if (!png) return null; // not every dish has an overhead photo
  const started = Date.now();
  try {
    const r: NutritionResponse = await callGemini(
      buildPhotoPrompt(null),
      encodeBase64(png),
      args.model,
      "image/png",
      args.grounded ? GROUNDED_SYSTEM_PROMPT : SYSTEM_PROMPT,
    );
    let grounded: DishResult["grounded"];
    if (foodIndex) {
      const items = r.items.map((i) => groundItem(i, foodIndex, Number(args["min-score"])));
      grounded = {
        calories: items.reduce((a, i) => a + i.calories, 0),
        mass: items.reduce((a, i) => a + i.weight_est_g, 0),
        fat: items.reduce((a, i) => a + i.fat_g, 0),
        carbs: items.reduce((a, i) => a + i.carbs_g, 0),
        protein: items.reduce((a, i) => a + i.protein_g, 0),
        itemsGrounded: items.filter((i) => i.grounded).length,
        itemsTotal: items.length,
      };
    }
    return {
      grounded,
      id: dish.id,
      reference: dish,
      estimate: {
        calories: r.totals.calories,
        mass: r.items.reduce((a, i) => a + i.weight_est_g, 0),
        fat: r.totals.fat_g,
        carbs: r.totals.carbs_g,
        protein: r.totals.protein_g,
      },
      items: r.items.map((i) => i.item),
      latencyMs: Date.now() - started,
    };
  } catch (e) {
    console.warn(`dish ${dish.id}: ${(e as Error).message.slice(0, 120)}`);
    return null;
  }
}

if (!Deno.env.get("GEMINI_API_KEY")) {
  console.error("Set GEMINI_API_KEY in your shell first (the same key the gateway uses).");
  Deno.exit(1);
}

const reference = await loadReference();
const sample = sampleDishes(reference, Number(args.n) * 2, Number(args.seed)); // extra: some have no photo
const results: DishResult[] = [];
const queue = [...sample];
const target = Number(args.n);
async function worker() {
  while (queue.length && results.length < target) {
    const dish = queue.shift()!;
    const r = await estimate(dish);
    if (r && results.length < target) {
      results.push(r);
      console.log(`${results.length}/${target} ${dish.id}: ref ${Math.round(dish.calories)} kcal, est ${r.estimate.calories} kcal`);
    }
  }
}
await Promise.all(Array.from({ length: Number(args.concurrency) }, worker));

const summary = summarize(results);
console.log("\nModel's own numbers\n" + summary.table);
let groundedSummary: ReturnType<typeof summarize> | null = null;
if (foodIndex) {
  const asGrounded = results.filter((r) => r.grounded).map((r) => ({ ...r, estimate: r.grounded! }));
  groundedSummary = summarize(asGrounded);
  const covered = results.reduce((acc, r) => acc + (r.grounded?.itemsGrounded ?? 0), 0);
  const total = results.reduce((acc, r) => acc + (r.grounded?.itemsTotal ?? 0), 0);
  console.log(`\nUSDA values where matched (${covered}/${total} items, min score ${args["min-score"]})\n` + groundedSummary.table);
}
await Deno.mkdir(`${HERE}results`, { recursive: true });
const out = `${HERE}results/${new Date().toISOString().replace(/[:.]/g, "-")}.json`;
await Deno.writeTextFile(out, JSON.stringify({ model: args.model, seed: args.seed, grounded: args.grounded, n: results.length, summary: summary.metrics, groundedSummary: groundedSummary?.metrics ?? null, results }, null, 2));
console.log(`\nSaved ${out}`);
