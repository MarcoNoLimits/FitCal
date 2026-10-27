# FitCal launch video — plan

**One line:** Snap a meal. See the macros.
**Viewer's problem:** logging food means searching databases, weighing and guessing portions.
**Structure:** Before / after.
**Proof moment:** a portion gets corrected on the result card (Rice 200 g → 150 g) and the calories
and macro bars re-settle on the spot — the estimate is editable, not a black box.
**Tone:** `app-store` (clean feature motion, device-framed UI) with `default` energy on the cuts.
**Brand:** ink `#0F172A`, emerald `#10B981` / `#34D399`, blue `#60A5FA`, amber `#FBBF24`,
paper `#F8FAFC`, slate `#94A3B8`. Logo: `site/brand/fitcal-icon.svg`.
**BPM:** 120 (beat 0.5 s, bar 2 s) — snapped to the track in step 3.
**Total:** ~21 s.

UI on screen is an honest recreation of the result card from `store-assets` (same labels, colours
and layout). The meal numbers are illustrative, as on the store feature graphic.

| # | Start | End | On screen | Words | Motion | Out |
|---|-------|-----|-----------|-------|--------|-----|
| 1 | 0.0 | 2.0 | Full-bleed photo of a chicken & rice bowl, slow push-in | **How many calories is this?** | type zooms from large to rest | hard cut |
| 2 | 2.0 | 4.0 | Ink ground, food-search rows flicking past fast (Chicken breast, grilled / raw / 100 g / 1 cup…) | **Searching. Weighing. Guessing.** (word by word) | rows scroll, words pop on beats | push |
| 3 | 4.0 | 5.0 | Full-bleed emerald | **Just snap it.** | pop on the accent | wipe |
| 4 | 5.0 | 8.0 | Phone frame: camera view of the bowl, shutter press, white flash | FitCal logo chip top | phone slides up, shutter scale-tap, flash | flash cut |
| 5 | 8.0 | 12.0 | Result card builds beside the photo: 592 kcal, Protein 48 g / Carbs 64 g / Fat 16 g bars, chips Chicken 180 g · Rice 200 g · Broccoli 90 g | **Calories and macros. From one photo.** | card slides in, bars grow, chips pop in order | slide |
| 6 | 12.0 | 16.0 | Same card, Rice chip highlighted, 200 g → 150 g, kcal 592 → 527, carbs bar shrinks | **Wrong portion? Change it. The totals update.** | tap pulse on chip, numbers swap, bar re-animates | push |
| 7 | 16.0 | 17.0 | Emerald full-bleed | **No account needed.** | pop | wipe |
| 8 | 17.0 | 21.0 | Ink ground, logo + FitCal wordmark, line, CTA | **FitCal** · Snap a meal. See the macros. · *CTA (to confirm)* · fitcal.tecaa.xyz | logo grow, line fade by word, CTA slide | hold, music fade 1 s |

## Build (as shipped)

Built with Remotion, not CoDesign: CoDesign's native CE.SDK 1.83 engine crashes on start on this
Windows machine ("tried to use the Result of an object with error"), so it cannot export mp4.

> Not the chosen version: the `/brag` video in `brag-output/` replaced it. The rendered mp4 was
> removed; this plan and its source stay because the brag video reuses its components.

- `remotion/src/Video.tsx` — the whole video (1080×1920, 30 fps, 630 frames).
  `npm run render:launch` in `remotion/` synthesizes the music and writes `launch.mp4`.
- `scripts/music.mjs` — synthesizes the original 120 BPM bed into `remotion/public/music.wav`
  (crashes on 4, 8, 16, 17 s; whooshes into each cut; button ending at 20 s; 1 s fade).
- `scripts/beats.mjs` — beat-grid analysis; on this bed it locks at 120.0 BPM (39/40 hits, 6 ms
  mean error), so every cut above sits on a beat unchanged.
- Changes from the table: beats 5 and 6 are one continuous scene (the card stays put while the
  headline changes) — the proof reads better without a cut. CTA is "Get it on Google Play".
- The meal is a drawn illustration, not a photo (no image generator was available). Swap in a
  real photo by replacing the `Bowl` component with an `<Img>`.

## Story checks
- One breath: "Snap a meal, see the macros — and fix any portion." ✔
- Proof moment (beat 6) is shown, not said: the number changes after the tap. ✔
- Input before output: the bowl photo (1, 4) comes before the result card (5). ✔
- Specific to FitCal: editable portions + recalculation is the project's own differentiator. ✔
