/**
 * Grounding estimates in verified data (accuracy work, 2026-10-23): the model names each food
 * and estimates its grams, and also gives the closest USDA FoodData Central description. When
 * that description matches a USDA SR Legacy food closely enough, the item's nutrition is
 * computed from the lab values per 100 g instead of the model's own numbers.
 *
 * Matching is IDF-weighted word overlap: rare words ("salmon", "quinoa") count far more than
 * common ones ("cooked", "raw"), and the first comma-part (the main food in USDA naming) must
 * share a word. Pure: give it the reference rows; tests and the benchmark use it directly.
 */

export type ReferenceRow = [number, string, number, number, number, number, number, number];
// fdc_id, description, kcal, protein_g, carbs_g, fat_g, fiber_g, sugar_g  (per 100 g)

export interface ReferenceFood {
  fdcId: number;
  description: string;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  sugar: number;
}

export interface FoodMatch {
  food: ReferenceFood;
  /** 0..1, share of the (weighted) words the two descriptions have in common. */
  score: number;
}

const STOPWORDS = new Set(["and", "or", "with", "without", "the", "of", "in", "a", "an", "to", "for", "from", "as", "made", "type", "style", "ns", "nfs", "prepared"]);

export function tokens(text: string): string[] {
  return text.toLowerCase().replace(/[^a-z0-9 ]+/g, " ").split(/\s+/)
    .filter((t) => t.length > 1 && !STOPWORDS.has(t))
    .map((t) => (t.length > 3 && t.endsWith("s") && !t.endsWith("ss") ? t.slice(0, -1) : t)); // crude singular
}

export class FoodIndex {
  private readonly foods: ReferenceFood[];
  private readonly foodTokens: Set<string>[];
  private readonly headTokens: Set<string>[];
  private readonly idf = new Map<string, number>();
  private readonly byToken = new Map<string, number[]>();

  constructor(rows: ReferenceRow[]) {
    this.foods = rows.map(([fdcId, description, kcal, protein, carbs, fat, fiber, sugar]) =>
      ({ fdcId, description, kcal, protein, carbs, fat, fiber, sugar })
    );
    this.foodTokens = this.foods.map((f) => new Set(tokens(f.description)));
    this.headTokens = this.foods.map((f) => new Set(tokens(f.description.split(",")[0])));
    const df = new Map<string, number>();
    this.foodTokens.forEach((set, i) => {
      for (const t of set) {
        df.set(t, (df.get(t) ?? 0) + 1);
        const list = this.byToken.get(t);
        if (list) list.push(i); else this.byToken.set(t, [i]);
      }
    });
    const n = this.foods.length;
    for (const [t, d] of df) this.idf.set(t, Math.log(1 + n / d));
  }

  private weight(t: string): number {
    return this.idf.get(t) ?? Math.log(1 + this.foods.length); // unknown words weigh like the rarest
  }

  /** Best match for a description, or null when nothing shares the main food word. */
  match(description: string): FoodMatch | null {
    const q = new Set(tokens(description));
    if (q.size === 0) return null;
    const qHead = new Set(tokens(description.split(",")[0]));
    const candidates = new Set<number>();
    for (const t of q) for (const i of this.byToken.get(t) ?? []) candidates.add(i);
    let best: FoodMatch | null = null;
    for (const i of candidates) {
      const f = this.foodTokens[i];
      // The main food must agree: "Chicken, ..." never grounds to "Beef, ..., chicken broth".
      if (![...this.headTokens[i]].some((t) => qHead.has(t))) continue;
      let shared = 0;
      let union = 0;
      for (const t of new Set([...q, ...f])) {
        const w = this.weight(t);
        union += w;
        if (q.has(t) && f.has(t)) shared += w;
      }
      const score = union > 0 ? shared / union : 0;
      if (!best || score > best.score) best = { food: this.foods[i], score };
    }
    return best;
  }
}

/** Minimum match score for using the USDA values; tuned with tools/accuracy-bench. */
export const GROUNDING_MIN_SCORE = 0.5;

export interface GroundableItem {
  item: string;
  weight_est_g: number;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  usda_description?: string;
}

/** The item with USDA values for its grams when the match is good enough, else unchanged. */
export function groundItem<T extends GroundableItem>(item: T, index: FoodIndex, minScore = GROUNDING_MIN_SCORE): T & { grounded?: { fdcId: number; score: number } } {
  const desc = item.usda_description?.trim();
  if (!desc || item.weight_est_g <= 0) return item;
  const m = index.match(desc);
  if (!m || m.score < minScore) return item;
  const g = item.weight_est_g / 100;
  const r1 = (v: number) => Math.round(v * 10) / 10;
  return {
    ...item,
    calories: Math.round(m.food.kcal * g),
    protein_g: r1(m.food.protein * g),
    carbs_g: r1(m.food.carbs * g),
    fat_g: r1(m.food.fat * g),
    grounded: { fdcId: m.food.fdcId, score: Math.round(m.score * 100) / 100 },
  };
}
