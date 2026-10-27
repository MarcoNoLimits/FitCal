# /brag-slim — FitCal

Made by following `/brag-slim` (github.com/latent-spaces/brag, `skills/brag-slim/SKILL.md`) on this
repo, to compare with the CoDesign-skill video in `launch-video/`.

## Inspect

- **What:** an Android app that estimates a meal's calories and macros from one photo.
- **For:** people who track food but hate searching databases and weighing portions.
- **Sets it apart:** every estimate is editable on the spot — change an ingredient's grams and the
  totals recompute locally (README: "~10x faster than re-uploading the image"); no account needed.
- **Visual hook:** the result first — the meal with its ingredients and kcal stamped on it.
- **Real UI to show** (rebuilt from the Compose source, since Compose can't render in a browser and
  no phone was connected): Dashboard `ScanHeroCard` ("Take a photo. FitCal estimates the calories and
  macros."), `ReviewScreen` ("Review Meal", "DETECTED INGREDIENTS" rows with the Swap row, the
  "kcal • P C F" line, the weight pill and the red delete button, "CALCULATED MACROS" grid of
  `MacroGridCard`s, "Recalculate with AI ⚡", "Log Meal to Dashboard"), and the "Meal Logged!" sheet.
  Light theme exactly as `theme/Color.kt`: Slate-50 ground, white cards, Emerald #10B981 accent,
  protein #34D399, carbs #60A5FA, fat #FBBF24, Slate-200 borders.
- **Tone:** `default` — punchy, playful, clean.
- **Share caption:** "One photo of lunch → 592 kcal, every gram editable. FitCal."
- **Fictional data:** the meal (chicken & rice bowl, 592 → 527 kcal) is the same illustrative meal as
  the store feature graphic. No real user data.

## Plan — vertical 1080×1920, 20 s, 120 BPM (beat 0.5 s, bar 2 s), every cut on a beat

| # | Time | Scene | On screen | Words |
|---|------|-------|-----------|-------|
| 1 | 0.0–2.0 | **Hook** (result first) | Full-bleed meal; on beats the labels land on the food — Chicken 180 g, Rice 200 g, Broccoli 90 g — then a 592 kcal stamp | **Snap.** → **592 kcal.** |
| 2 | 2.0–4.0 | **Reveal** | Light phone slides up on the real Dashboard scan card; tap | **FitCal** · Snap a meal. See the macros. |
| 3 | 4.0–9.0 | **Highlight 1** — the result | Review Meal screen: photo banner, DETECTED INGREDIENTS rows appear one by one, camera glides down to CALCULATED MACROS | **Ingredients, grams, macros. From one photo.** |
| 4 | 9.0–14.0 | **Highlight 2** — the proof | Zoom on the Rice row: tap the weight pill, type 150, the row and the macro grid recount (592 → 527 kcal, carbs 64 → 50 g), "Recalculate with AI ⚡" appears | **Wrong portion? Type the grams.** |
| 5 | 14.0–16.0 | **Highlight 3** | Tap "Log Meal to Dashboard" → "Meal Logged!" | **Logged. No account needed.** |
| 6 | 16.0–20.0 | **Outro** | Logo, wordmark, line, Google Play pill, URL — held | **FitCal** · Snap a meal. See the macros. · Get it on Google Play · fitcal.tecaa.xyz |

## Final music (replaces the synthesized track below)

`brag.mp4` now uses `vaitsez-vlog-vlog-beat-620108.mp3`, the owner's download (from its file name, a
Pixabay track by the uploader "vaitsez"). It measures 96 BPM (`work/tempo.mjs`), so the whole
composition is slowed by exactly 1.25× (Remotion composition `Brag96`): every cut, tap and keystroke
that sat on the 120 BPM grid now sits on the 96 BPM grid, and the video runs 25 s. The track starts
at 5.6 s, on a phrase boundary (`work/pick96.mjs`), fades out over the last 1.2 s, and is
loudness-matched to −14 LUFS.

## Re-render

The source lives in `launch-video/remotion/src/Brag.tsx` (it reuses `Bowl`, `Table` and `Logo` from
`Video.tsx`). Needs Node 22+, ffmpeg on PATH, and the track at `work/music-candidates/vlog-beat.mp3`
— the mp3 is gitignored because the repo is public; re-download it from Pixabay if it's missing.

```bash
cd launch-video/remotion && npm install && npm run brag
```

That renders the silent `Brag96` composition to `work/brag96-silent.mp4`, then `work/mux.mjs` lays the
track under it and bakes `brag.jpg` in as frame 0. `npm run studio` opens the compositions for
editing. The `Brag` composition is the original 120 BPM cut with the synth track from
`work/music.mjs` (`node work/music.mjs ../launch-video/remotion/public/brag-music.wav` first).

## Sound (first version)

One piece in C major at 120 BPM: soft kick and shaker, a round bass, a marimba-like pluck melody.
The effects are written into the same grid and key — a muted shutter on 1.0 s, soft woody taps on
every UI tap, quiet key ticks on 16ths while typing, a C–E–G chime on "Meal Logged!" — mixed under
the music. Clean ending on the downbeat at 19 s, 1 s tail.
