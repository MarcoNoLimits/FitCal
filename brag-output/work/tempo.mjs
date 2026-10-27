// node tempo.mjs <audio> — tempo, beat phase and downbeat of a full mix.
// Onset envelope: positive log-energy change, low band (<150 Hz, kick) plus full band (snare/hats).
// Tempo: comb-filtered autocorrelation over 60–180 BPM (lags 1–4 beats), weighted toward ~120 BPM
// so half/double time doesn't win by accident. Phase: grid offset collecting the most onset energy.
import { execFileSync } from 'node:child_process';

const file = process.argv[2], SR = 11025, HOP = 110, FPS = SR / HOP;
const envOf = (af) => {
  const args = ['-v', 'error', '-i', file, ...(af ? ['-af', af] : []), '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-'];
  const pcm = execFileSync('ffmpeg', args, { maxBuffer: 1 << 30 });
  const x = new Float32Array(pcm.buffer, pcm.byteOffset, pcm.length / 4);
  const n = Math.floor(x.length / HOP), e = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    let s = 0;
    for (let j = i * HOP; j < (i + 1) * HOP; j++) s += x[j] * x[j];
    e[i] = Math.log1p(1e4 * s / HOP);
  }
  const o = e.map((v, i) => (i ? Math.max(0, v - e[i - 1]) : 0));
  const m = o.reduce((a, b) => a + b, 0) / o.length;
  return o.map((v) => v / m);
};
const low = envOf('lowpass=f=150,lowpass=f=150'), full = envOf(null);
const n = Math.min(low.length, full.length);
const on = Float64Array.from({ length: n }, (_, i) => low[i] + 0.7 * full[i]);
// Remove the local mean so the autocorrelation measures periodicity, not loudness.
const W = Math.round(FPS);
const hp = Float64Array.from(on, (v, i) => {
  let s = 0, c = 0;
  for (let k = Math.max(0, i - W); k < Math.min(n, i + W); k++) { s += on[k]; c++; }
  return v - s / c;
});
const acAt = (lagF) => {
  const l0 = Math.floor(lagF), fr = lagF - l0;
  let s = 0;
  for (let i = l0 + 1; i < n; i++) s += hp[i] * (hp[i - l0] * (1 - fr) + hp[i - l0 - 1] * fr);
  return s / (n - l0);
};
let best = null;
for (let bpm = 60; bpm <= 180; bpm += 0.05) {
  const lag = (60 / bpm) * FPS;
  let s = 0;
  for (let k = 1; k <= 4; k++) s += acAt(k * lag) / k ** 0.5;
  const w = Math.exp(-0.5 * (Math.log2(bpm / 120) / 0.9) ** 2);
  if (!best || s * w > best.score) best = { bpm, score: s * w };
}
const period = 60 / best.bpm;
const at = (t) => { const p = t * FPS, i = Math.floor(p); return i < 0 || i + 1 >= n ? 0 : Math.max(on[i], on[i + 1]); };
let phase = 0, ps = -1;
for (let ph = 0; ph < period; ph += 0.002) {
  let s = 0;
  for (let t = ph; t < n / FPS; t += period) s += at(t);
  if (s > ps) { ps = s; phase = ph; }
}
const beats = [];
for (let t = phase; t < n / FPS; t += period) beats.push(+t.toFixed(3));
const bar = [0, 1, 2, 3].map((p) => beats.filter((_, i) => i % 4 === p).reduce((s, t) => s + at(t), 0));
const down = bar.indexOf(Math.max(...bar));
console.log(JSON.stringify({ bpm: +best.bpm.toFixed(2), period: +period.toFixed(4), phase: +phase.toFixed(3), downbeatIndex: down, firstBeats: beats.slice(0, 8) }));
