import { Link } from '@tanstack/react-router';
import { Pause, Play } from 'lucide-react';
import { useEffect, useRef, useState, useSyncExternalStore, type CSSProperties, type ElementType, type ReactNode, type RefObject } from 'react';
import { cn } from '@/lib/utils';

/*
 * Wireframe kit: the product drawn in greys (no screenshots this round), always with real, readable words in it.
 * Pills and chips are radius-full and radius-6; cards are radius 12 with the floating shadow.
 */

/** A white wireframe card (radius 12, floating shadow). Never put one inside another. */
export function WireCard({ className, children, as: Tag = 'div', style }: { className?: string; children?: ReactNode; as?: ElementType; style?: CSSProperties }) {
  return (
    <Tag className={cn('lp-card', className)} style={style}>
      {children}
    </Tag>
  );
}

/** A pill: hairline outline, or `ink` (filled) for the selected one. */
export function WirePill({ className, children, ink }: { className?: string; children?: ReactNode; ink?: boolean }) {
  return <span className={cn('lp-pill', ink && 'lp-pill--ink', className)}>{children}</span>;
}

/** A small grey chip (radius 6), e.g. a correction or a tag. */
export function WireChip({ className, children }: { className?: string; children?: ReactNode }) {
  return <span className={cn('lp-chip', className)}>{children}</span>;
}

/** CSS custom properties for a style prop: `style={vars({ '--k': 2 })}`. */
export const vars = (o: Record<string, string | number>) => o as CSSProperties;

/** A check mark drawn inline: lucide's Check would pull the app's overlay chunk (Radix menus) into the landing page. */
export function Tick({ size = 16, strokeWidth = 1.75, className }: { size?: number; strokeWidth?: number; className?: string }) {
  return (
    <svg aria-hidden width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

/**
 * The page's one primary action: a full mock test (all four skills, one overall band), free with a free account and no
 * card; /mock shows the account gate to a guest. The header pill uses the same verb.
 */
export function StartFree({ className, children = 'Take a full mock free', size }: { className?: string; children?: ReactNode; size?: 'sm' }) {
  return (
    <Link to="/mock" className={cn('lp-btn lp-btn--primary', size === 'sm' && 'lp-btn--sm', className)}>
      {children}
    </Link>
  );
}

/*
 * The visitor's "Pause animation" switch (WCAG 2.2.2): one module-level flag, remembered per browser. While it is on,
 * the hero stops re-marking, every landing clock stops, and the infinite loops in landing.css pause.
 */
let paused = false;
try {
  paused = localStorage.getItem('lp-paused') === '1';
} catch {
  /* storage blocked: start playing */
}
const pauseSubs = new Set<() => void>();
export const isPaused = () => paused;
export function setPaused(v: boolean) {
  paused = v;
  try {
    if (v) localStorage.setItem('lp-paused', '1');
    else localStorage.removeItem('lp-paused');
  } catch {
    /* not remembered, still applied */
  }
  pauseSubs.forEach((f) => f());
}
export const usePaused = () =>
  useSyncExternalStore(
    (f) => (pauseSubs.add(f), () => void pauseSubs.delete(f)),
    () => paused,
    () => false,
  );

/**
 * The "Pause animation" switch (WCAG 2.2.2) as a button: `aria-pressed` carries the state, so the name never changes.
 * The hero, the fixed header (icon only) and the footer each carry one, so it is never a scroll away.
 */
export function PauseButton({ className, label }: { className?: string; label?: boolean }) {
  const stopped = usePaused();
  const Icon = stopped ? Play : Pause;
  return (
    <button type="button" className={cn('lp-pause', className)} aria-pressed={stopped} title="Pause animation" onClick={() => setPaused(!stopped)}>
      <Icon size={14} strokeWidth={2} aria-hidden />
      <span className={label ? undefined : 'sr-only'}>Pause animation</span>
    </button>
  );
}

/** "m:ss" (or "mm:ss" from 10 minutes up) for a number of seconds. */
export const clock = (s: number) => `${s >= 600 ? String(Math.floor(s / 60)).padStart(2, '0') : Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

/**
 * Calls `cb(true)` while `el` is within `rootMargin` of the screen and the tab is visible, `cb(false)` otherwise. The one
 * place the landing page asks "is this on screen": every clock, loop and the hero's re-marking start and stop from it.
 * Returns the cleanup. Without IntersectionObserver nothing is ever live.
 */
export function watchLive(el: Element, cb: (live: boolean) => void, rootMargin = '0px') {
  if (!('IntersectionObserver' in window)) return () => {};
  let seen = false;
  const sync = () => cb(seen && !document.hidden);
  const io = new IntersectionObserver((entries) => {
    seen = !!entries.at(-1)?.isIntersecting;
    sync();
  }, { rootMargin });
  io.observe(el);
  document.addEventListener('visibilitychange', sync);
  return () => {
    io.disconnect();
    document.removeEventListener('visibilitychange', sync);
  };
}

/** `watchLive` as state: true while the element behind `ref` is on screen in a visible tab. */
export function useOnScreen(ref: RefObject<Element | null>, rootMargin?: string) {
  const [live, setLive] = useState(false);
  useEffect(() => (ref.current ? watchLive(ref.current, setLive, rootMargin) : undefined), [ref, rootMargin]);
  return live;
}

/**
 * Seconds left on a landing clock: ticks down once a second while `ref` is on screen and `run` is true, pauses off
 * screen, in a hidden tab and while the visitor has paused animation, loops back to `from` after 0, and stays at `from`
 * under reduced motion.
 */
export function useCountdown(from: number, ref: RefObject<Element | null>, run = true) {
  const [left, setLeft] = useState(from);
  const stopped = usePaused();
  useEffect(() => setLeft(from), [from]);
  useEffect(() => {
    const el = ref.current;
    if (!el || !run || stopped || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    let timer: ReturnType<typeof setInterval> | undefined;
    const off = watchLive(el, (live) => {
      clearInterval(timer);
      if (live) timer = setInterval(() => setLeft((s) => (s > 0 ? s - 1 : from)), 1000);
    });
    return () => {
      off();
      clearInterval(timer);
    };
  }, [from, ref, run, stopped]);
  return left;
}

/** A clock that counts down from `from` seconds in real time while on screen (see useCountdown). Every landing clock. */
export function Countdown({ from, className, run }: { from: number; className?: string; run?: boolean }) {
  const ref = useRef<HTMLSpanElement>(null);
  const left = useCountdown(from, ref, run);
  return (
    <span ref={ref} className={cn('type-num', className)}>
      {clock(left)}
    </span>
  );
}

/**
 * The unknown band, "?.?": two pen-drawn question-mark hooks with one point between them on the baseline (the hooks
 * carry no points of their own), so it reads as a decimal band like 6.5 and never as three dots. Size it with
 * font-size: it is 0.8em tall. The landing never shows a band number.
 */
export function BandQ({ className }: { className?: string }) {
  const hook = 'M7 20C7 9 15 4 22 4c8 0 14 5 14 12 0 8-7 11-12 15-4 3-5 6-5 13';
  return (
    <span className={cn('lp-q', className)} aria-hidden>
      <svg viewBox="0 0 118 66">
        <path className="lp-q-hook" d={hook} />
        <circle className="lp-q-pt" cx="59" cy="58" r="5.5" />
        <path className="lp-q-hook" d={hook} transform="translate(76 0)" />
      </svg>
    </span>
  );
}

/**
 * The examiner's pen on a headline word: a red underline or loop drawn as the heading scrolls in (the hero's "fix." and
 * six act heads use it, so every headline is marked the way the product marks your essay). Decorative: the word keeps
 * its text. At rest it is always fully drawn.
 */
export function Pen({ kind = 'underline', children }: { kind?: 'underline' | 'loop'; children: ReactNode }) {
  return (
    <span className={cn('lp-pen', `lp-pen--${kind}`)}>
      {children}
      <svg aria-hidden className="lp-pen-s" viewBox="0 0 120 24" preserveAspectRatio="none">
        <path d={kind === 'loop' ? 'M60 3C96 2 118 9 114 14 108 21 40 23 12 16 -2 11 22 4 78 4' : 'M2 14C14 7 22 19 36 13S58 9 70 14 94 19 118 10'} />
      </svg>
    </span>
  );
}
