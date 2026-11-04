import { Link } from '@tanstack/react-router';
import { useEffect, useRef, useState } from 'react';
import { rowLink, useGuestRecent } from '@/lib/guestRecent';
import { verb } from '../ContinueStrip';
import { bdt, PAID_PLANS } from '@ielts/core';
import { BandQ, Countdown, isPaused, PauseButton, StartFree, useOnScreen, vars, WireChip } from '../wire';
import './01-hero.css';
import { inPlayApp } from '@/lib/inApp';

/*
 * Act 1, the marking desk. Copy left; on the right one big answer sheet, marked, with the other three skills fanned above
 * it. The sheet works through the four skills in turn (Writing, Listening, Reading, Speaking), one every CYCLE_MS while the
 * hero is on screen: each is marked the same way (a struck mistake, a weak word, a missing word, each tagged in the
 * margin), four bars fill and the "?.?" lands on the Overall slip. The fan always shows the three skills not on the sheet
 * and springs up again on every change. Nothing empties or greys out between passes. 02-mock owns the four-skill reveal,
 * so the hero does not pin.
 */

type Row4 = readonly [string, string, string, string];
type MarkSet = { prompt: string; strike: Row4; under: Row4; ins: readonly [string, string, string]; rest: readonly [string, string]; fill: readonly number[] };
type Skill = 'writing' | 'listening' | 'reading' | 'speaking';
type Sheet = { chip: string; clock: number; tags: readonly [string, string, string]; criteria: readonly string[]; label: string; sets: readonly MarkSet[] };

// Every row is written for this page and short enough for one ruled row on a 375px phone. strike: before, wrong, fix,
// after. under: before, weak word, fix, after. ins: before, missing word, after. rest: two unmarked rows; the last types
// in. fill: bar proportions only, never a score.
const SHEETS: Record<Skill, Sheet> = {
  // Writing: the three slips Task 2 markers see most: subject-verb agreement, a wrong collocation, a dropped article.
  writing: {
    chip: 'Task 2',
    clock: 2400,
    tags: ['Agreement', 'Collocation', 'Article'],
    criteria: ['Task response', 'Coherence and cohesion', 'Lexical resource', 'Grammatical range and accuracy'],
    label: 'A Task 2 essay with a subject-verb agreement error, a wrong collocation and a missing article fixed in red, four criterion bars, and an overall band slip showing a question mark',
    sets: [
      {
        prompt: 'Should buses in big cities be free?',
        strike: ['In Dhaka, most people ', 'travels', 'travel', ' by bus.'],
        under: ['The government must ', 'do', 'take', ' action now.'],
        ins: ['Free buses would help ', 'the', 'environment.'],
        rest: ['Fewer cars would mean cleaner air.', 'So the city should pay for buses.'],
        fill: [0.58, 0.5, 0.44, 0.66],
      },
      {
        prompt: 'Is online learning as good as the classroom?',
        strike: ['Online learning ', 'have', 'has', ' many benefits.'],
        under: ['Students can ', 'make', 'take', ' courses at home.'],
        ins: ['However, ', 'a', 'teacher can answer questions.'],
        rest: ['Both ways of learning have a place.', 'Schools should offer a mix of both.'],
        fill: [0.62, 0.46, 0.52, 0.6],
      },
      {
        prompt: 'Should cities build more parks?',
        strike: ['Every city ', 'need', 'needs', ' more green space.'],
        under: ['Parks can ', 'do', 'make', ' a big difference.'],
        ins: ['Children can play in ', 'the', 'open air.'],
        rest: ['Doctors say green space lowers stress.', 'Councils should plant more trees.'],
        fill: [0.54, 0.58, 0.48, 0.7],
      },
    ],
  },
  // Listening, note completion: a homophone spelt wrong, the answer the speaker corrects (the classic distractor), and a
  // two-word answer cut to one.
  listening: {
    chip: 'Section 2',
    clock: 1800,
    tags: ['Spelling', 'Distractor', 'Missed word'],
    criteria: ['Section 1', 'Section 2', 'Section 3', 'Section 4'],
    label: 'Listening answers for an island ferry: a misspelt word, a distractor and a missed word fixed in red, four section bars, and an overall band slip showing a question mark',
    sets: [
      {
        prompt: 'The island ferry: complete the notes.',
        strike: ['Q5 Departs from ', 'Peer', 'Pier', ' 3'],
        under: ['Q6 Bikes are kept on the ', 'top', 'lower', ' deck'],
        ins: ['Q7 Park in the ', 'long-stay', 'car park'],
        rest: ['Q8 Last ferry back at 8.45 pm', 'Q9 Children under five travel free'],
        fill: [0.8, 0.62, 0.5, 0.36],
      },
    ],
  },
  // Reading: a statement the passage never makes, a detail misread, and a sentence-completion answer missing a word.
  reading: {
    chip: 'Passage 1',
    clock: 3600,
    tags: ['Not given', 'Detail', 'Missed word'],
    criteria: ['True, False, Not given', 'Sentence completion', 'Matching headings', 'Multiple choice'],
    label: 'Reading answers for a passage about otters: a not-given trap, a misread detail and a missed word fixed in red, four question-type bars, and an overall band slip showing a question mark',
    sets: [
      {
        prompt: 'Otters return to the Calder',
        strike: ['Q1 Otters vanished in the 1960s: ', 'True', 'Not given', ''],
        under: ['Q2 First pair seen at ', 'dawn', 'dusk', ' in March'],
        ins: ['Q3 The pollution came from ', 'paper', 'mills'],
        rest: ['Q4 Heading for paragraph C: iii', 'Q5 Survey season: spring'],
        fill: [0.46, 0.7, 0.56, 0.64],
      },
    ],
  },
  // Speaking Part 2: present perfect with a finished time ("last year"), a flat phrase where a precise word scores, and a
  // dropped article.
  speaking: {
    chip: 'Part 2',
    clock: 120,
    tags: ['Tense', 'Vocabulary', 'Article'],
    criteria: ['Fluency and coherence', 'Lexical resource', 'Grammatical range and accuracy', 'Pronunciation'],
    label: 'A Speaking Part 2 transcript with a tense mistake, a flat phrase and a missing article fixed in red, four criterion bars, and an overall band slip showing a question mark',
    sets: [
      {
        prompt: 'Describe a place you visited.',
        strike: ['I ', 'have visited', 'visited', ' Sylhet last year.'],
        under: ['The tea gardens were ', 'very nice', 'breathtaking', '.'],
        ins: ['We took ', 'a', 'boat along the river.'],
        rest: ['It was quiet and green everywhere.', 'I would love to go back one day.'],
        fill: [0.6, 0.52, 0.48, 0.66],
      },
    ],
  },
};
const ORDER: readonly Skill[] = ['writing', 'listening', 'reading', 'speaking'];
const SLOTS = ['l', 'r', 's'] as const;

const CYCLE_MS = 4500; // a marking pass (about 1s), a short read, then straight on to the next skill: no long hold
const PLAN = PAID_PLANS.month;

// The headline, split into words for the intro (line 2 waits a beat after line 1).
const LINES = [['Know', 'your', 'band.'], ['Know', 'what', 'to', 'fix.']];

function Marks({ set, tags }: { set: MarkSet; tags: Sheet['tags'] }) {
  const [s1, s2, s3, s4] = set.strike;
  const [u1, u2, u3, u4] = set.under;
  const [i1, i2, i3] = set.ins;
  const [r1, r2] = set.rest;
  return (
    <>
      <div className="lp-row" style={vars({ '--k': 0 })}>
        <span className="lp-mk-tag">{tags[0]}</span>
        <p className="lp-row-t">
          {s1}
          <span className="lp-mk lp-mk--strike">{s2}</span>
          <span className="lp-mk-fix">{s3}</span>
          {s4}
        </p>
      </div>
      <div className="lp-row" style={vars({ '--k': 1 })}>
        <span className="lp-mk-tag">{tags[1]}</span>
        <p className="lp-row-t">
          {u1}
          <span className="lp-mk lp-mk--under">
            {u2}
            <svg aria-hidden className="lp-mk-under" viewBox="0 0 28 6" preserveAspectRatio="none">
              <path d="M1 3 Q4.25 0 7.5 3 T14 3 T20.5 3 T27 3" />
            </svg>
          </span>
          <span className="lp-mk-fix">{u3}</span>
          {u4}
        </p>
      </div>
      <div className="lp-row" style={vars({ '--k': 2 })}>
        <span className="lp-mk-tag">{tags[2]}</span>
        <p className="lp-row-t">
          {i1}
          <span className="lp-mk-fix lp-mk-fix--ins">{i2}</span>
          {i3}
        </p>
      </div>
      <div className="lp-row lp-row--rest" style={vars({ '--k': 3 })}>
        <p className="lp-row-t">{r1}</p>
      </div>
      <div className="lp-row lp-row--rest lp-row--type" style={vars({ '--k': 4 })}>
        <p className="lp-row-t" style={vars({ '--n': r2.length })}>
          <span className="lp-type">{r2}</span>
          <span className="lp-type-caret" />
        </p>
      </div>
    </>
  );
}

const WAVE = [0.4, 0.75, 0.55, 0.95, 0.65, 0.35, 0.8, 0.5, 0.9, 0.6, 0.3, 0.7];

/** The small card for a skill that is not on the sheet right now (decorative, real content). */
function Mini({ skill }: { skill: Skill }) {
  if (skill === 'listening')
    return (
      <>
        <span className="lp-sis-cap">
          Listening
          <WireChip className="lp-sis-chip">Section 2</WireChip>
        </span>
        <span className="lp-sis-wave lp-sis-wave--l">
          {WAVE.map((h, i) => (
            <i key={i} style={vars({ '--h': h, '--d': `${i * 90}ms` })} />
          ))}
        </span>
        <p className="lp-sis-ans">
          Q8 Last ferry <span>8.45 pm</span>
        </p>
      </>
    );
  if (skill === 'reading')
    return (
      <>
        <span className="lp-sis-cap">
          Reading
          <WireChip className="lp-sis-chip">Passage 1</WireChip>
        </span>
        <p className="lp-sis-title">Otters return to the Calder</p>
        <p className="lp-sis-text">
          The first pair was seen <mark>at dusk in March</mark>
          <b>Q2</b>
        </p>
      </>
    );
  if (skill === 'speaking')
    return (
      <>
        <span className="lp-sis-cap">
          <i className="lp-sis-ring" />
          Speaking
          <WireChip className="lp-sis-chip">Part 2</WireChip>
        </span>
        <p className="lp-sis-title">Describe a place you visited.</p>
        <span className="lp-sis-wave lp-sis-wave--s">
          {WAVE.map((_, i) => (
            <i key={i} style={vars({ '--h': WAVE[(i * 5) % WAVE.length]!, '--d': `${i * 70}ms` })} />
          ))}
        </span>
      </>
    );
  return (
    <>
      <span className="lp-sis-cap">
        Writing
        <WireChip className="lp-sis-chip">Task 2</WireChip>
      </span>
      <p className="lp-sis-title">Should buses in big cities be free?</p>
      <p className="lp-sis-text">
        In Dhaka, most people <mark>travel</mark> by bus.
      </p>
    </>
  );
}

// The media strip under each sheet's header, so the skill reads at a glance. Listening: the recording with a moving
// playhead and a red tick where each marked question was heard. Speaking: the visitor's own answer, ticked where each
// slip was said. Reading: the passage line that holds the evidence. Writing: the word count against the 250 minimum.
const TRACK = Array.from({ length: 44 }, (_, i) => 0.25 + 0.75 * WAVE[(i * 7 + (i >> 2)) % WAVE.length]!);
const TICKS: Partial<Record<Skill, { x: number; t: string }[]>> = {
  listening: [
    { x: 0.2, t: 'Q5' },
    { x: 0.36, t: 'Q6' },
    { x: 0.52, t: 'Q7' },
  ],
  speaking: [
    { x: 0.14, t: '0:09' },
    { x: 0.4, t: '0:31' },
    { x: 0.58, t: '0:46' },
  ],
};

function Track({ skill }: { skill: 'listening' | 'speaking' }) {
  const bars = TRACK.map((h, i) => <i key={i} style={vars({ '--h': h })} />);
  return (
    <div className={`lp-audio lp-audio--${skill}`}>
      <span className="lp-audio-btn">
        {skill === 'listening' ? (
          <svg viewBox="0 0 12 12">
            <path d="M3.5 2.2v7.6L10 6z" />
          </svg>
        ) : (
          <i />
        )}
      </span>
      <span className="lp-audio-wave">
        <span className="lp-audio-bars">{bars}</span>
        <span className="lp-audio-bars lp-audio-played">{bars}</span>
        <span className="lp-audio-head" />
        {TICKS[skill]!.map((m) => (
          <b key={m.t} className="lp-audio-tick" style={vars({ '--x': m.x })}>
            {m.t}
          </b>
        ))}
      </span>
      <span className="lp-mono lp-audio-time">{skill === 'listening' ? '3:40 of 7:30' : '0:52 of 2:00'}</span>
    </div>
  );
}

function Media({ skill }: { skill: Skill }) {
  if (skill === 'listening' || skill === 'speaking') return <Track skill={skill} />;
  if (skill === 'reading')
    return (
      <p className="lp-media-text">
        <span className="lp-media-para">B</span>
        <span className="lp-media-line">
          ...and the first pair was seen <mark>at dusk in March</mark>, near the old mill weir.
        </span>
        <b className="lp-media-q">Q2</b>
      </p>
    );
  return (
    <div className="lp-wc">
      <span className="lp-wc-n">263 words</span>
      <span className="lp-wc-bar">
        <i style={vars({ '--f': 263 / 320 })} />
        <b style={vars({ '--x': 250 / 320 })} />
      </span>
      <span className="lp-wc-min">250 minimum</span>
    </div>
  );
}

export function Hero() {
  const hero = useRef<HTMLElement>(null);
  const latest = useGuestRecent()[0];
  const [cycle, setCycle] = useState(0);

  // The desk marks a new set every CYCLE_MS, only while the hero is on screen in a visible tab, motion is allowed and the
  // visitor has not paused it. data-live (on screen and visible) also runs the ambient floats, the voice bars and the caret.
  const live = useOnScreen(hero);
  useEffect(() => {
    hero.current?.toggleAttribute('data-live', live);
  }, [live]);
  useEffect(() => {
    if (!live) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
    const timer = window.setInterval(() => {
      if (!reduce.matches && !isPaused()) setCycle((c) => c + 1);
    }, CYCLE_MS);
    return () => window.clearInterval(timer);
  }, [live]);

  const skill = ORDER[cycle % ORDER.length]!;
  const sheet = SHEETS[skill];
  const set = sheet.sets[Math.floor(cycle / ORDER.length) % sheet.sets.length]!;
  const again = cycle ? ' is-again' : '';
  let w = 0;
  return (
    <section ref={hero} id="hero" aria-labelledby="hero-title" className="lp-hero">
      <div className="lp-container lp-hero-in">
        <div className="lp-hero-copy">
          <h1 id="hero-title" className="lp-hero-title">
            {LINES.map((line, l) => (
              <span key={l} className="lp-hero-line">
                {line.map((word, j) => (
                  <span key={j}>
                    {j > 0 && ' '}
                    <span className="lp-hero-w" style={vars({ '--i': w++, '--l': l })}>
                      {word}
                    </span>
                  </span>
                ))}
                {l === 0 && ' '}
              </span>
            ))}
          </h1>
          <p className="lp-hero-sub">Practise all four IELTS skills and see every mistake marked.</p>
          <div className="lp-hero-actions">
            {latest ? (
              <>
                <Link {...rowLink(latest)} className="lp-btn lp-btn--primary lp-hero-btn">
                  {verb(latest) === 'Resume' ? 'Resume your test' : 'See your result'}
                </Link>
                <Link to="/writing" className="lp-btn lp-btn--secondary lp-hero-btn">
                  Get an essay marked free
                </Link>
              </>
            ) : (
              <>
                <StartFree className="lp-hero-btn" />
                {!inPlayApp() && (
                  <a href="#pricing" className="lp-btn lp-btn--secondary lp-hero-btn">
                    See pricing
                  </a>
                )}
              </>
            )}
          </div>
          <ul role="list" className="lp-hero-proof">
            <li>Free to start</li>
            <li>
              <span className="sr-only">{PLAN.bdt} taka a month</span>
              <span aria-hidden>{bdt(PLAN.bdt)} a month if you stay</span>
            </li>
            <li>bKash, Nagad or card</li>
            <li>7-day refund</li>
          </ul>
          <div className="lp-hero-meta">
            {latest ? <p className="lp-hero-micro lp-hero-last">{latest.title}</p> : <p className="lp-hero-micro">Free account, no card.</p>}
            <PauseButton className="lp-hero-pause" label />
          </div>
        </div>

        <div className="lp-hero-desk">
          {/* The three skills not on the sheet, fanned above it with real content (decorative). Keyed by cycle so they spring up again on every change. */}
          <figure className="lp-hero-fan" aria-hidden>
            {ORDER.filter((k) => k !== skill).map((k, i) => (
              <div key={`${cycle}-${k}`} className={`lp-sis-pos lp-sis-pos--${SLOTS[i]}`}>
                <div className={`lp-card lp-sis lp-sis--${SLOTS[i]}`}>
                  <Mini skill={k} />
                </div>
              </div>
            ))}
          </figure>

          <div className="lp-sheet-par">
            <figure className="lp-card lp-sheet" role="img" aria-label={sheet.label}>
              <div className="lp-sheet-full" aria-hidden>
                <div className="lp-sheet-strip">
                  <WireChip key={`chip-${skill}`}>{sheet.chip}</WireChip>
                  <span key={cycle} className="lp-sheet-prompt">
                    {set.prompt}
                  </span>
                  <Countdown key={`clock-${skill}`} from={sheet.clock} className="lp-mono lp-sheet-clock" />
                </div>
                <div key={`media-${cycle}`} className="lp-media">
                  <Media skill={skill} />
                </div>
                <div className="lp-sheet-body">
                  <div key={cycle} className={'lp-marks' + again}>
                    <Marks set={set} tags={sheet.tags} />
                  </div>
                </div>
                <div className="lp-sheet-foot">
                  <div key={cycle} className="lp-sheet-crit">
                    {sheet.criteria.map((name, i) => (
                      <div key={name} className="lp-crit" style={vars({ '--k': i, '--f': set.fill[i]! })}>
                        <span className="lp-crit-name">{name}</span>
                        <span className="lp-crit-bar">
                          <i />
                        </span>
                      </div>
                    ))}
                  </div>
                  <div className="lp-slip">
                    <span className="type-caption lp-slip-cap">Overall band</span>
                    <span key={cycle} className={'lp-slip-q' + again}>
                      <BandQ />
                    </span>
                  </div>
                </div>
              </div>
            </figure>
          </div>
        </div>
      </div>
      <span className="lp-hero-cue" aria-hidden>
        <i />
      </span>
    </section>
  );
}
