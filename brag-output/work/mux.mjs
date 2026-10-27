// node mux.mjs — finish brag.mp4: lay the music under the silent Brag96 render and bake the poster
// in as frame 0 (brag-slim step 4). Needs ffmpeg on PATH, work/brag96-silent.mp4 (npm run brag in
// launch-video/remotion) and the track at work/music-candidates/vlog-beat.mp3 (Pixabay "Vlog Beat"
// by vaitsez, not committed — download it again if missing).
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const work = dirname(fileURLToPath(import.meta.url));
const out = join(work, '..');
const silent = join(work, 'brag96-silent.mp4');
const track = join(work, 'music-candidates', 'vlog-beat.mp3');
const TRACK_START = 5.601; // a phrase boundary on the track's 96 BPM grid (pick96.mjs)
const POSTER_AT = 16.75; // "Wrong portion? Type the grams." settled, totals already updated
const LEN = 25;

for (const f of [silent, track]) if (!existsSync(f)) throw new Error(`missing ${f}`);
const ff = (...args) => execFileSync('ffmpeg', ['-nostdin', '-v', 'error', '-y', ...args], { stdio: 'inherit' });

ff('-ss', String(POSTER_AT), '-i', silent, '-frames:v', '1', '-q:v', '2', join(out, 'brag.jpg'));
ff(
  '-i', silent, '-i', join(out, 'brag.jpg'), '-ss', String(TRACK_START), '-i', track,
  '-filter_complex',
  `[0:v][1:v]overlay=enable='eq(n,0)'[v];` +
    `[2:a]atrim=0:${LEN},asetpts=PTS-STARTPTS,afade=t=in:d=0.02,afade=t=out:st=${LEN - 1.2}:d=1.2,` +
    `loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[a]`,
  '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k',
  '-shortest', join(out, 'brag.mp4')
);
console.log('wrote brag-output/brag.mp4 and brag.jpg');
