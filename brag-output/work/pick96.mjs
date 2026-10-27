// node pick96.mjs <audio> <bpm> <firstDownbeat> <len> — strongest LEN-second window starting on a downbeat.
import { execFileSync } from 'node:child_process';
const [file, bpm, first, len] = process.argv.slice(2).map((v, i) => (i ? Number(v) : v));
const SR = 8000, pcm = execFileSync('ffmpeg', ['-v', 'error', '-i', file, '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-'], { maxBuffer: 1 << 30 });
const x = new Float32Array(pcm.buffer, pcm.byteOffset, pcm.length / 4), Q = SR / 4, rms = [];
for (let i = 0; i + Q <= x.length; i += Q) { let s = 0; for (let j = i; j < i + Q; j++) s += x[j] * x[j]; rms.push(Math.sqrt(s / Q)); }
const peak = Math.max(...rms), mean = (a, b) => { let s = 0, c = 0; for (let i = Math.round(a * 4); i < Math.min(rms.length, Math.round(b * 4)); i++) { s += rms[i]; c++; } return s / Math.max(1, c) / peak; };
const bar = 4 * 60 / bpm, dur = x.length / SR, out = [];
for (let t = first; t + len + 0.5 <= dur; t += bar) out.push({ t: +t.toFixed(3), body: +mean(t, t + len).toFixed(2), hook: +mean(t, t + 2).toFixed(2), score: mean(t, t + len) + 0.5 * mean(t, t + 2) });
out.sort((a, b) => b.score - a.score);
console.log('track', dur.toFixed(1), 's; energy by 10 s:', Array.from({ length: Math.ceil(dur / 10) }, (_, i) => mean(i * 10, i * 10 + 10).toFixed(2)).join(' '));
console.log(out.slice(0, 4));
