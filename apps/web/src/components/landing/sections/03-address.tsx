import { Play, Volume2 } from 'lucide-react';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { Pen, vars } from '../wire';
import './03-address.css';

/*
 * The Listening act: the exam, then the evaluation, drawn from the real screens (components/lr). Pinned, 4 steps, cards
 * overlapping like a desk:
 *   0  the exam sheet (Runner): a part playing once, no controls, answers typed into the note gaps, the navigator below.
 *   1  the result (Results hero) slides over it: a sample band 8.5, "Review your 2 mistakes", "Why you lost marks".
 *   2  the transcript review rises on top: answer pins on the scrubber (right round, wrong square, as Audio.tsx draws
 *      them), the playhead parks on Q11 and the transcript marks the words that held it.
 *   3  the playhead moves on to Q12, the second mistake.
 * Base CSS is step 3, the finished picture: phones, reduced motion and no JS show it.
 */

// Part 2, Questions 11 to 15, note completion. Two mistakes, each a real cause from packages/core lr-review.ts.
const NOTES = [
  { n: 11, before: 'First ferry leaves at', typed: '9.00', after: '', wrong: true },
  { n: 12, before: 'Departs from', typed: 'Peer', after: '3', wrong: true },
  { n: 13, before: 'Bikes go on the', typed: 'lower', after: 'deck', wrong: false },
  { n: 14, before: 'Return ticket', typed: '18', after: '', wrong: false, pre: '£' },
  { n: 15, before: 'Ask at the desk for a', typed: 'free', after: 'map', wrong: false },
] as const;

// Answer moments on the Part 2 scrubber (fraction of the recording). Q11 and Q12 wrong.
const PINS = [
  { n: 11, at: 0.3, wrong: true },
  { n: 12, at: 0.44, wrong: true },
  { n: 13, at: 0.58, wrong: false },
  { n: 14, at: 0.71, wrong: false },
  { n: 15, at: 0.85, wrong: false },
];

// Bar heights for the drawn waveforms: a fixed pseudo-random speech envelope, so every render draws the same shape.
const level = (i: number) => 0.25 + 0.75 * Math.abs(Math.sin(i * 1.7) * Math.cos(i * 0.43 + 1));

/** A waveform of `n` bars: grey for the whole recording, ink over the part already played (clipped by --p, 0..1). */
function Wave({ n, className }: { n: number; className: string }) {
  const bars = Array.from({ length: n }, (_, i) => <i key={i} style={vars({ '--h': level(i).toFixed(2) })} />);
  return (
    <span className={`lp-lx-wave ${className}`}>
      <span className="lp-lx-wbars">{bars}</span>
      <span className="lp-lx-wbars lp-lx-wplayed">{bars}</span>
    </span>
  );
}

function ExamSheet() {
  return (
    <div className="lp-card lp-lx-exam">
      <div className="lp-lx-bar">
        <span className="lp-lx-label">Listening</span>
        <span className="lp-lx-where">Part 2, Questions 11 to 15</span>
        <span className="lp-lx-clock">
          <span className="lp-lx-cap">Time left</span> 24:10
        </span>
      </div>
      <div className="lp-lx-rec">
        <Volume2 size={16} aria-hidden />
        <span className="lp-lx-rectext">Part 2 playing</span>
        <Wave n={56} className="lp-lx-recwave" />
        <span className="lp-lx-cap">Plays once</span>
      </div>
      <div className="lp-lx-notes">
        <p className="lp-lx-ntitle">The island ferry</p>
        <p className="lp-lx-ninstr">Complete the notes. Write ONE WORD AND/OR A NUMBER for each answer.</p>
        <ol className="lp-lx-nlist">
          {NOTES.map((r, i) => (
            <li key={r.n} className="lp-lx-nrow" style={vars({ '--i': i })}>
              <span>{r.before}</span>
              {'pre' in r && <span>{r.pre}</span>}
              <span className="lp-lx-gap" data-wrong={r.wrong || undefined}>
                <span className="lp-lx-gnum">{r.n}</span>
                <span className="lp-lx-typed">{r.typed}</span>
              </span>
              {r.after && <span>{r.after}</span>}
            </li>
          ))}
        </ol>
      </div>
      <div className="lp-lx-nav">
        <span className="lp-lx-part">
          <b>Part 1</b> 10/10
        </span>
        <span className="lp-lx-nums">
          {Array.from({ length: 10 }, (_, i) => (
            <i key={i} data-on={i < 5 || undefined} data-cur={i === 4 || undefined}>
              {11 + i}
            </i>
          ))}
        </span>
        <span className="lp-lx-part">
          <b>Part 3</b> 0/10
        </span>
        <span className="lp-lx-part lp-lx-part--wide">
          <b>Part 4</b> 0/10
        </span>
      </div>
    </div>
  );
}

function ResultCard() {
  return (
    <div className="lp-card lp-lx-result">
      <span className="lp-lx-cap">The island ferry · Listening</span>
      {/* A sample band, labelled as one: 38 of 40 is band 8.5 on the Listening scale (37 to 38). */}
      <div className="lp-lx-band">
        <span className="lp-lx-cap">
          Band <span className="lp-lx-sample">Sample</span>
        </span>
        <span className="lp-lx-bandnum">8.5</span>
        <span className="lp-lx-raw">38 of 40 correct</span>
      </div>
      <span className="lp-lx-btn">Review your 2 mistakes</span>
      <p className="lp-lx-h">Why you lost marks</p>
      <ul className="lp-lx-causes">
        <li>
          <span>Picked a distractor</span>
          <i className="lp-lx-qchip">11</i>
        </li>
        <li>
          <span>Right idea, lost the mark</span>
          <i className="lp-lx-qchip">12</i>
        </li>
      </ul>
    </div>
  );
}

function Review() {
  return (
    <div className="lp-card lp-lx-review">
      <div className="lp-lx-tabs">
        <span>Summary</span>
        <span>
          Answers <i className="lp-lx-count">2</i>
        </span>
        <span className="lp-lx-tab-on">Transcript</span>
      </div>
      <div className="lp-lx-player">
        <span className="lp-lx-play">
          <Play size={14} fill="currentColor" aria-hidden />
        </span>
        <span className="lp-lx-scrub">
          <Wave n={72} className="lp-lx-rail" />
          {PINS.map((p) => (
            <i key={p.n} className={`lp-lx-pin lp-lx-pin--${p.n}`} data-wrong={p.wrong || undefined} style={vars({ '--at': p.at })}>
              {p.n}
            </i>
          ))}
          <span className="lp-lx-head" />
        </span>
        <span className="lp-lx-time">
          <span className="lp-lx-t11">1:51</span>
          <span className="lp-lx-t12">2:43</span> / 6:10
        </span>
      </div>
      <div className="lp-lx-body">
        <div className="lp-lx-transcript">
          <p>Woman: What time does the first ferry leave?</p>
          <p className="lp-lx-line lp-lx-line--11">
            <i className="lp-lx-qtag">Q11</i>Man: At <mark>a quarter to nine</mark>, not nine as it says online.
          </p>
          <p className="lp-lx-line lp-lx-line--12">
            <i className="lp-lx-qtag">Q12</i>Man: You board from <mark>Pier</mark> 3, at the end of the harbour wall.
          </p>
          <p>Man: Bikes go on the lower deck, and cars on the top.</p>
        </div>
        <div className="lp-lx-details">
          <div className="lp-lx-detail lp-lx-detail--11">
            <p className="lp-lx-dq">Question 11 · Picked a distractor</p>
            <p>
              You wrote <s>9.00</s>. Answer <b>8.45</b>.
            </p>
            <p className="lp-lx-dwhy">"Nine" is said, then corrected. Wait for the time that matches the question.</p>
            <span className="lp-lx-pill">
              <Play size={12} fill="currentColor" aria-hidden /> Play from here
            </span>
          </div>
          <div className="lp-lx-detail lp-lx-detail--12">
            <p className="lp-lx-dq">Question 12 · Spelling slip</p>
            <p>
              You wrote <s>Peer</s>. Answer <b>Pier</b>.
            </p>
            <p className="lp-lx-dwhy">The right word, spelt wrongly, scores nothing. Type what you hear to learn it.</p>
            <span className="lp-lx-pills">
              <span className="lp-lx-pill">
                <Play size={12} fill="currentColor" aria-hidden /> Play from here
              </span>
              <span className="lp-lx-pill">Dictation</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

export function Address() {
  return (
    <section id="address" aria-labelledby="address-title" className="lp-address">
      <Stage steps={4} track="190svh" className="lp-track--mobile-flat lp-lx-track" play>
        <div className="lp-container lp-lx-frame">
          <SectionHead
            id="address-title"
            title={
              <>
                Hear it once. See <Pen>where</Pen> it was.
              </>
            }
            sub="Listening runs like the real test: one play, no rewind. Your result pins every answer to the moment it was said, and says why you lost it."
          />
          <p className="sr-only">
            Illustration: a Listening exam sheet for Part 2 with answers typed into the notes. The result slides over it: a sample band
            of 8.5, 38 of 40 correct, with two mistakes. The transcript review then marks the words that held each one: question 11, a distractor, and
            question 12, a spelling slip.
          </p>
          <div className="lp-lx-desk" aria-hidden="true">
            <ExamSheet />
            <ResultCard />
            <Review />
          </div>
        </div>
      </Stage>
    </section>
  );
}
