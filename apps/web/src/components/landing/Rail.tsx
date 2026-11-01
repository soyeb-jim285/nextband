import { ArrowUp } from 'lucide-react';
import { useEffect, useState } from 'react';

// Every act a visitor passes, in page order. Each is a dot; the one on screen is named beside it, the rest on hover.
const CELLS = [
  { id: 'mock', name: 'Full mock' },
  { id: 'address', name: 'Listening' },
  { id: 'practice', name: 'Reading' },
  { id: 'task1', name: 'Writing' },
  { id: 'examiner', name: 'Speaking' },
  { id: 'habits', name: 'Review' },
  { id: 'plan', name: 'Coach' },
  { id: 'pricing', name: 'Pricing' },
  { id: 'faq', name: 'Questions' },
] as const;
const WATCH = CELLS.map((c) => c.id);

/**
 * The page rail and the back-to-top button. The rail (from 1280px up; narrower, it would sit on the content) is a
 * vertical track with a stop per act: stops already passed are filled, the act on screen is in pen with its name beside
 * it, and the track fills down to it. It shows once the hero is behind. Each stop links to its act.
 * Back to top shows once the hero is behind, at every width.
 */
export function Rail() {
  const [passed, setPassed] = useState<ReadonlySet<string>>(new Set());
  useEffect(() => {
    if (!('IntersectionObserver' in window)) return;
    const seen = new Set<string>();
    // The root box is everything above the screen's middle, so "intersecting" means "its top has reached the middle".
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) e.isIntersecting ? seen.add(e.target.id) : seen.delete(e.target.id);
        setPassed(new Set(seen));
      },
      { rootMargin: '1000000px 0px -50% 0px' },
    );
    for (const id of WATCH) {
      const el = document.getElementById(id);
      if (el) io.observe(el);
    }
    return () => io.disconnect();
  }, []);
  const show = passed.has('mock');
  const at = CELLS.findLastIndex((c) => passed.has(c.id)); // the act on screen
  const top = () => window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  return (
    <>
      <nav aria-label="On this page" className="lp-rail" data-show={show || undefined} style={{ '--fill': Math.max(0, at) / (CELLS.length - 1) } as React.CSSProperties}>
        <ol>
          {CELLS.map((c, i) => (
            <li key={c.id} data-state={i < at ? 'done' : i === at ? 'on' : 'next'}>
              <a href={`#${c.id}`} aria-current={i === at ? 'true' : undefined}>
                <span className="lp-rail-name">{c.name}</span>
                <span className="lp-rail-dot" aria-hidden="true" />
              </a>
            </li>
          ))}
        </ol>
      </nav>
      <button type="button" className="lp-totop" data-show={show || undefined} onClick={top} aria-label="Back to top">
        <ArrowUp size={18} strokeWidth={2} aria-hidden />
      </button>
    </>
  );
}
