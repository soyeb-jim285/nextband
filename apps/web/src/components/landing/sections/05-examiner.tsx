import { Link } from '@tanstack/react-router';
import { ArrowDown, Lightbulb, Mic } from 'lucide-react';
import { useRef } from 'react';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { clock, Pen, Tick, useCountdown, vars } from '../wire';
import './05-examiner.css';

/*
 * Act 5, the live Speaking test itself (its evaluation is the next act, 05b-result). Pinned, 5 steps driven by the step
 * number: the mic check, Part 1, Part 2 preparation (a real one-minute clock and notes), Part 2 talk (a real two-minute
 * clock, the answer transcribed live, a nudge) and Part 3. The examiner panel speaks each line.
 * Lines and answers are written for this page.
 */

const PARTS = ['Mic check', 'Part 1', 'Part 2', 'Part 3'];
const PART_OF = [0, 1, 2, 2, 3];
const SAYS = [
  'Before we start, let’s check your microphone. Please tell me your full name.',
  'Let’s talk about where you live. Do you live in a house or an apartment?',
  'Now I’m going to give you a topic. You have one minute to prepare, and you can make notes.',
  'All right? You have one to two minutes. Please start speaking now.',
  'You talked about a place you’d like to go back to. Why do you think people from big cities enjoy quiet places?',
];
const CUE = ['where it is', 'when you went there and who with', 'what you did there', 'and explain why you would like to go back.'];
const TALK = [
  'Okay, so the place I’d like to talk about is Sylhet, up in the north-east of Bangladesh.',
  'Last winter I go there with my cousins, and we stayed in a small guesthouse right next to the tea gardens.',
  'What I remember most is the mornings…',
];
const C = 2 * Math.PI * 26;

function Ring({ left, total, label }: { left: number; total: number; label: string }) {
  return (
    <span className="lp-sxe-ring">
      <svg viewBox="0 0 60 60" width="60" height="60" aria-hidden="true">
        <circle className="track" cx="30" cy="30" r="26" />
        <circle className="arc" cx="30" cy="30" r="26" strokeDasharray={C} style={{ strokeDashoffset: C - (C * left) / total }} />
      </svg>
      <span>
        <b className="lp-mono">{clock(left)}</b>
        <span>{label}</span>
      </span>
    </span>
  );
}

/** The candidate's side for one step. Clocks count in real seconds while their step is on screen. */
function Side({ step }: { step: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const prep = useCountdown(60, ref, step === 2);
  const talk = useCountdown(120, ref, step === 3);
  return (
    <div ref={ref} className="lp-sxe-side">
      {step === 0 && (
        <div className="lp-sxe-pane" key="0">
          <p className="lp-sxe-label">Say your full name</p>
          <p className="lp-sxe-you">
            My name is Nusrat Jahan.
            <i className="lp-sxe-caret" />
          </p>
          <span className="lp-sxe-meter">
            {Array.from({ length: 28 }, (_, i) => (
              <i key={i} style={vars({ '--i': i, '--h': (0.25 + 0.75 * Math.abs(Math.sin(i * 1.3) * Math.cos(i * 0.37))).toFixed(2) })} />
            ))}
          </span>
          <p className="lp-sxe-ok">
            <Tick size={14} strokeWidth={2} /> We can hear you clearly
          </p>
        </div>
      )}
      {step === 1 && (
        <div className="lp-sxe-pane" key="1">
          <p className="lp-sxe-label">Part 1 · Interview</p>
          <p className="lp-sxe-you">
            An apartment. We live on the fourth floor of a building in Chittagong, so it’s a bit noisy, but from the balcony you can see the port lights at night.
            <i className="lp-sxe-caret" />
          </p>
          <p className="lp-sxe-next">Follow-up: Is that a typical home where you live?</p>
        </div>
      )}
      {(step === 2 || step === 3) && (
        <div className="lp-sxe-pane" key="2">
          <div className="lp-sxe-cuehead">
            <div>
              <p className="lp-sxe-label">Part 2 · Long turn</p>
              <p className="lp-sxe-cuetitle">Describe a place you visited that you would like to go back to.</p>
            </div>
            {step === 2 ? <Ring left={prep} total={60} label="Prep" /> : <Ring left={talk} total={120} label="Talk" />}
          </div>
          {step === 2 ? (
            <div className="lp-sxe-prep">
              <div>
                <p className="lp-sxe-label">You should say:</p>
                <ul className="lp-sxe-cue">
                  {CUE.map((t) => (
                    <li key={t}>{t}</li>
                  ))}
                </ul>
              </div>
              <p className="lp-sxe-notes">
                <span className="lp-sxe-label">Your notes</span>
                <span style={vars({ '--i': 0 })}>Sylhet, NE · last winter, cousins</span>
                <span style={vars({ '--i': 1 })}>tea gardens, fog, hills at 6am</span>
                <span style={vars({ '--i': 2 })}>quiet ≠ Dhaka → go back</span>
              </p>
            </div>
          ) : (
            <div key="talk">
              <div className="lp-sxe-talk">
                {TALK.map((t, i) => (
                  <p key={t} style={vars({ '--i': i })}>
                    {t}
                  </p>
                ))}
              </div>
              <p className="lp-sxe-nudge">
                <Lightbulb size={14} aria-hidden /> Keep going. Add a reason or an example.
              </p>
            </div>
          )}
        </div>
      )}
      {step === 4 && (
        <div className="lp-sxe-pane" key="4">
          <p className="lp-sxe-label">Part 3 · Discussion</p>
          <p className="lp-sxe-you">
            I think mainly because cities are so crowded and loud. People want somewhere they can slow down and actually hear themselves think, even for a weekend.
          </p>
          <p className="lp-sxe-next">Follow-up: Is that also true for older people?</p>
          <a className="lp-sxe-done" href="#speaking-result" tabIndex={-1}>
            <span>
              <b>Test complete</b> <span className="lp-sxe-muted">13 min 48 s · 3 parts</span>
            </span>
            <span>
              Your result is ready <ArrowDown size={14} aria-hidden />
            </span>
          </a>
        </div>
      )}
    </div>
  );
}

export function Examiner() {
  return (
    <section id="examiner" aria-labelledby="examiner-title" className="lp-sxe">
      <Stage steps={5} track="230svh" className="lp-track--mobile-flat" play>
        {(step) => {
          const talking = step !== 3; // in the long turn the examiner listens
          return (
            <div className="lp-container lp-sxe-frame">
              <SectionHead
                id="examiner-title"
                title={
                  <>
                    An AI examiner who <Pen kind="loop">listens</Pen> and talks back.
                  </>
                }
                sub="A live Speaking test in all three parts, on the real timings. The examiner asks follow-up questions, keeps the clock and nudges you when you go quiet."
              />
              <p className="sr-only">
                Illustration: a live Speaking test. The examiner checks your microphone, asks a Part 1 question about where you live, gives a Part 2 cue card with one minute to prepare and make notes, listens for up
                to two minutes while your answer is transcribed and nudges you to add a reason, then asks a Part 3 discussion question. When the test is complete, your result is ready.
              </p>
              <div className="lp-card lp-sxe-room" aria-hidden="true" data-lp-play>
                <div className="lp-sxe-bar">
                  <b>Speaking</b>
                  <span className="lp-sxe-live">
                    <i /> Live
                  </span>
                  <ol className="lp-sxe-parts">
                    {PARTS.map((p, i) => (
                      <li key={p} data-on={PART_OF[step] === i || undefined} data-done={PART_OF[step]! > i || undefined}>
                        {PART_OF[step]! > i && <Tick size={12} strokeWidth={2} />}
                        {p}
                      </li>
                    ))}
                  </ol>
                </div>
                <div className="lp-sxe-body">
                  <div className="lp-sxe-examiner" data-talking={talking || undefined}>
                    <span className="lp-sxe-viz">
                      {[0.45, 0.75, 1, 0.7, 0.4].map((v, i) => (
                        <i key={i} style={vars({ '--v': v, '--i': i })} />
                      ))}
                    </span>
                    <p className="lp-sxe-state">{talking ? 'The examiner is speaking' : 'The examiner is listening'}</p>
                    <p key={step} className="lp-sxe-says">
                      {SAYS[step]}
                    </p>
                    <span className="lp-sxe-mic">
                      <Mic size={14} aria-hidden /> {talking ? 'Your turn next' : 'Your mic is on'}
                    </span>
                  </div>
                  <Side step={step} />
                </div>
              </div>
            </div>
          );
        }}
      </Stage>
      <div className="lp-container lp-act-cta lp-act-cta--centre lp-sxe-cta">
        <Link to="/speaking/live" className="lp-btn lp-btn--primary">
          Talk to the examiner free
        </Link>
        <Link to="/speaking" className="lp-link">
          or record one part at your own pace
        </Link>
      </div>
    </section>
  );
}
