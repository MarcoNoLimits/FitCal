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

import { buildPhotoPrompt, callGemini, type NutritionResponse } from "../../supabase/functions/analyze-meal/vlm.ts";
import { encodeBase64 } from "jsr:@std/encoding@1/base64";
import { parseArgs } from "jsr:@std/cli@1/parse-args";
import { summarize, type DishResult, parseDishMetadata, type ReferenceDish, sampleDishes } from "./metrics.ts";

const BASE = "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset";
const HERE = new URL(".", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const CACHE = `${HERE}.cache`;

const args = parseArgs(Deno.args, {
  string: ["n", "seed", "model", "concurrency"],
  default: { n: "100", seed: "1", model: "gemini-2.5-flash", concurrency: "4" },
});

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
    const r: NutritionResponse = await callGemini(buildPhotoPrompt(null), encodeBase64(png), args.model, "image/png");
    return {
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
console.log("\n" + summary.table);
await Deno.mkdir(`${HERE}results`, { recursive: true });
const out = `${HERE}results/${new Date().toISOString().replace(/[:.]/g, "-")}.json`;
await Deno.writeTextFile(out, JSON.stringify({ model: args.model, seed: args.seed, n: results.length, summary: summary.metrics, results }, null, 2));
console.log(`\nSaved ${out}`);
