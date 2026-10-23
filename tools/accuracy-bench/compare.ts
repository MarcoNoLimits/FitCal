/**
 * Prints one table comparing the benchmark result files in results/ (newest run per model),
 * optionally only those written after --since (ISO time).
 *   deno run --allow-read tools/accuracy-bench/compare.ts [--since 2026-10-23T15:00:00Z]
 */
import { parseArgs } from "jsr:@std/cli@1/parse-args";

const args = parseArgs(Deno.args, { string: ["since"] });
const dir = new URL("./results/", import.meta.url);
const since = args.since ? Date.parse(args.since) : 0;

type Row = { model: string; file: string; n: number; m: Record<string, { mae: number; mape: number; bias: number; within20: number }> };
const latest = new Map<string, Row>();
for await (const e of Deno.readDir(dir)) {
  if (!e.name.endsWith(".json")) continue;
  const stat = await Deno.stat(new URL(e.name, dir));
  if ((stat.mtime?.getTime() ?? 0) < since) continue;
  const r = JSON.parse(await Deno.readTextFile(new URL(e.name, dir)));
  if (!r.n) continue;
  const model = `${r.model}${r.grounded ? " (grounded prompt)" : ""}`;
  const prev = latest.get(model);
  if (!prev || e.name > prev.file) latest.set(model, { model, file: e.name, n: r.n, m: r.summary });
}

const f = (x: number) => (Number.isFinite(x) ? x.toFixed(1) : "-");
console.log("| model | dishes | kcal error | % error | bias | within ±20% | fat bias | portion % error |");
console.log("|---|---|---|---|---|---|---|---|");
for (const r of [...latest.values()].sort((a, b) => a.m.calories.mape - b.m.calories.mape)) {
  const c = r.m.calories;
  console.log(`| ${r.model} | ${r.n} | ${f(c.mae)} | ${f(c.mape)}% | ${c.bias > 0 ? "+" : ""}${f(c.bias)}% | ${f(c.within20)}% | ${f(r.m.fat.bias)}% | ${f(r.m.mass.mape)}% |`);
}
