import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Easing,
  interpolate,
  Sequence,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig
} from 'remotion';
import { loadFont } from '@remotion/google-fonts/Inter';

const { fontFamily } = loadFont('normal', { weights: ['500', '600', '700', '800'] });

export const FPS = 30;
export const TOTAL = 630; // 21 s
const B = 15; // one beat at 120 BPM

// Brand (store-assets/src/feature-graphic-1024x500.html, site/brand/fitcal-icon.svg).
const C = {
  ink: '#0F172A',
  ink2: '#1E293B',
  emerald: '#10B981',
  mint: '#34D399',
  blue: '#60A5FA',
  amber: '#FBBF24',
  paper: '#F8FAFC',
  slate: '#94A3B8',
  slate5: '#64748B',
  slate7: '#334155',
  line: '#E2E8F0',
  chipBg: '#ECFDF5',
  chipFg: '#047857'
};

// Cut points (frames), all on the 120 BPM grid of public/music.wav.
const CUT = { hook: 0, search: 60, snap: 120, camera: 150, result: 240, proof: 360, noAccount: 480, cta: 510 };
const PUSH = 9; // frames a scene keeps playing under the next one's push-in

const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
const easeOut = Easing.bezier(0.16, 1, 0.3, 1);

const pop = (frame: number, fps: number, delay = 0) =>
  spring({ frame: frame - delay, fps, config: { damping: 13, stiffness: 180, mass: 0.7 } });

const settle = (frame: number, from: number, to: number, start = 0, len = 12) =>
  interpolate(frame, [start, start + len], [from, to], { ...clamp, easing: easeOut });

// ---------------------------------------------------------------------------------------------
// Shared pieces

export const Logo: React.FC<{ size: number }> = ({ size }) => (
  <svg width={size} height={size} viewBox="0 0 108 108">
    <rect width="108" height="108" rx="24" fill={C.ink} />
    <path fill={C.emerald} d="M40.5,28 A6.5,6.5 0 0,1 47,34.5 V73.5 A6.5,6.5 0 0,1 34,73.5 V34.5 A6.5,6.5 0 0,1 40.5,28 Z" />
    <path fill={C.blue} d="M40.5,28 H69.5 A6.5,6.5 0 0,1 69.5,41 H40.5 A6.5,6.5 0 0,1 40.5,28 Z" />
    <path fill={C.amber} d="M40.5,48 H57.5 A6.5,6.5 0 0,1 57.5,61 H40.5 A6.5,6.5 0 0,1 40.5,48 Z" />
    <path fill={C.emerald} d="M40.5,28 A6.5,6.5 0 1,1 40.49,28 Z" />
    <path fill={C.mint} d="M72,68 A6,6 0 1,1 71.99,68 Z" />
  </svg>
);

// A chicken & rice bowl, top-down — the same meal as the store feature graphic.
export const Bowl: React.FC<{ size: number }> = ({ size }) => {
  const grains = Array.from({ length: 70 }, (_, i) => {
    const a = i * 2.399, r = 30 + ((i * 37) % 120);
    return { x: 200 + Math.cos(a) * r * 0.9 - 30, y: 200 + Math.sin(a) * r * 0.75 + 35, rot: (i * 47) % 180 };
  });
  const florets = [
    [292, 150, 34], [322, 196, 30], [296, 240, 32], [258, 120, 26], [338, 248, 24]
  ];
  return (
    <svg width={size} height={size} viewBox="0 0 400 400">
      <defs>
        <radialGradient id="rim" cx="45%" cy="40%" r="60%">
          <stop offset="0%" stopColor="#FFFFFF" />
          <stop offset="100%" stopColor="#D9DEE5" />
        </radialGradient>
        <radialGradient id="rice" cx="40%" cy="40%" r="70%">
          <stop offset="0%" stopColor="#FFFBF2" />
          <stop offset="100%" stopColor="#EADFC8" />
        </radialGradient>
        <linearGradient id="chicken" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#E7B57A" />
          <stop offset="100%" stopColor="#C98B4E" />
        </linearGradient>
      </defs>
      <ellipse cx="206" cy="214" rx="190" ry="190" fill="rgba(0,0,0,0.35)" />
      <circle cx="200" cy="200" r="190" fill="url(#rim)" />
      <circle cx="200" cy="200" r="160" fill="#E9EDF2" />
      <circle cx="200" cy="200" r="150" fill="url(#rice)" />
      {grains.map((g, i) => (
        <ellipse key={i} cx={g.x} cy={g.y} rx="6" ry="2.6" fill="#FFFFFF" opacity="0.85" transform={`rotate(${g.rot} ${g.x} ${g.y})`} />
      ))}
      {/* sliced grilled chicken fanned across the left */}
      {[0, 1, 2, 3, 4].map((i) => (
        <g key={i} transform={`rotate(${-38 + i * 13} 190 210)`}>
          <rect x="70" y={150 + i * 4} width="120" height="34" rx="14" fill="url(#chicken)" />
          {[0, 1, 2].map((k) => (
            <rect key={k} x={92 + k * 30} y={152 + i * 4} width="7" height="30" rx="3" fill="#8B5A2B" opacity="0.55" />
          ))}
        </g>
      ))}
      {/* broccoli */}
      {florets.map(([x, y, r], i) => (
        <g key={i}>
          <circle cx={x} cy={y} r={r} fill="#2F7A35" />
          <circle cx={x - r * 0.35} cy={y - r * 0.3} r={r * 0.55} fill="#3F9A43" />
          <circle cx={x + r * 0.35} cy={y - r * 0.15} r={r * 0.45} fill="#4DAA4F" />
          <circle cx={x} cy={y + r * 0.35} r={r * 0.4} fill="#2B6B2F" />
        </g>
      ))}
      {/* sesame */}
      {[[150, 300], [180, 318], [214, 292], [130, 270], [240, 320]].map(([x, y], i) => (
        <ellipse key={i} cx={x} cy={y} rx="4" ry="2" fill="#F5E9C8" transform={`rotate(${i * 40} ${x} ${y})`} />
      ))}
    </svg>
  );
};

// The table the meal sits on: warm wood with soft light, so the bowl reads as a photo set.
export const Table: React.FC = () => (
  <AbsoluteFill
    style={{
      background:
        'radial-gradient(circle at 50% 45%, #9A7350 0%, #6E4E33 45%, #3B2A1C 100%)'
    }}
  >
    <AbsoluteFill
      style={{
        backgroundImage:
          'repeating-linear-gradient(92deg, rgba(0,0,0,0.06) 0px, rgba(0,0,0,0.06) 3px, transparent 3px, transparent 38px)'
      }}
    />
  </AbsoluteFill>
);

const Headline: React.FC<{
  children: React.ReactNode;
  size?: number;
  color?: string;
  delay?: number;
  align?: 'left' | 'center';
  style?: React.CSSProperties;
}> = ({ children, size = 104, color = C.paper, delay = 0, align = 'left', style }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = pop(frame, fps, delay);
  return (
    <div
      style={{
        fontFamily,
        fontWeight: 800,
        fontSize: size,
        lineHeight: 1.05,
        letterSpacing: '-0.035em',
        color,
        textAlign: align,
        opacity: interpolate(p, [0, 0.4], [0, 1], clamp),
        transform: `translateY(${(1 - p) * 40}px) scale(${1.15 - 0.15 * p})`,
        transformOrigin: align === 'center' ? 'center' : 'left center',
        ...style
      }}
    >
      {children}
    </div>
  );
};

// Scene wrapper: pushes in from `from` over the previous scene on its cut.
const Scene: React.FC<{ from?: 'right' | 'bottom' | 'none'; bg?: string; children: React.ReactNode }> = ({
  from = 'right',
  bg = C.ink,
  children
}) => {
  const frame = useCurrentFrame();
  const t = from === 'none' ? 1 : settle(frame, 0, 1, 0, PUSH);
  const tx = from === 'right' ? (1 - t) * 1080 : 0;
  const ty = from === 'bottom' ? (1 - t) * 1920 : 0;
  return (
    <AbsoluteFill style={{ background: bg, transform: `translate(${tx}px, ${ty}px)`, overflow: 'hidden' }}>
      {children}
    </AbsoluteFill>
  );
};

// ---------------------------------------------------------------------------------------------
// Beat 1 — the hook: the meal, and the question everyone asks.

const Hook: React.FC = () => {
  const frame = useCurrentFrame();
  const zoom = interpolate(frame, [0, 60 + PUSH], [1.0, 1.12]);
  return (
    <Scene from="none">
      <Table />
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', transform: `scale(${zoom})` }}>
        <div style={{ marginTop: 340 }}>
          <Bowl size={900} />
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ background: 'linear-gradient(180deg, rgba(15,23,42,0.92) 0%, rgba(15,23,42,0.55) 38%, rgba(15,23,42,0) 58%)' }} />
      <div style={{ position: 'absolute', left: 96, right: 96, top: 210 }}>
        <Headline size={128}>How many calories is this?</Headline>
      </div>
    </Scene>
  );
};

// Beat 2 — the old way: searching a database, weighing, guessing.

const searchRows = [
  ['Chicken breast, grilled', '100 g', '165'],
  ['Chicken breast, raw', '100 g', '120'],
  ['Chicken, rotisserie', '1 cup', '231'],
  ['Rice, white, cooked', '1 cup', '205'],
  ['Rice, jasmine, cooked', '100 g', '129'],
  ['Broccoli, steamed', '1 cup', '55'],
  ['Broccoli, raw', '100 g', '34'],
  ['Teriyaki chicken bowl', '1 bowl', '640?'],
  ['Chicken rice bowl (restaurant)', '1 serving', '780?'],
  ['Rice, brown, cooked', '1 cup', '216']
];

const Search: React.FC = () => {
  const frame = useCurrentFrame();
  const scroll = interpolate(frame, [0, 60], [0, -520], { ...clamp, easing: Easing.inOut(Easing.quad) });
  const typed = 'chicken rice bowl'.slice(0, Math.floor(interpolate(frame, [2, 20], [0, 17], clamp)));
  const words = ['Searching.', 'Weighing.', 'Guessing.'];
  return (
    <Scene from="right">
      <div style={{ position: 'absolute', left: 72, right: 72, top: 150, fontFamily }}>
        <div
          style={{
            background: C.ink2,
            borderRadius: 28,
            padding: '30px 36px',
            fontSize: 44,
            fontWeight: 500,
            color: C.paper,
            border: `2px solid ${C.slate7}`
          }}
        >
          <span style={{ color: C.slate }}>⌕ </span>
          {typed}
          <span style={{ opacity: frame % 16 < 8 ? 1 : 0, color: C.mint }}>|</span>
        </div>
        <div style={{ height: 760, overflow: 'hidden', marginTop: 28, position: 'relative' }}>
          <div style={{ transform: `translateY(${scroll}px)` }}>
            {searchRows.map(([name, qty, kcal], i) => (
              <div
                key={i}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '30px 8px',
                  borderBottom: `2px solid ${C.ink2}`,
                  color: C.slate,
                  fontSize: 38,
                  fontWeight: 500
                }}
              >
                <span style={{ color: C.paper }}>{name}</span>
                <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {qty} · {kcal}
                </span>
              </div>
            ))}
          </div>
          <div style={{ position: 'absolute', inset: 0, background: `linear-gradient(180deg, transparent 60%, ${C.ink} 100%)` }} />
        </div>
      </div>
      <div style={{ position: 'absolute', left: 96, right: 96, top: 1110 }}>
        {words.map((w, i) => (
          <Headline key={w} size={150} delay={i * B} color={i === 2 ? C.amber : C.paper}>
            {w}
          </Headline>
        ))}
      </div>
    </Scene>
  );
};

// Beat 3 — the turn.

const Statement: React.FC<{ text: string }> = ({ text }) => (
  <Scene from="right" bg={C.emerald}>
    <AbsoluteFill style={{ justifyContent: 'center', padding: '0 96px' }}>
      <Headline size={170} color={C.ink} align="center">
        {text}
      </Headline>
    </AbsoluteFill>
  </Scene>
);

// Beat 4 — the camera: the meal in the viewfinder, shutter, flash, scan.

const SHUTTER = 2 * B + 15; // 6.5 s — on the beat

const Camera: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const rise = spring({ frame, fps, config: { damping: 18, stiffness: 120 } });
  const press = interpolate(frame, [SHUTTER - 4, SHUTTER, SHUTTER + 5], [1, 0.82, 1], clamp);
  const flash = interpolate(frame, [SHUTTER, SHUTTER + 2, SHUTTER + 12], [0, 1, 0], clamp);
  const captured = frame >= SHUTTER;
  const scanY = interpolate(frame, [SHUTTER + 8, 90 + PUSH], [0, 1], clamp);
  const pw = 760, ph = 1500;
  return (
    <Scene from="right">
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        <div
          style={{
            width: pw,
            height: ph,
            borderRadius: 90,
            background: '#020617',
            border: `14px solid ${C.ink2}`,
            boxShadow: '0 40px 120px rgba(0,0,0,0.55)',
            position: 'relative',
            overflow: 'hidden',
            transform: `translateY(${(1 - rise) * 900}px)`
          }}
        >
          {/* viewfinder */}
          <div style={{ position: 'absolute', left: 0, right: 0, top: 160, height: 1000, overflow: 'hidden' }}>
            <Table />
            <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', transform: `scale(${captured ? 1.0 : 1.04})` }}>
              <Bowl size={680} />
            </AbsoluteFill>
            {!captured &&
              [
                { left: 60, top: 140, b: 'borderTop borderLeft' },
                { right: 60, top: 140, b: 'borderTop borderRight' },
                { left: 60, bottom: 140, b: 'borderBottom borderLeft' },
                { right: 60, bottom: 140, b: 'borderBottom borderRight' }
              ].map(({ b, ...pos }, i) => (
                <div
                  key={i}
                  style={{
                    position: 'absolute',
                    width: 90,
                    height: 90,
                    ...pos,
                    ...Object.fromEntries(b.split(' ').map((k) => [k, `6px solid ${C.paper}`]))
                  }}
                />
              ))}
            {captured && (
              <>
                <AbsoluteFill style={{ background: 'rgba(15,23,42,0.25)' }} />
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    top: `${scanY * 100}%`,
                    height: 6,
                    background: C.mint,
                    boxShadow: `0 0 40px 12px ${C.mint}`
                  }}
                />
              </>
            )}
          </div>
          {/* top bar: brand chip */}
          <div style={{ position: 'absolute', top: 54, left: 0, right: 0, display: 'flex', justifyContent: 'center' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                background: 'rgba(30,41,59,0.9)',
                padding: '12px 26px 12px 14px',
                borderRadius: 999,
                fontFamily,
                fontWeight: 700,
                fontSize: 34,
                color: C.paper
              }}
            >
              <Logo size={52} />
              FitCal
            </div>
          </div>
          {/* bottom: status + shutter */}
          <div
            style={{
              position: 'absolute',
              top: 1190,
              left: 0,
              right: 0,
              textAlign: 'center',
              fontFamily,
              fontWeight: 600,
              fontSize: 34,
              color: captured ? C.mint : C.slate
            }}
          >
            {captured ? 'Estimating ingredients and portions…' : 'Point at your meal'}
          </div>
          <div style={{ position: 'absolute', bottom: 70, left: 0, right: 0, display: 'flex', justifyContent: 'center' }}>
            <div
              style={{
                width: 150,
                height: 150,
                borderRadius: 999,
                border: `8px solid ${C.paper}`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transform: `scale(${press})`
              }}
            >
              <div style={{ width: 116, height: 116, borderRadius: 999, background: captured ? C.emerald : C.paper }} />
            </div>
          </div>
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ background: '#FFFFFF', opacity: flash }} />
    </Scene>
  );
};

// Beats 5 + 6 — the result card, then the proof: correct a portion and the totals follow.

const TAP = CUT.proof - CUT.result + B; // 12.5 s — finger taps the Rice chip
const CHANGE = TAP + 2 * B; // 13.5 s — 200 g becomes 150 g

const MacroRow: React.FC<{ label: string; grams: number; max: number; color: string; delay: number }> = ({
  label,
  grams,
  max,
  color,
  delay
}) => {
  const frame = useCurrentFrame();
  const grow = settle(frame, 0, 1, delay, 18);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 28, marginTop: 30, fontSize: 40, fontWeight: 600 }}>
      <span style={{ width: 170, color: C.slate7 }}>{label}</span>
      <div style={{ flex: 1, height: 22, background: C.line, borderRadius: 12, overflow: 'hidden' }}>
        <div style={{ width: `${(grams / max) * 100 * grow}%`, height: '100%', background: color, borderRadius: 12 }} />
      </div>
      <span style={{ width: 110, textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: C.ink }}>
        {Math.round(grams * grow)} g
      </span>
    </div>
  );
};

const Result: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const cardIn = spring({ frame: frame - 4, fps, config: { damping: 16, stiffness: 140 } });
  // Totals before and after the correction (store feature graphic values; rice 200 g → 150 g).
  const t = settle(frame, 0, 1, CHANGE, 16);
  const kcalUp = settle(frame, 0, 592, 8, 22);
  const kcal = frame < CHANGE ? kcalUp : 592 + (527 - 592) * t;
  const carbs = 64 + (50 - 64) * t;
  const protein = 48 + (47 - 48) * t;
  const riceG = frame < CHANGE ? 200 : 150;
  const tapP = interpolate(frame, [TAP, TAP + 14], [0, 1], clamp);
  const showFinger = frame >= TAP - 8 && frame < CHANGE + 10;
  const stepperIn = settle(frame, 0, 1, TAP + 4, 8) * (1 - settle(frame, 0, 1, CHANGE + 14, 8));
  const proof = frame >= CUT.proof - CUT.result;
  const chips = [
    ['Chicken', '180 g'],
    ['Rice', `${riceG} g`],
    ['Broccoli', '90 g']
  ];
  return (
    <Scene from="right">
      {/* headline: beat 5 then beat 6 */}
      <div style={{ position: 'absolute', left: 96, right: 96, top: 130, height: 260 }}>
        {!proof ? (
          <>
            <Headline size={84}>Calories and macros.</Headline>
            <Headline size={84} delay={B} color={C.mint}>
              From one photo.
            </Headline>
          </>
        ) : (
          <Sequence from={CUT.proof - CUT.result} layout="none">
            <Headline size={84}>Wrong portion? Change it.</Headline>
            <Headline size={84} delay={CHANGE - (CUT.proof - CUT.result)} color={C.mint}>
              The totals update.
            </Headline>
          </Sequence>
        )}
      </div>
      {/* the photo just taken */}
      <div
        style={{
          position: 'absolute',
          left: 90,
          right: 90,
          top: 430,
          height: 420,
          borderRadius: 44,
          overflow: 'hidden',
          transform: `scale(${0.9 + 0.1 * cardIn})`,
          opacity: cardIn
        }}
      >
        <Table />
        <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
          <Bowl size={560} />
        </AbsoluteFill>
      </div>
      {/* result card */}
      <div
        style={{
          position: 'absolute',
          left: 90,
          right: 90,
          top: 890,
          background: C.paper,
          borderRadius: 48,
          padding: '52px 56px 56px',
          fontFamily,
          color: C.ink,
          boxShadow: '0 30px 80px rgba(0,0,0,0.45)',
          transform: `translateY(${(1 - cardIn) * 700}px)`
        }}
      >
        <div style={{ fontSize: 32, fontWeight: 700, color: C.slate5, letterSpacing: '0.12em', textTransform: 'uppercase' }}>
          Chicken &amp; rice bowl
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 16, margin: '10px 0 14px' }}>
          <span style={{ fontSize: 150, fontWeight: 800, letterSpacing: '-0.04em', fontVariantNumeric: 'tabular-nums', lineHeight: 1 }}>
            {Math.round(kcal)}
          </span>
          <span style={{ fontSize: 46, fontWeight: 600, color: C.slate5 }}>kcal</span>
        </div>
        <MacroRow label="Protein" grams={protein} max={65} color={C.emerald} delay={12} />
        <MacroRow label="Carbs" grams={carbs} max={78} color={C.blue} delay={16} />
        <MacroRow label="Fat" grams={16} max={42} color={C.amber} delay={20} />
        <div style={{ display: 'flex', gap: 18, marginTop: 44, paddingTop: 36, borderTop: `2px solid ${C.line}`, flexWrap: 'wrap' }}>
          {chips.map(([name, g], i) => {
            const isRice = name === 'Rice';
            const chipPop = pop(frame, fps, 26 + i * 5);
            const active = isRice && frame >= TAP && frame < CHANGE + 24;
            return (
              <div
                key={name}
                style={{
                  position: 'relative',
                  background: active ? C.emerald : C.chipBg,
                  color: active ? C.paper : C.chipFg,
                  borderRadius: 999,
                  padding: '14px 28px',
                  fontSize: 36,
                  fontWeight: 700,
                  fontVariantNumeric: 'tabular-nums',
                  transform: `scale(${chipPop})`
                }}
              >
                {name} {g}
                {isRice && showFinger && (
                  <div
                    style={{
                      position: 'absolute',
                      left: '50%',
                      top: '50%',
                      width: 90,
                      height: 90,
                      marginLeft: -45,
                      marginTop: -45,
                      borderRadius: 999,
                      border: `5px solid ${C.ink}`,
                      opacity: 1 - tapP * 0.8,
                      transform: `scale(${0.6 + tapP * 0.9})`
                    }}
                  />
                )}
              </div>
            );
          })}
        </div>
      </div>
      {/* portion stepper that opens from the Rice chip */}
      <div
        style={{
          position: 'absolute',
          left: 260,
          right: 260,
          top: 1716,
          background: C.ink2,
          border: `2px solid ${C.slate7}`,
          borderRadius: 32,
          padding: '22px 30px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontFamily,
          fontWeight: 700,
          fontSize: 44,
          color: C.paper,
          opacity: stepperIn,
          transform: `translateY(${(1 - stepperIn) * 30}px)`
        }}
      >
        <span style={{ color: C.mint }}>−</span>
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>Rice {Math.round(200 - 50 * settle(frame, 0, 1, CHANGE - 6, 10))} g</span>
        <span style={{ color: C.mint }}>+</span>
      </div>
    </Scene>
  );
};

// Beat 8 — the call to action, held.

const Cta: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const logo = pop(frame, fps, 0);
  return (
    <Scene from="bottom">
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', fontFamily }}>
        <div style={{ transform: `scale(${logo})` }}>
          <Logo size={260} />
        </div>
        <Headline size={150} align="center" delay={4} style={{ marginTop: 40 }}>
          FitCal
        </Headline>
        <Headline size={64} align="center" delay={B} color={C.slate} style={{ marginTop: 30, fontWeight: 700, letterSpacing: '-0.02em' }}>
          Snap a meal. <span style={{ color: C.mint }}>See the macros.</span>
        </Headline>
        <div style={{ marginTop: 110, transform: `scale(${pop(frame, fps, 2 * B)})` }}>
          <div
            style={{
              background: C.paper,
              color: C.ink,
              borderRadius: 999,
              padding: '34px 64px',
              fontSize: 56,
              fontWeight: 800,
              letterSpacing: '-0.02em',
              display: 'flex',
              alignItems: 'center',
              gap: 22
            }}
          >
            <svg width="50" height="54" viewBox="0 0 50 54">
              <path d="M4 2 L46 27 L4 52 Z" fill={C.emerald} />
            </svg>
            Get it on Google Play
          </div>
        </div>
        <div style={{ marginTop: 50, fontSize: 46, fontWeight: 600, color: C.slate, opacity: settle(frame, 0, 1, 3 * B, 12) }}>
          fitcal.tecaa.xyz
        </div>
      </AbsoluteFill>
    </Scene>
  );
};

// ---------------------------------------------------------------------------------------------

const span = (from: number, to: number) => ({ from, durationInFrames: to - from + PUSH });

export const FitCalLaunch: React.FC = () => (
  <AbsoluteFill style={{ background: C.ink }}>
    <Audio src={staticFile('music.wav')} />
    <Sequence {...span(CUT.hook, CUT.search)}>
      <Hook />
    </Sequence>
    <Sequence {...span(CUT.search, CUT.snap)}>
      <Search />
    </Sequence>
    <Sequence {...span(CUT.snap, CUT.camera)}>
      <Statement text={'Just snap it.'} />
    </Sequence>
    <Sequence {...span(CUT.camera, CUT.result)}>
      <Camera />
    </Sequence>
    <Sequence {...span(CUT.result, CUT.noAccount)}>
      <Result />
    </Sequence>
    <Sequence {...span(CUT.noAccount, CUT.cta)}>
      <Statement text="No account needed." />
    </Sequence>
    <Sequence from={CUT.cta} durationInFrames={TOTAL - CUT.cta}>
      <Cta />
    </Sequence>
  </AbsoluteFill>
);
