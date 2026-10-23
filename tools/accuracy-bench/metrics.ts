/**
 * Pure helpers for the accuracy benchmark: parsing Nutrition5k metadata, seeded sampling, and
 * error metrics. No network or file access, so metrics_test.ts can check them.
 */

export interface ReferenceDish {
  id: string;
  calories: number;
  mass: number;
  fat: number;
  carbs: number;
  protein: number;
}

export interface Estimate {
  calories: number;
  mass: number;
  fat: number;
  carbs: number;
  protein: number;
}

export interface DishResult {
  id: string;
  reference: ReferenceDish;
  estimate: Estimate;
  items: string[];
  latencyMs: number;
}

/**
 * dish_metadata_cafeN.csv rows start with: dish_id, total_calories, total_mass, total_fat,
 * total_carb, total_protein, then one group of columns per ingredient (ignored here).
 * Dishes under 20 kcal (drinks, condiments alone) are dropped: percent errors explode there.
 */
export function parseDishMetadata(csv: string): ReferenceDish[] {
  const out: ReferenceDish[] = [];
  for (const line of csv.split(/\r?\n/)) {
    const c = line.split(",");
    if (c.length < 6 || !c[0].startsWith("dish_")) continue;
    const [calories, mass, fat, carbs, protein] = c.slice(1, 6).map(Number);
    if (![calories, mass, fat, carbs, protein].every(Number.isFinite) || calories < 20) continue;
    out.push({ id: c[0], calories, mass, fat, carbs, protein });
  }
  return out;
}

/** Deterministic shuffle (mulberry32) so a given seed always picks the same dishes. */
export function sampleDishes<T>(items: T[], n: number, seed: number): T[] {
  let a = seed >>> 0;
  const rand = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, n);
}

export interface NutrientMetrics {
  /** Mean absolute error, in the nutrient's unit (kcal or g). */
  mae: number;
  /** Mean absolute error as a share of the reference value, %. */
  mape: number;
  /** Mean signed error, %: negative = the estimates run low. */
  bias: number;
  /** Share of dishes estimated within ±20% of the reference, %. */
  within20: number;
}

const KEYS = ["calories", "mass", "protein", "carbs", "fat"] as const;

function metricsFor(results: DishResult[], key: typeof KEYS[number]): NutrientMetrics {
  // Percent errors only where the reference is big enough to be meaningful (≥ 5 g or ≥ 20 kcal).
  const floor = key === "calories" ? 20 : 5;
  const abs = results.map((r) => Math.abs(r.estimate[key] - r.reference[key]));
  const pct = results.filter((r) => r.reference[key] >= floor).map((r) => (r.estimate[key] - r.reference[key]) / r.reference[key] * 100);
  const mean = (xs: number[]) => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : NaN;
  return {
    mae: mean(abs),
    mape: mean(pct.map(Math.abs)),
    bias: mean(pct),
    within20: pct.length ? pct.filter((p) => Math.abs(p) <= 20).length / pct.length * 100 : NaN,
  };
}

export function summarize(results: DishResult[]): { metrics: Record<string, NutrientMetrics>; table: string } {
  const metrics = Object.fromEntries(KEYS.map((k) => [k, metricsFor(results, k)]));
  const f = (n: number) => Number.isFinite(n) ? n.toFixed(1) : "-";
  const rows = KEYS.map((k) => {
    const m = metrics[k];
    const unit = k === "calories" ? "kcal" : "g";
    return `| ${k} | ${f(m.mae)} ${unit} | ${f(m.mape)}% | ${m.bias > 0 ? "+" : ""}${f(m.bias)}% | ${f(m.within20)}% |`;
  });
  const table = [
    `Dishes: ${results.length}`,
    "| value | mean error | mean % error | bias | within ±20% |",
    "|---|---|---|---|---|",
    ...rows,
  ].join("\n");
  return { metrics, table };
}
