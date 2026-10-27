// node beats.mjs <audio> [--bpm <requested>] > beats.json — beat grid, onsets and accents from any audio ffmpeg reads.
import { execFileSync } from 'node:child_process';
const SR = 22050,
  HOP = 512;
const flag = process.argv.indexOf('--bpm');
const requested = flag > 0 ? Number(process.argv[flag + 1]) : null;
const pcm = execFileSync(
  'ffmpeg',
  [
    '-v',
    'error',
    '-i',
    process.argv[2],
    '-ac',
    '1',
    '-ar',
    String(SR),
    '-f',
    'f32le',
    '-'
  ],
  { maxBuffer: 1 << 30 }
);
const x = new Float32Array(pcm.buffer, pcm.byteOffset, pcm.length / 4);
const fps = SR / HOP,
  n = Math.floor(x.length / HOP),
  duration = x.length / SR;
const energy = new Float64Array(n);
for (let i = 0; i < n; i++) {
  let s = 0;
  for (let j = i * HOP; j < (i + 1) * HOP; j++) s += x[j] * x[j];
  energy[i] = Math.log1p((1000 * s) / HOP);
}
const flux = energy.map((e, i) => (i ? Math.max(0, e - energy[i - 1]) : 0));
const W = Math.round(fps / 2); // adaptive threshold: local mean over ±0.5 s
const onsets = [];
for (let i = 1; i < n - 1; i++) {
  let m = 0;
  for (let k = Math.max(0, i - W); k < Math.min(n, i + W); k++) m += flux[k];
  m /= Math.min(n, i + W) - Math.max(0, i - W);
  if (
    flux[i] > 1.5 * m &&
    flux[i] > 0.05 &&
    flux[i] >= flux[i - 1] &&
    flux[i] > flux[i + 1]
  )
    onsets.push({
      t: i / fps,
      attack: flux[i],
      strength: Math.max(...energy.slice(i, i + 4))
    });
}
const sum = (a, f) => a.reduce((s, v) => s + f(v), 0);
const attack = sum(onsets, (o) => o.attack),
  nEff = attack ** 2 / sum(onsets, (o) => o.attack ** 2);
// The onsets within `tol` of the grid `phase + k·period`, each with its beat index k and its error.
const near = (period, phase, tol = Math.min(0.05, period / 8)) =>
  onsets
    .map((o) => ({ o, k: Math.round((o.t - phase) / period) }))
    .map((p) => ({ ...p, err: p.o.t - phase - p.k * period }))
    .filter((p) => Math.abs(p.err) < tol);
// How well a grid explains the attacks: share of attack energy on it, above what chance would put there.
const measure = (period, phase) => {
  const tol = Math.min(0.05, period / 8),
    chance = (2 * tol) / period,
    pts = near(period, phase);
  return {
    period,
    phase: phase - period * Math.floor(phase / period),
    score: sum(pts, (p) => p.o.attack) / attack - chance,
    noise: Math.sqrt((chance * (1 - chance)) / nEff),
    onGrid: pts.length,
    meanErrMs: +(
      (1000 * sum(pts, (p) => Math.abs(p.err))) / pts.length || 0
    ).toFixed(1)
  };
};
// Refine a tempo guess: seed the phase from the attacks, then weighted least squares on the onsets near the grid.
const fit = (period) => {
  let sx = 0,
    sy = 0;
  for (const o of onsets) {
    sx += o.attack * Math.cos((2 * Math.PI * o.t) / period);
    sy += o.attack * Math.sin((2 * Math.PI * o.t) / period);
  }
  let phase = (Math.atan2(sy, sx) / (2 * Math.PI)) * period;
  for (let it = 0; it < 10; it++) {
    const tol = Math.min(0.05, period / 8),
      pts = near(period, phase, tol);
    for (const p of pts)
      p.w = p.o.attack * Math.exp(-(((2 * p.err) / tol) ** 2));
    const w = sum(pts, (p) => p.w),
      mk = sum(pts, (p) => p.w * p.k) / w,
      mt = sum(pts, (p) => p.w * p.o.t) / w,
      vk = sum(pts, (p) => p.w * (p.k - mk) ** 2);
    if (pts.length < 4 || !vk) return null;
    period = sum(pts, (p) => p.w * (p.k - mk) * (p.o.t - mt)) / vk;
    phase = mt - period * mk;
  }
  return measure(period, phase);
};
const ac = (l) => {
  let s = 0;
  for (let i = l; i < n; i++) s += flux[i] * flux[i - l];
  return s;
};
let lag = 0; // tempo guess: autocorrelation peak of the flux over 70–180 BPM
for (
  let l = Math.round((fps * 60) / 180);
  l <= Math.round((fps * 60) / 70);
  l++
)
  if (!lag || ac(l) > ac(lag)) lag = l;
// The peak can lock onto a related tempo (2/3, 3/4 …) — keep the alias whose grid carries the most
// attack. Halving or doubling the grid fits by construction, so the requested BPM decides that.
let best = null;
for (const r of [1, 2, 1 / 2, 3 / 2, 2 / 3, 4 / 3, 3 / 4]) {
  const f =
    onsets.length >= 8 &&
    lag / fps / r >= 0.25 &&
    lag / fps / r <= 1.5 &&
    fit(lag / fps / r);
  if (f && (!best || f.score > best.score)) best = f;
}
let grid = best && best.score > 3 * best.noise ? best : null;
if (grid && requested) {
  const { period: p, phase: ph } = grid,
    half = [measure(2 * p, ph), measure(2 * p, ph + p)].sort(
      (a, b) => b.score - a.score
    )[0];
  const off = (g) => Math.abs(Math.log(60 / g.period / requested));
  grid = [grid, half, measure(p / 2, ph)].sort((a, b) => off(a) - off(b))[0];
}
const beats = [];
for (let t = grid?.phase; grid && t < duration; t += grid.period)
  beats.push(+t.toFixed(3));
const max = Math.max(...onsets.map((o) => o.strength));
const hits = onsets.map((o) => ({
  t: +o.t.toFixed(3),
  strength: +(o.strength / max).toFixed(2)
}));
console.log(
  JSON.stringify({
    duration: +duration.toFixed(3),
    bpm: grid && +(60 / grid.period).toFixed(2),
    requestedBpm: requested,
    fit: grid && {
      onsets: onsets.length,
      onGrid: grid.onGrid,
      meanErrMs: grid.meanErrMs
    },
    beats,
    accents: hits.filter((o) => o.strength >= 0.8).map((o) => o.t),
    onsets: hits
  })
);
