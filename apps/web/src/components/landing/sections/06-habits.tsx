import { ArrowRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { vars, WireCard } from '../wire';
import './06-habits.css';

/*
 * Act 6, "Your mistakes, sorted into habits." A magnet sort filmed as a focus pull: scattered correction chips (step 0)
 * drain into three piles (1), the Grammar: article pile stays sharp while the review card sharpens in (2), then the answer
 * comes into focus, Good fills and the day strip lands on +3 (3). The base CSS is the final state; steps only add motion.
 */

type Chip = { i: number; k: number; sx: number; sy: number; sr: number; far: 0 | 1; real: [string, string]; lift?: boolean };

// Three chips a pile. Step 0 throws every chip across all three columns (sx, sy in container units, sr in degrees), so
// the act opens on a heap of loose corrections that scrolling sorts into piles (owner: "more chaotic, organized by scroll").
const PILES: { label: string; cx: number; focus?: boolean; chips: Chip[] }[] = [
  {
    label: 'Vocabulary',
    cx: 0,
    chips: [
      { i: 1, k: 0, sx: 38, sy: 62, sr: -9, far: 0, real: ['informations', 'information'] },
      { i: 4, k: 1, sx: 14, sy: 6, sr: 7, far: 1, real: ['say me', 'tell me'] },
      { i: 7, k: 2, sx: 56, sy: 30, sr: 11, far: 0, real: ['much people', 'many people'] },
    ],
  },
  {
    label: 'Pronunciation',
    cx: 21.75,
    chips: [
      { i: 2, k: 0, sx: 2, sy: 44, sr: 10, far: 0, real: ['fink', 'think'] },
      { i: 5, k: 1, sx: 47, sy: 72, sr: -6, far: 1, real: ['wery', 'very'] },
      { i: 8, k: 2, sx: 30, sy: 2, sr: -12, far: 0, real: ['ship', 'sheep'] },
    ],
  },
  {
    label: 'Grammar: article',
    cx: 43.5,
    focus: true,
    chips: [
      { i: 0, k: 0, sx: 20, sy: 26, sr: -7, far: 0, real: ['a', 'the'], lift: true },
      { i: 3, k: 1, sx: 60, sy: 52, sr: 8, far: 1, real: ['an university', 'a university'] },
      { i: 6, k: 2, sx: 8, sy: 74, sr: -10, far: 0, real: ['the Dhaka', 'Dhaka'] },
    ],
  },
];

// The first-success intervals from packages/core/src/srs.ts (SM-2): Hard 1 day, Good 3, Easy 7.
const DAYS = [
  ['Hard', '+1 day'],
  ['Good', '+3 days'],
  ['Easy', '+7 days'],
] as const;

function ChipView({ c }: { c: Chip }) {
  const body: ReactNode = (
    <>
      <s>{c.real[0]}</s>
      <ArrowRight size={14} aria-hidden className="lp-habits-arrow" />
      <b>{c.real[1]}</b>
    </>
  );
  return (
    <span className={cn('lp-habits-chip', c.lift && 'is-lift')} style={vars({ '--i': c.i, '--k': c.k, '--sx': c.sx, '--sy': c.sy, '--sr': c.sr, '--far': c.far, '--tilt': c.k % 2 ? -1.5 : 1.5 })}>
      {body}
    </span>
  );
}

export function Habits() {
  return (
    <section id="habits" className="lp-habits">
      <Stage steps={4} track="240svh" className="lp-habits-track lp-track--mobile-flat" flat="(max-width: 63.999rem), (max-height: 43.75rem)">
        <div className="lp-container lp-habits-inner">
          <div className="lp-habits-head">
            <SectionHead id="habits-title" layout="split" title="Your mistakes, sorted into habits." sub="Corrections from every test are grouped, then return as review cards." />
          </div>
          <p className="sr-only">
            For example, the correction from a to the is filed under Grammar: article with your other article slips. It comes back as a review card: It was a best day of my
            trip. Answer: It was the best day of my trip. Grade it Good and it returns in 3 days.
          </p>
          <div className="lp-habits-visual" aria-hidden="true">
            <div className="lp-habits-piles">
              {PILES.map((p, order) => (
                <div key={p.label} className={cn('lp-habits-stack', p.focus && 'is-focus')} style={vars({ '--cx': p.cx, '--order': order })}>
                  <span className="lp-habits-label">
                    {p.label}
                    <span className="lp-habits-count">{p.chips.length}</span>
                  </span>
                  <span className="lp-habits-rule" />
                  <div className="lp-habits-deck">
                    {p.chips.map((c) => (
                      <ChipView key={c.i} c={c} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
            <span className="lp-habits-link" />
            <div className="lp-habits-review">
              <WireCard className="lp-habits-card">
                <div className="lp-habits-prompt">
                  <span className="type-caption">Grammar: article</span>
                  <span className="type-caption lp-habits-ask">Fix the sentence.</span>
                </div>
                <p className="lp-habits-q">
                  It was <u>a</u> best day of my trip.
                </p>
                <div className="lp-habits-a">
                  <p className="lp-habits-q">
                    It was <b>the</b> best day of my trip.
                  </p>
                  <p className="lp-habits-why">Why: there is only one best day, so it takes the.</p>
                </div>
                <div className="lp-habits-foot">
                  <span className="lp-habits-show">Show answer</span>
                  <span className="lp-habits-grades">
                    <span>Again</span>
                    <span>Hard</span>
                    <span className="is-good">Good</span>
                    <span>Easy</span>
                  </span>
                </div>
              </WireCard>
              <div className="lp-habits-days">
                <div className="lp-habits-strip">
                  {DAYS.map(([g, d], n) => (
                    <span key={g} className={cn('lp-habits-cell', n === 1 && 'is-on')}>
                      <span className="lp-habits-cell-g">{g}</span>
                      {d}
                    </span>
                  ))}
                </div>
              </div>
              <p className="lp-habits-when">Back in 3 days</p>
            </div>
          </div>
        </div>
      </Stage>
    </section>
  );
}
