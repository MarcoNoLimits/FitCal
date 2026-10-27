// node music.mjs <out.wav> — the /brag soundtrack for FitCal: one piece, C major, 120 BPM, 20 s.
// Music and effects share the grid (counted from frame 0) and the key; effects sit under the music.
import { writeFileSync } from 'node:fs';

const SR = 44100, BEAT = 0.5, LEN = 20;
const N = SR * LEN;
const L = new Float32Array(N), R = new Float32Array(N);
const add = (i, l, r = l) => { if (i >= 0 && i < N) { L[i] += l; R[i] += r; } };
const hz = (m) => 440 * 2 ** ((m - 69) / 12);
let seed = 11;
const noise = () => ((seed = (seed * 16807) % 2147483647) / 2147483647) * 2 - 1;

function kick(t, g = 0.55) {
  const s = Math.floor(t * SR);
  for (let i = 0; i < SR * 0.25; i++) {
    const x = i / SR, f = 48 + 70 * Math.exp(-x * 35);
    add(s + i, g * Math.sin(2 * Math.PI * f * x) * Math.exp(-x * 14));
  }
}
function shaker(t, g = 0.045) {
  const s = Math.floor(t * SR); let p = 0;
  for (let i = 0; i < SR * 0.05; i++) {
    const n = noise(), hp = n - p; p = n;
    const x = i / SR, v = g * hp * Math.min(1, x / 0.004) * Math.exp(-x * 60);
    add(s + i, v * 0.7, v);
  }
}
// Marimba-ish pluck: sine + 4th partial, fast decay.
function pluck(t, midi, g = 0.12, pan = 0, dec = 7) {
  const s = Math.floor(t * SR), f = hz(midi);
  for (let i = 0; i < SR * 0.7; i++) {
    const x = i / SR;
    const v = g * (Math.sin(2 * Math.PI * f * x) + 0.25 * Math.sin(2 * Math.PI * f * 4 * x) * Math.exp(-x * 30)) *
      Math.min(1, x / 0.002) * Math.exp(-x * dec);
    add(s + i, v * (1 - pan), v * (1 + pan));
  }
}
function bass(t, midi, dur, g = 0.2) {
  const s = Math.floor(t * SR), f = hz(midi);
  for (let i = 0; i < SR * dur; i++) {
    const x = i / SR, env = Math.min(1, x / 0.01) * Math.min(1, (dur - x) / 0.05);
    add(s + i, g * env * (Math.sin(2 * Math.PI * f * x) + 0.15 * Math.sin(4 * Math.PI * f * x)));
  }
}
function pad(t, notes, dur, g = 0.022) {
  const s = Math.floor(t * SR);
  for (let i = 0; i < SR * dur; i++) {
    const x = i / SR, env = Math.min(1, x / 0.3) * Math.min(1, (dur - x) / 0.4);
    let v = 0;
    for (const m of notes) v += Math.sin(2 * Math.PI * hz(m) * x + Math.sin(2 * Math.PI * 0.3 * x));
    add(s + i, g * env * v);
  }
}
// Effects, tuned into the piece.
function tap(t, midi = 72, g = 0.07) { pluck(t, midi, g, 0, 28); } // short woody tap on the key's root
function tick(t, g = 0.03) { // a key tick: tiny band-passed click
  const s = Math.floor(t * SR); let a = 0, b = 0;
  for (let i = 0; i < SR * 0.02; i++) {
    a += 0.5 * (noise() - a); b += 0.5 * (a - b);
    add(s + i, g * (a - b) * Math.exp(-(i / SR) * 200));
  }
}
function shutter(t, g = 0.16) { // two soft clicks, the second a beat-sixteenth later
  for (const [o, k] of [[0, 1], [0.06, 0.6]]) {
    const s = Math.floor((t + o) * SR); let p = 0;
    for (let i = 0; i < SR * 0.04; i++) {
      const n = noise(), lp = p + 0.35 * (n - p); p = lp;
      add(s + i, g * k * lp * Math.exp(-(i / SR) * 90));
    }
  }
}
function swell(tEnd, dur = 0.5, g = 0.05) { // airy rise into a cut
  const s = Math.floor((tEnd - dur) * SR); let lp = 0;
  for (let i = 0; i < SR * dur; i++) {
    const p = i / (SR * dur);
    lp += (0.02 + 0.3 * p * p) * (noise() - lp);
    add(s + i, g * lp * p * p, g * lp * p);
  }
}

// C – Am – F – G, one chord per bar.
const prog = [[60, 64, 67], [57, 60, 64], [53, 57, 60], [55, 59, 62]];
const roots = [36, 33, 41, 43];
const melody = [76, 79, 84, 79, 77, 76, 72, 74]; // 8ths over each bar, shifted per chord
for (let bar = 0; bar < 9.5; bar++) {
  const t0 = bar * 4 * BEAT, c = bar % 4;
  pad(t0, prog[c].map((m) => m + 12), 4 * BEAT);
  for (let k = 0; k < 4; k++) {
    const t = t0 + k * BEAT;
    if (t >= 19) break;
    kick(t, bar === 0 ? 0.4 : 0.55);
    shaker(t + BEAT / 2);
    if (bar > 0) shaker(t + BEAT / 4, 0.025), shaker(t + 3 * BEAT / 4, 0.025);
    bass(t, roots[c] + (k === 3 ? 7 : 0), BEAT * 0.8);
  }
  // Melody enters at the reveal (bar 1) and rests under the proof's typing (bar 5) to leave room.
  if (bar >= 1 && bar !== 5)
    for (let k = 0; k < 8; k++) {
      const t = t0 + k * BEAT / 2;
      if (t >= 19) break;
      if (k % 2 === 0 || bar >= 6) pluck(t, melody[k] - (c === 1 ? 3 : c === 2 ? 5 : c === 3 ? 2 : 0), 0.08, k % 2 ? 0.35 : -0.35);
    }
}
// Effects on the storyboard's beats.
shutter(0);
for (const t of [0.5, 0.75, 1.0]) tap(t, 79, 0.06);   // labels land on the food
pluck(1.5, 84, 0.12); pluck(1.5, 88, 0.08);            // 592 kcal stamp
for (const t of [2, 4, 9, 14, 16]) swell(t);
tap(3.0, 72);                                           // tap the scan card
tap(10.0, 72);                                          // tap the Rice weight pill
for (const t of [10.5, 10.75, 11.0]) tick(t);           // type 1, 5, 0
pluck(11.5, 79, 0.08);                                  // totals recount
tap(14.5, 72);                                          // Log Meal to Dashboard
for (const [i, m] of [72, 76, 79, 84].entries()) pluck(15 + i * 0.125, m, 0.1, 0, 5); // Meal Logged! chime
// Ending: a full C chord on the downbeat at 19 s, let ring.
kick(19, 0.6); bass(19, 36, 0.9, 0.22);
for (const m of [72, 76, 79, 84]) pluck(19, m, 0.09, 0, 3.5);

for (let i = 0; i < N; i++) {
  const t = i / SR, g = t > LEN - 0.8 ? (LEN - t) / 0.8 : 1;
  L[i] = Math.tanh(L[i] * g * 1.2); R[i] = Math.tanh(R[i] * g * 1.2);
}
let peak = 0;
for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
const norm = 0.89 / peak, buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8); buf.write('fmt ', 12);
buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write('data', 36);
buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  buf.writeInt16LE(Math.round(L[i] * norm * 32767), 44 + i * 4);
  buf.writeInt16LE(Math.round(R[i] * norm * 32767), 46 + i * 4);
}
writeFileSync(process.argv[2], buf);
console.log(`wrote ${process.argv[2]}`);
