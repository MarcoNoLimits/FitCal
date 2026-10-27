// /brag-slim edition of the FitCal launch video (see brag-output/brag-plan.md).
// The phone screens are rebuilt from the app's Compose source: theme/Color.kt, theme/Radii.kt,
// screens/dashboard/components/ScanHeroCard.kt and screens/review/ReviewScreen.kt.
import React from 'react';
import { AbsoluteFill, Audio, Easing, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont } from '@remotion/google-fonts/Roboto';
import { Bowl, Logo, Table } from './Video';

const { fontFamily } = loadFont('normal', { weights: ['400', '500', '700', '900'] });

export const BRAG_TOTAL = 600; // 20 s at 30 fps, 120 BPM: a beat is 15 frames

// theme/Color.kt
const T = {
  bg: '#F8FAFC',
  card: '#FFFFFF',
  text: '#0F172A',
  muted: '#64748B',
  border: '#E2E8F0',
  inputBorder: '#CBD5E1',
  accent: '#10B981',
  protein: '#34D399',
  carbs: '#60A5FA',
  fat: '#FBBF24',
  dangerSoft: '#FEF2F2',
  danger: '#EF4444',
  surfaceTint: '#F1F5F9',
  infoPanel: '#F8FAFC'
};
// theme/Radii.kt, in dp
const RAD = { xs: 8, s: 12, m: 16, l: 24, xl: 32 };

const S = 860 / 411; // phone screen: 411 dp across 860 px
const d = (dp: number) => dp * S;

const CUT = { reveal: 60, result: 120, proof: 270, logged: 420, outro: 480 };
const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
const ease = Easing.bezier(0.16, 1, 0.3, 1);
const ramp = (f: number, a: number, b: number, from = 0, to = 1, e = ease) =>
  interpolate(f, [a, b], [from, to], { ...clamp, easing: e });

// Ingredients as the app computes them: kcal and macros = per-gram value × grams, truncated (toInt).
const ITEMS = [
  { name: 'Grilled chicken', g: 180, kcal: 1.65, p: 0.228, c: 0, f: 0.078 },
  { name: 'White rice', g: 200, kcal: 1.31, p: 0.027, c: 0.285, f: 0.005 },
  { name: 'Broccoli', g: 90, kcal: 0.367, p: 0.025, c: 0.078, f: 0.012 }
];
const RICE_TYPED = { tap: 300, k1: 315, k2: 322, k3: 330 }; // 10.0 s tap, then 1 · 5 · 0 on the 16ths
const riceGrams = (f: number) =>
  f < RICE_TYPED.k1 ? '200' : f < RICE_TYPED.k2 ? '1' : f < RICE_TYPED.k3 ? '15' : '150';
const totals = (riceG: number) => {
  const items = ITEMS.map((it) => (it.name === 'White rice' ? { ...it, g: riceG } : it));
  return {
    items,
    kcal: items.reduce((s, it) => s + Math.trunc(it.kcal * it.g), 0),
    p: Math.trunc(items.reduce((s, it) => s + it.p * it.g, 0)),
    c: Math.trunc(items.reduce((s, it) => s + it.c * it.g, 0)),
    f: Math.trunc(items.reduce((s, it) => s + it.f * it.g, 0))
  };
};

// ---------------------------------------------------------------------------------------------

const Caption: React.FC<{ f: number; start: number; end: number; lines: [string, string?]; accent?: 1 | 2 }> = ({
  f,
  start,
  end,
  lines,
  accent = 2
}) => {
  const { fps } = useVideoConfig();
  if (f < start || f >= end) return null;
  const out = ramp(f, end - 6, end, 0, 1);
  return (
    <div style={{ position: 'absolute', left: 90, right: 90, top: 120, opacity: 1 - out, transform: `translateY(${-30 * out}px)` }}>
      {lines.map((line, i) => {
        if (!line) return null;
        const p = spring({ frame: f - start - 2 - i * 8, fps, config: { damping: 14, stiffness: 170, mass: 0.7 } });
        return (
          <div
            key={i}
            style={{
              fontFamily,
              fontWeight: 900,
              fontSize: 96,
              lineHeight: 1.04,
              letterSpacing: '-0.035em',
              color: i + 1 === accent ? T.accent : T.text,
              opacity: interpolate(p, [0, 0.35], [0, 1], clamp),
              transform: `translateY(${(1 - p) * 36}px)`
            }}
          >
            {line}
          </div>
        );
      })}
    </div>
  );
};

const Touch: React.FC<{ f: number; at: number; x: number; y: number }> = ({ f, at, x, y }) => {
  if (f < at - 6 || f > at + 14) return null;
  const inP = ramp(f, at - 6, at, 0, 1);
  const ring = ramp(f, at, at + 14, 0, 1);
  return (
    <div style={{ position: 'absolute', left: x - 45, top: y - 45, width: 90, height: 90 }}>
      <div style={{ position: 'absolute', inset: 0, borderRadius: 999, background: 'rgba(15,23,42,0.22)', opacity: inP * (1 - ring), transform: `scale(${1 - 0.15 * ramp(f, at - 2, at + 2)})` }} />
      <div style={{ position: 'absolute', inset: 0, borderRadius: 999, border: '4px solid rgba(15,23,42,0.35)', opacity: (1 - ring) * (f >= at ? 1 : 0), transform: `scale(${1 + ring * 0.9})` }} />
    </div>
  );
};

const StatusBar: React.FC<{ dark?: boolean }> = ({ dark }) => (
  <div style={{ height: d(28), display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: `0 ${d(20)}px`, fontFamily, fontWeight: 500, fontSize: d(12), color: dark ? '#FFF' : T.text }}>
    <span>12:41</span>
    <span style={{ letterSpacing: 2 }}>▾ ▴ ▮</span>
  </div>
);

const SearchIcon: React.FC<{ size: number; color: string }> = ({ size, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24">
    <circle cx="10" cy="10" r="6.5" fill="none" stroke={color} strokeWidth="2.6" />
    <path d="M15 15 L21 21" stroke={color} strokeWidth="2.6" strokeLinecap="round" />
  </svg>
);

// Dashboard: the ScanHeroCard ("Snap and log") with the floating scan button in its slot.
const Dashboard: React.FC<{ f: number }> = ({ f }) => {
  const press = 1 - 0.1 * ramp(f, 86, 90) + 0.1 * ramp(f, 90, 96);
  return (
    <div style={{ background: T.bg, height: '100%', fontFamily }}>
      <StatusBar />
      <div style={{ padding: `${d(8)}px ${d(16)}px` }}>
        <div style={{ fontSize: d(24), fontWeight: 700, color: T.text, marginBottom: d(16) }}>Today</div>
        <div style={{ position: 'relative', height: d(220), borderRadius: d(RAD.xl), background: T.text, overflow: 'hidden', padding: d(20) }}>
          {/* drawn plate, top right (ScanHeroCard.drawBehind) */}
          <div style={{ position: 'absolute', right: d(52) - d(70), top: d(220) * 0.42 - d(70), width: d(140), height: d(140), borderRadius: 999, background: `radial-gradient(circle, rgba(16,185,129,0.35), transparent 70%)` }} />
          <div style={{ position: 'absolute', right: d(52) - d(46), top: d(220) * 0.42 - d(46), width: d(92), height: d(92), borderRadius: 999, background: 'rgba(255,255,255,0.10)', border: `${d(1.5)}px solid rgba(255,255,255,0.2)` }}>
            <div style={{ position: 'absolute', left: d(46 - 12 - 25 * 0.27), top: d(46 - 4.6 - 25 * 0.27), width: d(25 * 0.54), height: d(25 * 0.54), borderRadius: 999, background: T.protein, opacity: 0.85 }} />
            <div style={{ position: 'absolute', left: d(46 + 10 - 10), top: d(46 + 11 - 10), width: d(20), height: d(20), borderRadius: 999, background: T.carbs, opacity: 0.85 }} />
          </div>
          <div style={{ display: 'inline-block', background: 'rgba(255,255,255,0.12)', color: '#FFF', borderRadius: 999, padding: `${d(4)}px ${d(10)}px`, fontSize: d(11) }}>3 scans left today</div>
          <div style={{ color: '#FFF', fontSize: d(14), fontWeight: 600, marginTop: d(14) }}>Snap your lunch</div>
          <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: d(12), marginTop: d(4), width: d(200) }}>
            Take a photo. FitCal estimates the calories and macros.
          </div>
          {/* the floating scan button in the shutter slot */}
          <div style={{ position: 'absolute', left: d(20), bottom: d(18), width: d(64), height: d(64), borderRadius: 999, border: `${d(2)}px solid rgba(255,255,255,0.35)`, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${press})` }}>
            <div style={{ width: d(52), height: d(52), borderRadius: 999, background: T.accent }} />
          </div>
        </div>
      </div>
    </div>
  );
};

const IngredientRow: React.FC<{ f: number; item: (typeof ITEMS)[number]; grams: string; focused: boolean; appear: number }> = ({
  f,
  item,
  grams,
  focused,
  appear
}) => {
  const g = parseInt(grams, 10) || 0;
  const a = ramp(f, appear, appear + 10);
  const fresh = item.name === 'White rice' ? ramp(f, RICE_TYPED.k3, RICE_TYPED.k3 + 12, 1, 0) * (f >= RICE_TYPED.k3 ? 1 : 0) : 0;
  return (
    <div style={{ padding: `${d(4)}px 0`, opacity: a, transform: `translateY(${(1 - a) * d(12)}px)` }}>
      <div style={{ background: T.surfaceTint, borderRadius: d(RAD.s), padding: `${d(10)}px ${d(12)}px`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: d(13), fontWeight: 700, color: T.text }}>{item.name}</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: d(4), color: T.accent, fontSize: d(11), fontWeight: 700 }}>
          <SearchIcon size={d(16)} color={T.accent} /> Swap
        </span>
      </div>
      <div style={{ height: d(6) }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: d(12), color: T.muted, background: `rgba(16,185,129,${0.18 * fresh})`, borderRadius: d(4), padding: `0 ${d(2)}px` }}>
          {Math.trunc(item.kcal * g)} kcal{'  •  '}P: {Math.trunc(item.p * g)}g C: {Math.trunc(item.c * g)}g F: {Math.trunc(item.f * g)}g
        </span>
        <span style={{ display: 'flex', alignItems: 'center', gap: d(8) }}>
          <span
            style={{
              display: 'flex',
              alignItems: 'center',
              height: d(44),
              padding: `0 ${d(10)}px`,
              background: T.surfaceTint,
              borderRadius: d(RAD.s),
              border: `${d(focused ? 2 : 1)}px solid ${focused ? T.accent : T.inputBorder}`,
              fontSize: d(14),
              fontWeight: 600,
              color: T.text
            }}
          >
            <span style={{ width: d(40), textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
              {grams}
              {focused && <span style={{ color: T.accent, opacity: f % 16 < 9 ? 1 : 0 }}>|</span>}
            </span>
            <span style={{ color: T.muted, marginLeft: d(4) }}>g</span>
          </span>
          <span style={{ width: d(44), height: d(44), borderRadius: 999, background: T.dangerSoft, color: T.danger, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: d(18), fontWeight: 700 }}>×</span>
        </span>
      </div>
    </div>
  );
};

const MacroCard: React.FC<{ title: string; value: string; label: string; color: string; flash: number }> = ({ title, value, label, color, flash }) => (
  <div style={{ flex: 1, background: '#FFF', border: `${d(1)}px solid ${T.border}`, borderRadius: d(RAD.m), padding: d(16), boxShadow: `0 0 0 ${d(3) * flash}px rgba(16,185,129,${0.5 * flash})` }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span style={{ fontSize: d(10), fontWeight: 700, color: T.muted, letterSpacing: '0.1em', textTransform: 'uppercase' }}>{title}</span>
      <span style={{ width: d(6), height: d(6), borderRadius: 999, background: color }} />
    </div>
    <div style={{ display: 'flex', alignItems: 'baseline', gap: d(2), marginTop: d(4) }}>
      <span style={{ fontSize: d(18), fontWeight: 700, color: T.text, fontVariantNumeric: 'tabular-nums' }}>{value}</span>
      {label && <span style={{ fontSize: d(11), color: T.muted }}>{label}</span>}
    </div>
  </div>
);

const Eyebrow: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div style={{ fontSize: d(10), fontWeight: 700, color: T.muted, letterSpacing: '0.1em', marginBottom: d(16) }}>{children}</div>
);

const Button: React.FC<{ children: React.ReactNode; pressed?: number }> = ({ children, pressed = 0 }) => (
  <div style={{ height: d(54), borderRadius: d(RAD.m), background: T.accent, color: '#FFF', fontSize: d(14), fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${1 - 0.04 * pressed})` }}>
    {children}
  </div>
);

// Review Meal (ReviewScreen.kt), scrolled by the storyboard.
const Review: React.FC<{ f: number }> = ({ f }) => {
  const grams = f >= RICE_TYPED.tap ? riceGrams(f) : '200';
  const tot = totals(parseInt(grams, 10) || 0);
  const shown = f < RICE_TYPED.k3 ? totals(200) : tot; // totals follow the field once the 0 lands
  const flash = f >= RICE_TYPED.k3 ? ramp(f, RICE_TYPED.k3, RICE_TYPED.k3 + 18, 1, 0) : 0;
  const edited = f >= RICE_TYPED.k3 + 15;
  const scroll =
    ramp(f, 200, 236, 0, d(300), Easing.inOut(Easing.cubic)) +
    ramp(f, 362, 380, 0, d(80), Easing.inOut(Easing.cubic)) +
    ramp(f, 420, 430, 0, d(110), Easing.inOut(Easing.cubic));
  const logPress = ramp(f, 431, 435) - ramp(f, 435, 440);
  return (
    <div style={{ background: T.bg, height: '100%', fontFamily, position: 'relative', overflow: 'hidden' }}>
      <div style={{ transform: `translateY(${-scroll}px)` }}>
        <StatusBar />
        <div style={{ padding: `${d(8)}px ${d(16)}px ${d(16)}px`, fontSize: d(24), fontWeight: 700, color: T.text }}>Review Meal</div>
        <div style={{ margin: `0 ${d(16)}px`, background: T.card, borderRadius: d(RAD.xl), border: `${d(1)}px solid ${T.border}`, boxShadow: `0 ${d(6)}px ${d(18)}px rgba(15,23,42,0.10)`, overflow: 'hidden' }}>
          <div style={{ height: d(200), position: 'relative', overflow: 'hidden', background: T.surfaceTint }}>
            <Table />
            <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
              <Bowl size={d(260)} />
            </AbsoluteFill>
          </div>
          <div style={{ padding: d(24) }}>
            <Eyebrow>DETECTED INGREDIENTS</Eyebrow>
            <div style={{ display: 'flex', flexDirection: 'column', gap: d(16) }}>
              {tot.items.map((it, i) => (
                <IngredientRow
                  key={it.name}
                  f={f}
                  item={it}
                  grams={it.name === 'White rice' ? grams : String(it.g)}
                  focused={it.name === 'White rice' && f >= RICE_TYPED.tap && f < RICE_TYPED.k3 + 30}
                  appear={CUT.result + 10 + i * 8}
                />
              ))}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: d(6), paddingTop: d(20), color: T.accent, fontSize: d(14), fontWeight: 600 }}>
              <span style={{ fontSize: d(18), lineHeight: 1 }}>+</span> Add Item from Database
            </div>
          </div>
          <div style={{ background: T.infoPanel, padding: d(24) }}>
            <Eyebrow>CALCULATED MACROS</Eyebrow>
            <div style={{ display: 'flex', flexDirection: 'column', gap: d(16) }}>
              <div style={{ display: 'flex', gap: d(16) }}>
                <MacroCard title="Calories" value={`${shown.kcal}`} label="kcal" color={T.accent} flash={flash} />
                <MacroCard title="Protein" value={`${shown.p}g`} label="" color={T.protein} flash={flash} />
              </div>
              <div style={{ display: 'flex', gap: d(16) }}>
                <MacroCard title="Carbs" value={`${shown.c}g`} label="" color={T.carbs} flash={flash} />
                <MacroCard title="Fats" value={`${shown.f}g`} label="" color={T.fat} flash={flash} />
              </div>
            </div>
          </div>
          <div style={{ padding: d(24), display: 'flex', flexDirection: 'column', gap: d(12) }}>
            {edited && (
              <div style={{ opacity: ramp(f, RICE_TYPED.k3 + 15, RICE_TYPED.k3 + 25) }}>
                <Button>Recalculate with AI ⚡</Button>
              </div>
            )}
            <Button pressed={logPress}>Log Meal to Dashboard</Button>
          </div>
        </div>
        <div style={{ height: d(200) }} />
      </div>
      {/* Meal Logged! */}
      {f >= 450 && (
        <AbsoluteFill style={{ background: `rgba(15,23,42,${0.45 * ramp(f, 450, 456)})`, alignItems: 'center', justifyContent: 'center' }}>
          <div
            style={{
              width: d(330),
              background: '#FFF',
              borderRadius: d(RAD.l),
              padding: d(28),
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              transform: `scale(${spring({ frame: f - 450, fps: 30, config: { damping: 13, stiffness: 200 } })})`
            }}
          >
            <div style={{ width: d(56), height: d(56), borderRadius: 999, background: 'rgba(16,185,129,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width={d(32)} height={d(32)} viewBox="0 0 24 24">
                <path d="M5 12.5 L10 17.5 L19 7" fill="none" stroke={T.accent} strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" strokeDasharray="24" strokeDashoffset={24 * (1 - ramp(f, 454, 464))} />
              </svg>
            </div>
            <div style={{ fontSize: d(20), fontWeight: 700, color: T.text, marginTop: d(16) }}>Meal Logged!</div>
            <div style={{ fontSize: d(13), color: T.muted, textAlign: 'center', marginTop: d(8), lineHeight: 1.45 }}>
              Your nutrition data has been successfully updated on the dashboard.
            </div>
            <div style={{ width: '100%', marginTop: d(20) }}>
              <Button>Go to Dashboard</Button>
            </div>
          </div>
        </AbsoluteFill>
      )}
    </div>
  );
};

// ---------------------------------------------------------------------------------------------

const Hook: React.FC<{ f: number }> = ({ f }) => {
  const { fps } = useVideoConfig();
  const out = ramp(f, 52, 62);
  const flash = ramp(f, 0, 8, 1, 0);
  // Label anchors on the bowl (Bowl viewBox 400, drawn at 900 px from x=90, y=640).
  const at = (x: number, y: number) => ({ left: 90 + x * 2.25, top: 640 + y * 2.25 });
  const labels = [
    { text: 'Grilled chicken 180 g', pos: at(110, 175), t: 15 },
    { text: 'Broccoli 90 g', pos: at(300, 290), t: 22 },
    { text: 'White rice 200 g', pos: at(190, 330), t: 30 }
  ];
  return (
    <AbsoluteFill style={{ opacity: 1 - out, transform: `scale(${1 + 0.06 * out})` }}>
      <Table />
      <div style={{ position: 'absolute', left: 90, top: 640, transform: `scale(${interpolate(f, [0, 62], [1.04, 1.1])})` }}>
        <Bowl size={900} />
      </div>
      <AbsoluteFill style={{ background: 'linear-gradient(180deg, rgba(248,250,252,0.96) 0%, rgba(248,250,252,0.85) 22%, rgba(248,250,252,0) 36%)' }} />
      {labels.map((l) => {
        const p = spring({ frame: f - l.t, fps, config: { damping: 12, stiffness: 220, mass: 0.6 } });
        return (
          <div
            key={l.text}
            style={{
              position: 'absolute',
              ...l.pos,
              transform: `translate(-50%, -50%) scale(${p})`,
              background: '#FFF',
              borderRadius: 999,
              padding: '16px 28px',
              fontFamily,
              fontWeight: 700,
              fontSize: 36,
              color: T.text,
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              boxShadow: '0 10px 30px rgba(0,0,0,0.3)',
              whiteSpace: 'nowrap'
            }}
          >
            <span style={{ width: 14, height: 14, borderRadius: 999, background: T.accent }} />
            {l.text}
          </div>
        );
      })}
      <div style={{ position: 'absolute', left: 90, top: 120, fontFamily, fontWeight: 900, fontSize: 150, letterSpacing: '-0.04em', color: T.text, lineHeight: 1 }}>
        {f < 45 ? (
          'Snap.'
        ) : (
          <span style={{ color: T.accent, display: 'inline-block', transform: `scale(${spring({ frame: f - 45, fps, config: { damping: 10, stiffness: 240 } })})`, transformOrigin: 'left center' }}>
            592 kcal.
          </span>
        )}
      </div>
      <AbsoluteFill style={{ background: '#FFF', opacity: flash }} />
    </AbsoluteFill>
  );
};

const Outro: React.FC<{ f: number }> = ({ f }) => {
  const { fps } = useVideoConfig();
  const pop = (delay: number) => spring({ frame: f - CUT.outro - delay, fps, config: { damping: 13, stiffness: 170, mass: 0.7 } });
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', fontFamily }}>
      <div style={{ transform: `scale(${pop(0)})` }}>
        <Logo size={250} />
      </div>
      <div style={{ fontSize: 150, fontWeight: 900, letterSpacing: '-0.04em', color: T.text, marginTop: 30, opacity: pop(4), transform: `translateY(${(1 - pop(4)) * 30}px)` }}>
        FitCal
      </div>
      <div style={{ fontSize: 60, fontWeight: 700, letterSpacing: '-0.02em', color: T.muted, marginTop: 16, opacity: pop(15) }}>
        Snap a meal. <span style={{ color: T.accent }}>See the macros.</span>
      </div>
      <div style={{ marginTop: 100, transform: `scale(${pop(30)})`, background: T.text, color: '#FFF', borderRadius: 999, padding: '34px 64px', fontSize: 56, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 22 }}>
        <svg width="46" height="50" viewBox="0 0 50 54">
          <path d="M4 2 L46 27 L4 52 Z" fill={T.accent} />
        </svg>
        Get it on Google Play
      </div>
      <div style={{ marginTop: 44, fontSize: 46, fontWeight: 500, color: T.muted, opacity: ramp(f, CUT.outro + 45, CUT.outro + 57) }}>fitcal.tecaa.xyz</div>
    </AbsoluteFill>
  );
};

// `stretch` slows the whole timeline uniformly so the cuts land on a slower track's beats
// (1.25 maps the 120 BPM grid onto 96 BPM). `music: null` renders silent, for muxing a track later.
export const BragLaunch: React.FC<{ stretch?: number; music?: string | null }> = ({ stretch = 1, music = 'brag-music.wav' }) => {
  const f = useCurrentFrame() / stretch;
  const { fps } = useVideoConfig();
  const rise = spring({ frame: f - CUT.reveal, fps, config: { damping: 18, stiffness: 110 } });
  const leave = ramp(f, CUT.outro - 8, CUT.outro + 4, 0, 1, Easing.in(Easing.cubic));
  // The reveal shows the phone whole; from the result on it sits a little higher and larger.
  const lift = ramp(f, CUT.result - 4, CUT.result + 8, 0, 1);
  const phoneTop = 520 - 60 * lift;
  return (
    <AbsoluteFill style={{ background: T.bg }}>
      {music && <Audio src={staticFile(music)} />}
      {f >= CUT.reveal - 4 && f < CUT.outro + 6 && (
        <div
          style={{
            position: 'absolute',
            left: 90,
            width: 900,
            top: phoneTop,
            height: 1700,
            borderRadius: 100,
            background: '#020617',
            padding: 20,
            boxShadow: '0 40px 100px rgba(15,23,42,0.28)',
            transform: `translateY(${(1 - rise) * 1500 + leave * 1500}px)`
          }}
        >
          <div style={{ width: '100%', height: '100%', borderRadius: 80, overflow: 'hidden', position: 'relative' }}>
            {f < CUT.result ? <Dashboard f={f} /> : <Review f={f} />}
          </div>
          <Touch f={f} at={90} x={20 + d(16) + d(20) + d(32)} y={20 + d(28) + d(8) + d(24) + d(16) + d(220) - d(18) - d(32)} />
          <Touch f={f} at={RICE_TYPED.tap} x={20 + 860 - d(24) - d(16) - d(44) - d(8) - d(30)} y={20 + d(507) - d(300)} />
          <Touch f={f} at={435} x={20 + 430} y={150 + d(1000) - d(490)} />
        </div>
      )}
      <Caption f={f} start={CUT.reveal} end={CUT.result} lines={['FitCal.', 'Snap a meal.']} />
      <Caption f={f} start={CUT.result} end={CUT.proof} lines={['Ingredients, grams,', 'macros. One photo.']} />
      <Caption f={f} start={CUT.proof} end={CUT.logged} lines={['Wrong portion?', 'Type the grams.']} />
      <Caption f={f} start={CUT.logged} end={CUT.outro} lines={['Logged.', 'No account needed.']} />
      {f < CUT.reveal + 4 && <Hook f={f} />}
      {f >= CUT.outro && <Outro f={f} />}
    </AbsoluteFill>
  );
};
