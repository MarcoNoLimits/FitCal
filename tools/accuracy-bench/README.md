# FitCal accuracy benchmark

Measures how close FitCal's photo estimates are to lab-weighed food. Run it before and after any
change to the prompt, the model or the food data, and keep the result files in `results/`.

**Data:** [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k) (Google Research,
CC BY 4.0): about 5,000 cafeteria dishes with every ingredient weighed and an overhead photo.
Only the sampled dishes' photos are downloaded (about 0.5 MB each), into `.cache/` (git-ignored).

**What it runs:** the production prompt and model from `supabase/functions/analyze-meal/vlm.ts`,
called directly with your Gemini key (not through the app, so it uses no scan quota).

## Run

Needs [Deno](https://deno.com). From the repo root, in your own terminal:

```bash
GEMINI_API_KEY=your-key deno run --allow-net --allow-env --allow-read --allow-write tools/accuracy-bench/bench.ts --n 100 --seed 1
```

- `--n`: dishes to test (100 is a good balance; ~$0.03 of Gemini usage).
- `--seed`: which dishes. Keep the same seed to compare runs fairly.
- `--model`: defaults to production (`gemini-2.5-flash`).

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
