import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { ArrowRight, BookmarkPlus } from 'lucide-react';
import { Link } from '@tanstack/react-router';
import { SectionHead } from './SectionHead';
import { Stage } from './Stage';
import { vars } from './wire';
import './writing-desk.css';

/*
 * The Writing desk, shared by the Task 1 and Task 2 acts and drawn from the real screens (WritingExam, the result's
 * Overview, Essay and Improve tabs). Pinned, 6 steps:
 *   0  the exam: the task on the left, the answer written in on the right (scrubbed), the clock and word count.
 *   1  marked: the Overview card slides in (a sample band and the four criteria); every mistake is underlined.
 *   2-4  the Essay tab walks the mistakes: the paper scrolls to each one, it lights up with its type, the card explains it.
 *   5  Improve: the improved version rises over the desk, the whole answer rewritten with what changed marked; it scrolls.
 * Base CSS is step 5. Category labels are what lib/result.ts categoryLabel gives.
 */

/** A run of the answer: plain text, a focused mistake (k = 1..3, walked in order) or an extra one (k = 0, underlined only). */
export type Seg = string | { k: number; text: string; type: string };
/** A change in the improved version: the new words, what they replace (none when added) and why, shown on hover. */
export type Change = { ins: string; was?: string; why: string };
export type Mistake = { type: string; criterion: string; wrote: string; fix: string; why: string };
export type DeskProps = {
  id: string;
  title: ReactNode;
  sub: string;
  srText: string;
  task: string; // "Task 1 Academic"
  clock: string; // time left as shown
  split: string; // grid columns of the task panel and the answer
  left: ReactNode;
  paras: Seg[][];
  marks: number; // mistakes marked in all (focused + extra)
  band: string;
  criteria: [string, string][];
  mistakes: [Mistake, Mistake, Mistake];
  better: { lead: string; paras: (string | Change)[][] }; // the whole answer, improved
  cta: { to: '/writing'; label: string };
};

const words = (paras: Seg[][]) =>
  paras
    .flat()
    .map((s) => (typeof s === 'string' ? s : s.text))
    .join(' ')
    .split(/\s+/)
    .filter(Boolean).length;

/** The answer pane. On each mistake step it scrolls the text so the focused mistake sits a third of the way down. */
function Paper({ step, paras }: { step: number; paras: Seg[][] }) {
  const win = useRef<HTMLDivElement>(null);
  const text = useRef<HTMLDivElement>(null);
  const focus = step >= 2 ? Math.min(step - 1, 3) : 0;
  useLayoutEffect(() => {
    const w = win.current;
    const t = text.current;
    if (!w || !t) return;
    const el = focus ? t.querySelector<HTMLElement>(`.lp-wx-mk--${focus}`) : null;
    const max = t.scrollHeight - w.clientHeight;
    const y = el && max > 0 ? Math.min(max, Math.max(0, el.offsetTop - w.clientHeight * 0.35)) : 0;
    t.style.transform = y ? `translateY(${-y}px)` : '';
  }, [focus]);
  return (
    <div ref={win} className="lp-wx-win">
      <div ref={text} className="lp-wx-text">
        {paras.map((p, i) => (
          <p key={i} style={vars({ '--i': i })}>
            {p.map((s, j) =>
              typeof s === 'string' ? (
                s
              ) : (
                <mark key={j} className={`lp-wx-mk lp-wx-mk--${s.k}`} data-type={s.type}>
                  {s.text}
                </mark>
              ),
            )}
          </p>
        ))}
      </div>
    </div>
  );
}

type Tip = Change & { x: number; y: number };

/** The Improve card. Hovering a change shows what it replaced and why, in a tip placed on the card (the text scrolls, the tip must not be clipped). */
export function Better({ marks, better, tab, className = 'lp-card lp-wx-better' }: { marks: number; better: DeskProps['better']; tab?: string; className?: string }) {
  const card = useRef<HTMLDivElement>(null);
  const [tip, setTip] = useState<Tip | null>(null);
  const show = (c: Change, el: HTMLElement) => {
    const box = card.current!.getBoundingClientRect();
    const r = el.getBoundingClientRect();
    const half = Math.min(140, box.width / 2); // keep the tip inside the card
    setTip({ ...c, x: Math.min(box.width - half, Math.max(half, r.left - box.left + r.width / 2)), y: r.top - box.top });
  };
  const n = words(better.paras.map((q) => q.map((s) => (typeof s === 'string' ? s : s.ins))));
  return (
    <div ref={card} className={className}>
      {tab && (
      <span className="lp-wx-tabs">
        <span>Overview</span>
        <span>
          {tab} <i>{marks}</i>
        </span>
        <b>Improve</b>
      </span>
      )}
      <p className="lp-wx-bhead">
        Improved version <span className="lp-wx-muted">{n} words</span>
      </p>
      <p className="lp-wx-muted lp-wx-blead">{better.lead} Point at a change to see why.</p>
      {/* The whole answer: it scrolls inside the card. */}
      <div className="lp-wx-bwin" onScroll={() => setTip(null)}>
        {better.paras.map((q, i) => (
          <p key={i} className="lp-wx-btext">
            {q.map((s, j) =>
              typeof s === 'string' ? (
                s
              ) : (
                <ins key={j} onPointerEnter={(e) => show(s, e.currentTarget)} onPointerLeave={() => setTip(null)}>
                  {s.ins}
                </ins>
              ),
            )}
          </p>
        ))}
      </div>
      {tip && (
        <span className="lp-wx-tip" style={{ left: tip.x, top: tip.y }}>
          <span className="lp-wx-tipwas">{tip.was ? <><del>{tip.was}</del> → {tip.ins}</> : <>Added: {tip.ins}</>}</span>
          {tip.why}
        </span>
      )}
    </div>
  );
}

export function WritingDesk(p: DeskProps) {
  const n = words(p.paras);
  const tab = 'Essay';
  return (
    <section id={p.id} aria-labelledby={`${p.id}-title`} className="lp-wx">
      <Stage steps={6} track="280svh" className="lp-track--mobile-flat" play>
        {(step) => (
          <div className="lp-container lp-wx-frame">
            <SectionHead id={`${p.id}-title`} title={p.title} sub={p.sub} />
            <p className="sr-only">{p.srText}</p>
            <div className="lp-wx-desk" aria-hidden="true">
              <div className="lp-card lp-wx-exam" style={vars({ '--split': p.split })}>
                <div className="lp-wx-bar">
                  <b>Writing</b>
                  <span className="lp-wx-muted">{p.task}</span>
                  <span className="lp-wx-clock">
                    <span className="lp-wx-muted">Time left</span> {p.clock}
                  </span>
                </div>
                <div className="lp-wx-split">
                  <div className="lp-wx-task">{p.left}</div>
                  <Paper step={step} paras={p.paras} />
                </div>
                <div className="lp-wx-foot">
                  <span className="lp-wx-muted">Draft saved</span>
                  <span className="lp-wx-count">{n} words</span>
                </div>
              </div>

              <div className="lp-card lp-wx-score">
                <span className="lp-wx-tabs">
                  <b>Overview</b>
                  <span>
                    {tab} <i>{p.marks}</i>
                  </span>
                  <span>Improve</span>
                </span>
                <div className="lp-wx-band">
                  <span className="lp-wx-muted">
                    {p.task} band <span className="lp-wx-sample">Sample</span>
                  </span>
                  <span className="lp-wx-bandnum">{p.band}</span>
                </div>
                <ul className="lp-wx-crit">
                  {p.criteria.map(([label, b]) => (
                    <li key={label} style={vars({ '--f': (Number(b) / 9).toFixed(3) })}>
                      <span>{label}</span>
                      <b>{b}</b>
                      <i />
                    </li>
                  ))}
                </ul>
              </div>

              <div className="lp-card lp-wx-detail">
                {p.mistakes.map((m, i) => (
                  <div key={m.type} className={`lp-wx-m lp-wx-m--${i + 1}`}>
                    <span className="lp-wx-mhead">
                      <span className="lp-wx-type">{m.type}</span>
                      <span className="lp-wx-muted">
                        {i + 1} of {p.marks}
                      </span>
                    </span>
                    <span className="lp-wx-fix">
                      <del>{m.wrote}</del>
                      <ArrowRight size={14} aria-hidden />
                      <ins>{m.fix}</ins>
                    </span>
                    <span className="lp-wx-why">{m.why}</span>
                    <span className="lp-wx-mfoot">
                      <span className="lp-wx-muted">Counts in {m.criterion}</span>
                      <span className="lp-wx-pill">
                        <BookmarkPlus size={13} aria-hidden /> Add to review deck
                      </span>
                    </span>
                  </div>
                ))}
              </div>

              <Better marks={p.marks} better={p.better} tab={tab} />
            </div>
          </div>
        )}
      </Stage>
      <div className="lp-container lp-act-cta lp-wx-cta">
        <Link to={p.cta.to} className="lp-btn lp-btn--primary">
          {p.cta.label}
        </Link>
      </div>
    </section>
  );
}
