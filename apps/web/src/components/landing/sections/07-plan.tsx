import { Link } from '@tanstack/react-router';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { useRef } from 'react';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { Pen, Tick, useCountdown, vars, WireChip, WirePill } from '../wire';
import './07-plan.css';

const order = (n: number) => vars({ '--order': n });
// Unpinned below 1024px wide (the head, the goal card and the report stack) or 700px tall.
const FLAT = '(max-width: 63.999rem), (max-height: 43.75rem)';

// Progress per skill against the target (Settings: at or above it, within one band, further below), drawn as trend lines
// with no numbers. y is 0 (low) to 1 (high); the dashed target sits at 0.7. Labels carry the meaning, not the grey.
const SKILLS = [
  { name: 'Listening', pts: [0.42, 0.5, 0.56, 0.66, 0.74], status: 'At target' },
  { name: 'Reading', pts: [0.4, 0.46, 0.44, 0.54, 0.6], status: 'Close' },
  { name: 'Writing', pts: [0.3, 0.34, 0.32, 0.4, 0.42], status: 'Below' },
  { name: 'Speaking', pts: [0.38, 0.44, 0.5, 0.52, 0.6], status: 'Close' },
] as const;
const TARGET = 0.7;
const y = (v: number) => (1 - (v - 0.2) / 0.65) * 28 + 2; // 0.2..0.85 fills the 32-unit box
const spark = (pts: readonly number[]) => pts.map((v, i) => `${(i / (pts.length - 1)) * 100},${y(v)}`).join(' ');
const EXAM_IN = 41 * 86400 + 6 * 3600 + 12 * 60 + 9; // seconds to a sample exam date
const two = (n: number) => String(n).padStart(2, '0');

/** The coach's goal controls in miniature: target band, a live countdown to the exam date, and four skill trends. */
function Goal() {
  const ref = useRef<HTMLDivElement>(null);
  const left = useCountdown(EXAM_IN, ref);
  const days = Math.floor(left / 86400);
  const rest = left % 86400;
  return (
    <div ref={ref} className="lp-card lp-plan-goal" aria-hidden="true">
      <div className="lp-plan-goal-top">
        <div className="lp-plan-goal-cell">
          <span className="lp-label">Target band</span>
          <b className="type-num lp-plan-goal-q">8.0</b>
        </div>
        <div className="lp-plan-goal-cell">
          <span className="lp-label">Exam in</span>
          <span className="lp-plan-goal-days">
            <b className="type-num">{days}</b> days
          </span>
          <span className="lp-mono lp-plan-goal-clock">
            {two(Math.floor(rest / 3600))}:{two(Math.floor((rest % 3600) / 60))}:{two(rest % 60)}
          </span>
        </div>
      </div>
      <ul className="lp-plan-skills">
        {SKILLS.map((k) => (
          <li key={k.name}>
            <span className="lp-plan-skill-name">{k.name}</span>
            <svg viewBox="0 0 100 32" preserveAspectRatio="none" className="lp-plan-spark">
              <line x1="0" x2="100" y1={y(TARGET)} y2={y(TARGET)} className="lp-plan-spark-target" vectorEffect="non-scaling-stroke" />
              <polyline points={spark(k.pts)} vectorEffect="non-scaling-stroke" />
            </svg>
            <span className="lp-plan-skill-status" data-s={k.status}>
              {k.status}
            </span>
          </li>
        ))}
      </ul>
      <p className="lp-plan-goal-key">Dashed line: your target. Each line: your bands so far.</p>
    </div>
  );
}

/** Act 7: the head and your goal on the left; the coach report on the right cites your own mistake, then racks focus down a dated week. */
export function Plan() {
  return (
    <section id="plan" className="lp-plan">
      <Stage steps={4} track="160svh" className="lp-plan-track lp-track--mobile-flat" flat={FLAT} play>
        <div className="lp-plan-tint" aria-hidden="true" />
        <div className="lp-container lp-plan-grid">
          <div className="lp-plan-side">
            <SectionHead id="plan-title" layout="stacked" title={<>Know exactly what to <Pen>fix this week</Pen>.</>} sub="A coach plan written only from your results, one action per focus, counting down to your exam. Free for everyone, even before you sign up." />
            <p className="sr-only">
              Your goal: a target band you set, the days left until your exam date, and a trend line for each skill against the target, marked at target, close (within one band) or below.
            </p>
            <Goal />
          </div>
          <figure className="lp-plan-frame" aria-labelledby="plan-cap">
            <figcaption id="plan-cap" className="sr-only">
              Example coach report. Where you are: Writing is your weakest skill right now, and article slips cost you marks in each of your last three essays. Focus one:
              Grammar: article, seen in your last 3 essays. Focus two: False chosen for Not Given in Reading. Focus three: long pauses mid-sentence in Speaking. Your week:
              Monday, write a Task 2 essay, 40 minutes. Wednesday, practise one speaking part, 5 minutes. Friday, take a Reading test, 60 minutes. Sunday, go through your
              review cards, 10 minutes.
            </figcaption>
            {/* ponytail: lp-card directly, since WireCard does not forward aria-hidden */}
            <div className="lp-card lp-plan-sheet" aria-hidden="true" data-lp-play>
              <div>
                <div className="lp-plan-head">
                  <p className="type-title-sm">Your coach</p>
                  <WireChip>This week</WireChip>
                </div>
                <p className="lp-plan-summary">
                  <b>Where you are:</b> Writing is your weakest skill right now. Article slips cost you marks in each of your last three essays.
                </p>
              </div>

              <div className="lp-plan-focus">
                <p className="type-subheading">This week’s focus</p>
                <ol className="lp-plan-items">
                  <li className="lp-plan-item">
                    <span className="lp-plan-disc lp-plan-disc--ink">1</span>
                    <div className="min-w-0">
                      <p className="type-subheading lp-plan-cite">Grammar: article</p>
                      <p className="lp-plan-sub">Seen in your last 3 essays</p>
                      {/* The evidence, docked under the focus it proves: two of your own corrections. */}
                      <div className="lp-plan-stack">
                        <WirePill className="lp-plan-pill">
                          <s>a</s> → <b>the</b>
                        </WirePill>
                        <WirePill className="lp-plan-pill">
                          <s>an university</s> → <b>a university</b>
                        </WirePill>
                      </div>
                      <div className="lp-plan-acts">
                        <WireChip className="lp-plan-evidence">See evidence</WireChip>
                        <WirePill className="lp-plan-btn">Write a Task 2 essay (40 min)</WirePill>
                      </div>
                    </div>
                  </li>
                  <li className="lp-plan-item">
                    <span className="lp-plan-disc">2</span>
                    <div className="lp-plan-lines">
                      <p className="type-subheading">False chosen for Not Given</p>
                      <p className="lp-plan-sub">Reading: take a Reading test (60 min)</p>
                    </div>
                  </li>
                  <li className="lp-plan-item">
                    <span className="lp-plan-disc">3</span>
                    <div className="lp-plan-lines">
                      <p className="type-subheading">Long pauses mid-sentence</p>
                      <p className="lp-plan-sub">Speaking: practise one speaking part (5 min)</p>
                    </div>
                  </li>
                </ol>
              </div>

              <div className="lp-plan-cheat">
                <div className="lp-plan-cheat-head">
                  <ChevronDown size={16} strokeWidth={1.75} aria-hidden />
                  <span className="lp-plan-cheat-label">Before your next test</span>
                  <span className="lp-plan-boxes">
                    {[0, 1, 2, 3, 4].map((i) => (
                      <span key={i} className="lp-plan-box">
                        <Tick size={10} strokeWidth={2} />
                      </span>
                    ))}
                  </span>
                </div>
                <p className="lp-plan-cheat-item">Check every singular noun has a, an or the.</p>
              </div>

              <div className="lp-plan-week">
                <p className="type-subheading">Your week</p>
                <div className="lp-plan-drum">
                  <ul className="lp-plan-list">
                    <li className="lp-plan-row lp-plan-row--a" style={order(0)}>
                      <span className="type-num font-semibold">Mon</span>
                      <span className="lp-plan-type type-body">Write a Task 2 essay</span>
                      <span className="type-caption type-num">40 min</span>
                      <ChevronRight size={16} aria-hidden />
                    </li>
                    <li className="lp-plan-row lp-plan-row--b" style={order(1)}>
                      <span className="type-num font-semibold">Wed</span>
                      <span className="lp-plan-type type-body">Practise one speaking part</span>
                      <span className="type-caption type-num">5 min</span>
                      <ChevronRight size={16} aria-hidden />
                    </li>
                    <li className="lp-plan-row lp-plan-row--c" style={order(2)}>
                      <span className="type-num font-semibold">Fri</span>
                      <span className="lp-plan-type type-body">Take a Reading test</span>
                      <span className="type-caption type-num">60 min</span>
                      <ChevronRight size={16} aria-hidden />
                    </li>
                    <li className="lp-plan-row lp-plan-row--d" style={order(3)}>
                      <span className="type-num font-semibold">Sun</span>
                      <span className="lp-plan-type type-body">Go through your review cards</span>
                      <span className="type-caption type-num">10 min</span>
                      <ChevronRight size={16} aria-hidden />
                    </li>
                  </ul>
                  <span className="lp-plan-edge lp-plan-edge--top" />
                  <span className="lp-plan-edge lp-plan-edge--bot" />
                </div>
              </div>
            </div>
          </figure>
        </div>
      </Stage>
      <div className="lp-container lp-act-cta lp-act-cta--centre">
        <Link to="/writing" className="lp-btn lp-btn--secondary">
          Get your first plan free
        </Link>
      </div>
    </section>
  );
}
