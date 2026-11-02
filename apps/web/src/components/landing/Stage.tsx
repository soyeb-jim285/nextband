import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Where `lp-track--mobile-flat` unpins in landing.css. A section that unpins wider in its own CSS passes its query as `flat`. */
export const MOBILE = '(max-width: 47.999rem)';
/**
 * The default `flat`: phones, and screens too short for a pinned 100dvh frame to hold its content (a 1366x650 laptop
 * with browser bars, 1080p at 200% zoom). landing.css unpins `lp-track--mobile-flat` at the same query.
 */
export const SHORT = `${MOBILE}, (max-height: 43.75rem)`;
const motionOk = () => typeof window !== 'undefined' && 'IntersectionObserver' in window && window.matchMedia('(prefers-reduced-motion: no-preference)').matches;

/**
 * The step of an unpinned (phone) act from where its picture is: 0 until the picture's top reaches the line 70% down
 * the screen, then one more step each time another 1/steps of its height passes that line. Reverses on the way back up.
 */
export const scrubStep = (top: number, height: number, viewport: number, steps: number) => {
  const passed = viewport * 0.7 - top;
  return passed < 0 ? 0 : Math.min(steps - 1, Math.floor(passed / Math.max(1, height / steps)) + 1);
};

/** Space-separated steps reached so far ("0 1 2" at step 2), for `[data-past~='2']` selectors: "has reached step 2". */
export const past = (step: number) => Array.from({ length: step + 1 }, (_, i) => i).join(' ');

type Mode = 'final' | 'pin' | 'play';
const modeOf = (el: HTMLElement | null, flatQuery: string, play: boolean, last: number): Mode => {
  if (!motionOk() || last === 0) return 'final';
  const flat = !!el?.classList.contains('lp-track--mobile-flat') && window.matchMedia(flatQuery).matches;
  return flat ? (play ? 'play' : 'final') : 'pin';
};

/**
 * A pinned, stepped act: a tall `.lp-track` (default 180svh, 0.8x under 768px) holding a sticky 100dvh `.lp-stage`.
 * `data-step` (and `data-past`, every step reached) on the stage goes 0..steps-1 as the track scrolls past (thresholds
 * at k/steps of the pin run), set by an IntersectionObserver on invisible sentinels: no scroll listener. Reduced motion
 * or no IntersectionObserver show the final step. A `lp-track--mobile-flat` track is unpinned under `flat`: it shows the
 * final step, or with `play` it steps with the scroll through its picture (`[data-lp-play]`, else the stage): scrubStep.
 * The track also defines `view-timeline: --lp-track` so anything inside can scrub with `animation-timeline: --lp-track`.
 * Children: nodes styled by `[data-step]` / `[data-past]` selectors, or a function of the step.
 */
export function Stage({ steps, track = '180svh', className, children, flat = SHORT, play = false }: { steps: number; track?: string; className?: string; children: ReactNode | ((step: number) => ReactNode); flat?: string; play?: boolean }) {
  const last = Math.max(0, steps - 1);
  const ref = useRef<HTMLDivElement>(null);
  // Decided before the first paint (the class is known from props), so a flat stage never flashes step 0.
  const [step, setStep] = useState(() => {
    if (typeof window === 'undefined') return last;
    const flatNow = !!className?.split(' ').includes('lp-track--mobile-flat') && window.matchMedia(flat).matches;
    return !motionOk() || last === 0 || (flatNow && !play) ? last : 0;
  });

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
    const narrow = window.matchMedia(flat);
    let io: IntersectionObserver | null = null;
    let off = () => {};

    const stop = () => {
      io?.disconnect();
      io = null;
      off();
      off = () => {};
    };
    const start = () => {
      stop();
      const mode = modeOf(el, flat, play, last);
      if (mode === 'final') return setStep(last);
      if (mode === 'play') {
        // Unpinned on a phone, the act is taller than the screen and its picture sits below the heading, so a timer
        // played the steps before the picture was in view. The steps follow the scroll through the picture instead.
        const vis = el.querySelector<HTMLElement>('[data-lp-play]') ?? el;
        let raf = 0;
        // The picture's offset in the track and its height are measured once (again on resize), then only the track's
        // position is read: a picture that grows or moves as it steps would otherwise flip the step back and forth.
        let box: { off: number; h: number } | null = null;
        const update = () => {
          raf = 0;
          const t = el.getBoundingClientRect().top;
          if (!box) {
            const r = vis.getBoundingClientRect();
            box = { off: r.top - t, h: r.height };
          }
          setStep(scrubStep(t + box.off, box.h, window.innerHeight, steps));
        };
        const onScroll = () => void (raf ||= requestAnimationFrame(update));
        const onResize = () => ((box = null), onScroll());
        window.addEventListener('scroll', onScroll, { passive: true });
        window.addEventListener('resize', onResize);
        off = () => {
          cancelAnimationFrame(raf);
          window.removeEventListener('scroll', onScroll);
          window.removeEventListener('resize', onResize);
        };
        // First measure after the first paint, not in the mount commit: a read there forces the whole page's first layout
        // inside React's task (~400 ms on a slow phone). The step starts at 0, which is right until the picture is reached.
        const first = setTimeout(onScroll, 0);
        const offScroll = off;
        off = () => (clearTimeout(first), offScroll());
        return;
      }
      const sentinels = [...el.querySelectorAll<HTMLElement>(':scope > .lp-sentinel')];
      // A sentinel counts once it has scrolled above the viewport top; the step is how many have. The root box is
      // everything above the viewport top (huge top margin, bottom pulled up by 100%), so "passed" is plain
      // isIntersecting and a jump (anchor, scroll restore, End key) still flips it: a 1px sentinel that leaps from
      // below the viewport to above it never intersects the viewport itself.
      const passed = new Map<Element, boolean>();
      io = new IntersectionObserver(
        (entries) => {
          for (const e of entries) passed.set(e.target, e.isIntersecting);
          let n = 0;
          for (const v of passed.values()) if (v) n++;
          setStep(Math.min(n, last));
        },
        { rootMargin: '1000000px 0px -100% 0px' },
      );
      for (const s of sentinels) io.observe(s);
    };

    start();
    reduce.addEventListener('change', start);
    narrow.addEventListener('change', start);
    return () => {
      stop();
      reduce.removeEventListener('change', start);
      narrow.removeEventListener('change', start);
    };
  }, [last, steps, flat, play]);

  return (
    <div ref={ref} className={cn('lp-track', className)} style={{ '--lp-track-h': track } as CSSProperties}>
      {Array.from({ length: last }, (_, i) => (
        // Sentinel k sits k/steps of the way through the pin run (track height minus one viewport).
        <span key={i} aria-hidden className="lp-sentinel" style={{ top: `calc((100% - 100dvh) * ${(i + 1) / steps})` }} />
      ))}
      <div className="lp-stage" data-step={step} data-past={past(step)}>
        {typeof children === 'function' ? children(step) : children}
      </div>
    </div>
  );
}
