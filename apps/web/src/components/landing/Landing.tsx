import { useEffect, useRef } from 'react';
import './landing.css';
import { LandingHeader } from './LandingHeader';
import { Rail } from './Rail';
import { Hero } from './sections/01-hero';
import { Mock } from './sections/02-mock';
import { Address } from './sections/03-address';
import { Practice } from './sections/03b-practice';
import { Essay } from './sections/04-essay';
import { Task1 } from './sections/04b-task1';
import { Examiner } from './sections/05-examiner';
import { Result } from './sections/05b-result';
import { Habits } from './sections/06-habits';
import { Plan } from './sections/07-plan';
import { Bank } from './sections/07b-bank';
import { Pricing } from './sections/08-pricing';
import { Faq } from './sections/08b-faq';
import { Close } from './sections/08c-close';
import { Footer } from './sections/09-footer';
import { usePaused, watchLive } from './wire';

/**
 * Marks each act `data-live` while it is on screen (a screen of margin either side) and the tab is visible, so the
 * infinite loops in landing.css only run where someone can see them. The hero keeps its own observer (01-hero.tsx).
 */
function useLiveSections(root: React.RefObject<HTMLElement | null>) {
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const offs = [...el.querySelectorAll<HTMLElement>('main > section:not(#hero)')].map((s) => watchLive(s, (live) => s.toggleAttribute('data-live', live), '25% 0px'));
    return () => offs.forEach((off) => off());
  }, [root]);
}

const DRAWN = ':is(h2, h3, p, li, a, button, figure, img, .lp-card, [class*="-card"], [class*="-slot"])';
const GAP = 152; // px of paper between one act's last content and the next act's first, everywhere on the page

/** Layout Y of `el` from the offsetParent chain (transform-free), up to `stop` or the page. */
const layoutY = (el: HTMLElement | null, stop: Element | null = null) => {
  let y = 0;
  for (let n: Element | null = el; n && n !== stop; n = (n as HTMLElement).offsetParent) y += (n as HTMLElement).offsetTop;
  return y;
};

/**
 * Where an act's content starts (from its top, as it arrives) and ends (from its bottom, as it leaves), in layout px.
 * A pinned act is measured inside its sticky stage: at the start of the run for the top, at the release for the bottom.
 */
function extent(section: HTMLElement) {
  const sTop = layoutY(section);
  const stage = section.querySelector<HTMLElement>(':scope > .lp-track > .lp-stage');
  const pinned = !!stage && getComputedStyle(stage).position === 'sticky';
  // What is drawn, not the wrappers (a frame's grid can be taller than its cards): text, controls, cards and figures.
  const blocks = [...section.querySelectorAll<HTMLElement>(DRAWN)].filter((b) => b instanceof HTMLElement && b.offsetHeight > 4 && !b.closest('.sr-only'));
  if (!blocks.length) return null;
  let top = Infinity;
  let bottom = -Infinity;
  for (const b of blocks) {
    let y: number;
    if (pinned && stage!.contains(b)) {
      const track = stage!.parentElement!;
      const inStage = layoutY(b, stage);
      const trackTop = layoutY(track) - sTop;
      top = Math.min(top, trackTop + inStage);
      y = trackTop + track.offsetHeight - stage!.offsetHeight + inStage; // the stage sits at the track's end on release
    } else {
      y = layoutY(b) - sTop;
      top = Math.min(top, y);
    }
    bottom = Math.max(bottom, y + b.offsetHeight);
  }
  return { lead: top, trail: section.offsetHeight - bottom };
}

/**
 * Pinned acts centre their frame in a full screen, and acts pad themselves differently, so the paper between two acts
 * ran from 110px to 500px. This pulls each act up so every handoff leaves the same GAP: the next act arrives as soon as
 * the last one is done. Lazy-rendered acts (content-visibility) are laid out for the one synchronous measure.
 */
function useEvenHandoffs(root: React.RefObject<HTMLElement | null>) {
  useEffect(() => {
    const main = root.current;
    if (!main) return;
    let raf = 0;
    const fit = () => {
      const acts = [...main.querySelectorAll<HTMLElement>(':scope > section')];
      main.classList.add('lp-measuring');
      const ext = acts.map(extent);
      main.classList.remove('lp-measuring');
      acts.forEach((act, i) => {
        const a = ext[i - 1];
        const b = ext[i];
        // Never more than half a screen: a broken measure (a collapsed act) must not stack acts on top of each other.
        const ok = i && a && b && act.offsetHeight > 0 && acts[i - 1]!.offsetHeight > 0;
        const pull = ok ? Math.min(innerHeight / 2, Math.max(0, Math.round(a.trail + b.lead - GAP))) : 0;
        const next = pull ? `${-pull}px` : '';
        if (act.style.marginTop !== next) act.style.marginTop = next;
      });
    };
    const schedule = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(fit);
    };
    schedule();
    void document.fonts?.ready.then(schedule);
    const ro = new ResizeObserver(schedule);
    for (const s of main.querySelectorAll(':scope > section')) ro.observe(s);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, [root]);
}

/** The visitor landing page at '/': the product drawn as grey wireframes with real words, in acts. Outside the app shell. */
export function Landing() {
  const main = useRef<HTMLElement>(null);
  const paused = usePaused();
  useLiveSections(main);
  useEvenHandoffs(main);
  return (
    <div className="landing" data-paused={paused || undefined}>
      <LandingHeader />
      <main ref={main} id="main" tabIndex={-1}>
        <Hero />
        <Mock />
        <Address />
        <Practice />
        <Task1 />
        <Essay />
        <Examiner />
        <Result />
        <Habits />
        <Plan />
        <Pricing />
        <Bank />
        <Faq />
        <Close />
      </main>
      <Rail />
      <Footer />
    </div>
  );
}
