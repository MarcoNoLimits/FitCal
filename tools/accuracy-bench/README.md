# FitCal accuracy benchmark

Measures how close FitCal's photo estimates are to lab-weighed food. Run it before and after any
change to the prompt or the model. Raw per-run files go to `results/` (git-ignored); record the
summary in the table below.

**Data:** [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k) (Google Research,
CC BY 4.0): about 5,000 cafeteria dishes with every ingredient weighed and an overhead photo.
Only the sampled dishes' photos are downloaded (about 0.5 MB each), into `.cache/` (git-ignored).

**What it runs:** the production prompt and model from `supabase/functions/analyze-meal/vlm.ts`,
called directly with your Gemini or OpenRouter key (not through the app, so it uses no scan quota).

## Run

Needs [Deno](https://deno.com). From the repo root, in your own terminal:

```bash
GEMINI_API_KEY=your-key deno run --allow-net --allow-env --allow-read --allow-write tools/accuracy-bench/bench.ts --n 100 --seed 1
```

- `--n`: dishes to test (100 is a good balance; ~$0.03 of Gemini usage).
- `--seed`: which dishes. Keep the same seed to compare runs fairly.
- `--provider openrouter --model <id>`: test any OpenRouter model (needs `OPENROUTER_API_KEY`).
- `compare-models.ps1` runs several models on the same dishes and prints one table.

## Results (100 dishes, seed 1, 2026-10-23)

| model | % calorie error | within ±20% | bias | median latency |
|---|---|---|---|---|
| **google/gemini-3.1-flash-lite** (production) | 40.2% | 38% | −3% | 3.7 s |
| google/gemini-3-flash-preview | 41.0% | 33% | | 4.9 s |
| google/gemini-2.5-flash (fallback) | 47.8% | 28% | | 3.7 s |
| openai/gpt-5-mini | 53.4% | | +37% | 15 s |
| anthropic/claude-sonnet-5.5 | 62.8% | | +38% | |
| anthropic/claude-haiku-5.5 | 63% | | +40% | |

Tried and dropped: replacing the model's numbers with USDA values for matched foods made
estimates worse; the main error is portion size, not the per-gram values.

## Reading the result

| column | meaning |
|---|---|
| mean error | average miss, in kcal or grams |
| mean % error | average miss as a share of the real value |
| bias | negative = estimates run low, positive = high |
| within ±20% | share of dishes close enough to be useful |

Caveat: Nutrition5k is US cafeteria food photographed from straight above, a bit easier than
real phone photos at an angle. Use it to compare versions of FitCal, not as the number to quote.

Tests for the metric code: `deno test tools/accuracy-bench/metrics_test.ts`
