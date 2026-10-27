// node music.mjs <out.wav> — original 120 BPM, 21 s instrumental bed for the FitCal launch video.
// Kick on every beat, off-beat hats, a bass line on Am–F–C–G, soft pad chords, a crash on the
// accents (4 s "Just snap it", 8 s result, 16 s, 17 s CTA) and a clean ending on the last downbeat.
import { writeFileSync } from 'node:fs';

const SR = 44100, BPM = 120, BEAT = 60 / BPM, LEN = 21;
const N = SR * LEN;
const L = new Float32Array(N), R = new Float32Array(N);
const add = (i, l, r = l) => { if (i >= 0 && i < N) { L[i] += l; R[i] += r; } };
const hz = (m) => 440 * 2 ** ((m - 69) / 12);

// Seeded noise so the render is reproducible.
let seed = 7;
const noise = () => ((seed = (seed * 16807) % 2147483647) / 2147483647) * 2 - 1;

function kick(t, gain = 0.9) {
  const s = Math.floor(t * SR);
  for (let i = 0; i < SR * 0.35; i++) {
    const x = i / SR, f = 50 + 110 * Math.exp(-x * 30);
    add(s + i, gain * Math.sin(2 * Math.PI * f * x) * Math.exp(-x * 9));
  }
}
function hat(t, gain = 0.12) {
  const s = Math.floor(t * SR);
  let prev = 0;
  for (let i = 0; i < SR * 0.06; i++) {
    const n = noise(), hp = n - prev; prev = n;
    const v = gain * hp * Math.exp(-(i / SR) * 70);
    add(s + i, v * 0.8, v);
  }
}
function crash(t, gain = 0.22) {
  const s = Math.floor(t * SR);
  let prev = 0;
  for (let i = 0; i < SR * 1.6; i++) {
    const n = noise(), hp = n - prev; prev = n;
    const v = gain * hp * Math.exp(-(i / SR) * 2.6);
    add(s + i, v, v * 0.85);
  }
}
function whoosh(tEnd, dur = 0.45, gain = 0.1) {
  const s = Math.floor((tEnd - dur) * SR);
  let lp = 0;
  for (let i = 0; i < SR * dur; i++) {
    const p = i / (SR * dur), a = 0.02 + 0.5 * p * p;
    lp += a * (noise() - lp);
    const v = gain * lp * Math.sin(Math.PI * p);
    add(s + i, v * (1 - p * 0.4), v * (0.6 + p * 0.4));
  }
}
function tone(t, dur, midi, gain, shape = 'tri', pan = 0) {
  const s = Math.floor(t * SR), f = hz(midi);
  for (let i = 0; i < SR * dur; i++) {
    const x = i / SR, ph = (f * x) % 1;
    const w = shape === 'sine' ? Math.sin(2 * Math.PI * ph) : 1 - 4 * Math.abs(ph - 0.5);
    const env = Math.min(1, x / 0.02) * Math.min(1, (dur - x) / 0.08);
    const v = gain * w * env;
    add(s + i, v * (1 - pan), v * (1 + pan));
  }
}

// Am – F – C – G, one chord per bar (2 s).
const chords = [[57, 60, 64], [53, 57, 60], [48, 55, 60], [55, 59, 62]];
const roots = [45, 41, 48, 43];
const bars = Math.round(LEN / (4 * BEAT)); // 10 full bars + the ending hit
for (let b = 0; b < bars; b++) {
  const t0 = b * 4 * BEAT, c = b % 4;
  // Intro bar is lighter: pad + kick only on beats 1 and 3.
  for (let k = 0; k < 4; k++) {
    const t = t0 + k * BEAT;
    if (b > 0 || k % 2 === 0) kick(t, b === 0 ? 0.75 : 0.9);
    if (b > 0) hat(t + BEAT / 2);
    if (b > 0) tone(t, BEAT * 0.45, roots[c], 0.22, 'sine');
    if (b > 0) tone(t + BEAT / 2, BEAT * 0.3, roots[c] + 12, 0.08, 'sine');
  }
  for (const m of chords[c]) tone(t0, 4 * BEAT, m + 12, 0.035, 'tri', (m % 3 - 1) * 0.3);
  // A plucked arpeggio over the proof section (bars 4–7) lifts the energy.
  if (b >= 4 && b <= 7)
    for (let k = 0; k < 8; k++) tone(t0 + k * BEAT / 2, 0.18, chords[c][k % 3] + 24, 0.05, 'tri', k % 2 ? 0.4 : -0.4);
}
for (const t of [4, 8, 16, 17]) crash(t);
for (const t of [2, 4, 5, 8, 12, 16, 17]) whoosh(t);
// Clean button ending on the last downbeat.
kick(20, 1); crash(20, 0.18);
tone(20, 0.9, 45, 0.25, 'sine');
for (const m of chords[0]) tone(20, 0.9, m + 12, 0.05);

// Master: 1 s fade-out, soft clip, normalise to -1 dBFS.
for (let i = 0; i < N; i++) {
  const t = i / SR, g = t > LEN - 1 ? (LEN - t) : 1;
  L[i] = Math.tanh(L[i] * g * 1.1); R[i] = Math.tanh(R[i] * g * 1.1);
}
let peak = 0;
for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
const norm = 0.89 / peak;
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8);
buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  buf.writeInt16LE(Math.round(L[i] * norm * 32767), 44 + i * 4);
  buf.writeInt16LE(Math.round(R[i] * norm * 32767), 46 + i * 4);
}
writeFileSync(process.argv[2], buf);
console.log(`wrote ${process.argv[2]} (${LEN}s, ${BPM} BPM)`);
