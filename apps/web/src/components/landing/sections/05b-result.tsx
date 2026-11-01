import { Link } from '@tanstack/react-router';
import { ArrowRight, BookmarkPlus, Play } from 'lucide-react';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { Better, type Change } from '../WritingDesk';
import { Pen, vars } from '../wire';
import './05b-result.css';

/*
 * Act 5b, the Speaking evaluation: the result of the test in act 5, drawn from the real result screen
 * (routes/_app/speaking/result: Overview, Transcript, Fluency, Language, Pronunciation, Improve). Pinned, 6 steps, one per
 * tab; the recording's timeline stays on top and its playhead walks to what each tab is about.
 * Marks follow the real transcript (speaking/timeline.tsx): grammar solid, vocabulary dashed, pronunciation dotted,
 * fluency wavy, with the same shapes on the timeline. Phones show every tab stacked. The answer is written for this page.
 */

type Kind = 'g' | 'v' | 'p' | 'f';
type Tok = string | { m: Kind; text: string } | { pause: string };
const KIND: Record<Kind, string> = { g: 'Grammar', v: 'Vocabulary', p: 'Pronunciation', f: 'Fluency' };
const SHAPE: Record<Kind, string> = { g: 'circle', v: 'square', p: 'triangle', f: 'diamond' };

const TRANSCRIPT: Tok[][] = [
  [
    'Okay, so the place I’d like to talk about is Sylhet, up in the north-east of Bangladesh. Last winter I ',
    { m: 'g', text: 'go' },
    ' there with my cousins for ',
    { m: 'p', text: 'three' },
    ' days, ',
    { pause: '0.9s' },
    ' and we stayed in a small guesthouse right next to the tea gardens.',
  ],
  [
    'What I remember most is the mornings. We used to get up really early and walk up the hills before the fog lifted, and the view from the top was ',
    { m: 'v', text: 'very very beautiful' },
    '. You could see the whole valley and ',
    { m: 'f', text: 'the, the, um' },
    ' ',
    { pause: '1.4s' },
    ' river going ',
    { m: 'p', text: 'through' },
    ' it.',
  ],
  [
    'I ',
    { m: 'p', text: 'think' },
    ' I liked it so much because it was so quiet compared to Dhaka. No traffic, no horns, just birds and people picking tea. Honestly, if I ',
    { m: 'g', text: 'have' },
    ' the time, I would go back tomorrow.',
  ],
];

const TABS = ['Overview', 'Transcript', 'Fluency', 'Language', 'Pronunciation', 'Improve'];

// The recording: bars (0..1), flat where the two pauses are; pins where each mark was said (share of the answer).
const WAVE = Array.from({ length: 96 }, (_, i) =>
  (i >= 21 && i < 23) || (i >= 48 && i < 52) ? 0.05 : 0.26 + 0.52 * Math.abs(Math.sin(i * 1.7) * Math.cos(i * 0.43)) + 0.12 * Math.abs(Math.sin(i * 5.1)),
);
const PINS: { k: Kind; at: number }[] = [
  { k: 'g', at: 0.13 },
  { k: 'p', at: 0.19 },
  { k: 'v', at: 0.41 },
  { k: 'f', at: 0.48 },
  { k: 'p', at: 0.56 },
  { k: 'p', at: 0.62 },
  { k: 'g', at: 0.88 },
];
// What each tab looks at: the playhead's place and the kind of pin lit (none = all).
const FOCUS: { at: number; k?: Kind }[] = [{ at: 0 }, { at: 0.5 }, { at: 0.5, k: 'f' }, { at: 0.13, k: 'g' }, { at: 0.62, k: 'p' }, { at: 1 }];

function Shape({ k, size = 10 }: { k: Kind; size?: number }) {
  const s = SHAPE[k];
  return (
    <svg viewBox="0 0 10 10" width={size} height={size} aria-hidden="true" className="lp-sxr-shape">
      {s === 'circle' && <circle cx="5" cy="5" r="4" />}
      {s === 'square' && <rect x="1.5" y="1.5" width="7" height="7" />}
      {s === 'diamond' && <polygon points="5,0.5 9.5,5 5,9.5 0.5,5" />}
      {s === 'triangle' && <polygon points="5,1 9.5,9 0.5,9" />}
    </svg>
  );
}

function Timeline({ step }: { step: number }) {
  const f = FOCUS[step]!;
  return (
    <div className="lp-sxr-timeline">
      <span className="lp-sxr-play">
        <Play size={12} strokeWidth={2} aria-hidden />
      </span>
      <span className="lp-sxr-wave" style={vars({ '--at': f.at })}>
        <span className="lp-sxr-bars">
          {WAVE.map((h, i) => (
            <i key={i} style={vars({ '--h': h.toFixed(2) })} />
          ))}
        </span>
        {PINS.map((p, i) => (
          <span key={i} className="lp-sxr-pin" data-lit={f.k === p.k || undefined} data-dim={(f.k && f.k !== p.k) || undefined} style={vars({ '--x': p.at })}>
            <Shape k={p.k} />
          </span>
        ))}
        <i className="lp-sxr-head" />
      </span>
      <span className="lp-sxr-time lp-mono">1:06</span>
    </div>
  );
}

function Transcript({ only }: { only?: Kind }) {
  return (
    <div className="lp-sxr-text">
      {TRANSCRIPT.map((p, i) => (
        <p key={i}>
          {p.map((t, j) =>
            typeof t === 'string' ? (
              t
            ) : 'pause' in t ? (
              <span key={j} className="lp-sxr-pause">
                {t.pause}
              </span>
            ) : (
              <span key={j} className={`lp-sxr-mk lp-sxr-mk--${t.m}`} data-dim={(only && only !== t.m) || undefined}>
                {t.text}
              </span>
            ),
          )}
        </p>
      ))}
    </div>
  );
}

// Pace: words per minute, every 5 seconds; the dips are the two pauses. Band 7 speakers sit around 120-160.
const PACE = [118, 131, 138, 92, 124, 136, 142, 71, 112, 130, 141, 128, 135, 139, 130];
const px = (i: number) => 30 + i * (330 / (PACE.length - 1));
const py = (w: number) => 112 - ((w - 60) / 120) * 100;

function PaceChart() {
  return (
    <svg className="lp-sxr-pace" viewBox="0 0 380 132" role="presentation">
      <rect className="band" x="30" width="330" y={py(160)} height={py(120) - py(160)} />
      <text className="note" x="356" y={py(160) + 11} textAnchor="end">
        Typical band 7
      </text>
      {[60, 120, 180].map((w) => (
        <g key={w}>
          <line className="grid" x1="30" x2="360" y1={py(w)} y2={py(w)} />
          <text className="tick" x="24" y={py(w) + 3} textAnchor="end">
            {w}
          </text>
        </g>
      ))}
      <path className="line" pathLength={1} d={PACE.map((w, i) => `${i ? 'L' : 'M'}${px(i).toFixed(1)} ${py(w).toFixed(1)}`).join(' ')} />
      {[
        [3, '0.9s'],
        [7, '1.4s'],
      ].map(([i, s]) => (
        <g key={s} className="pause">
          <line x1={px(i as number)} x2={px(i as number)} y1={py(180)} y2="112" />
          <text x={px(i as number)} y={py(180) - 3} textAnchor="middle">
            Pause {s}
          </text>
        </g>
      ))}
      <text className="tick" x="30" y="128">
        0:00
      </text>
      <text className="tick" x="360" y="128" textAnchor="end">
        1:06
      </text>
    </svg>
  );
}

const MEASURES = [
  ['Speech rate', '118 wpm', 'Watch'],
  ['Longest run', '19 words', 'On target'],
  ['Long pauses', '2', 'Work on this'],
  ['Mid-clause pauses', '1', 'Watch'],
  ['Fillers per minute', '2.7', 'Watch'],
];
const BETTER: (string | Change)[][] = [
  [
    'Okay, so the place I’d like to talk about is Sylhet, up in the north-east of Bangladesh. Last winter I ',
    { ins: 'went', was: 'go', why: 'Past tense: “last winter” is finished time.' },
    ' there with my cousins for three days, and we stayed in a small guesthouse right ',
    { ins: 'on the edge of', was: 'next to', why: 'More exact, and it paints the place for the examiner.' },
    ' the tea gardens.',
  ],
  [
    'What ',
    { ins: 'stayed with me', was: 'I remember', why: 'A natural phrase that shows range without sounding learnt.' },
    ' most ',
    { ins: 'was', was: 'is', why: 'Keeps the story in the past, like the rest of the answer.' },
    ' the mornings. We used to get up ',
    { ins: 'at first light', was: 'really early', why: 'A precise, vivid time phrase.' },
    ' and walk up the hills before the fog lifted, and the view from the top was ',
    { ins: 'breathtaking', was: 'very very beautiful', why: 'One precise word instead of a repeated intensifier.' },
    '. You could see the whole valley, ',
    { ins: 'with a river winding through it', was: 'and the, the, um… river going through it', why: 'No repeat, filler or pause, and “winding” is the word a high band uses.' },
    '.',
  ],
  [
    'I think ',
    { ins: 'the main reason I loved it was how quiet it was', was: 'I liked it so much because it was so quiet', why: 'Signposts the reason, which the card asks you to explain.' },
    ' compared to Dhaka. No traffic, no horns, just birds and people picking tea. ',
    { ins: 'It’s the kind of place that makes you slow down.', why: 'Rounds off the reason with your own view.' },
    ' Honestly, if I ',
    { ins: 'had', was: 'have', why: 'Second conditional: “if I had…, I would…”.' },
    ' the time, I would go back tomorrow.',
  ],
];

export function Result() {
  return (
    <section id="speaking-result" aria-labelledby="speaking-result-title" className="lp-sxr">
      <Stage steps={6} track="300svh" className="lp-track--mobile-flat">
        {(step) => (
          <div className="lp-container lp-sxr-frame">
            <SectionHead
              id="speaking-result-title"
              title={
                <>
                  Every pause, slip and sound, <Pen>on your recording</Pen>.
                </>
              }
              sub="Your result marks the transcript word by word, charts your pace and pauses, finds the sounds you swap and hands your answer back improved."
            />
            <p className="sr-only">
              Illustration: a Speaking result with a sample band of 6.5 over the recording’s timeline. The transcript marks two grammar slips (go for went, have for had), a repeated intensifier, a repeat with a filler and a 1.4
              second pause, and three words where /θ/ was said as /t/ (three, through, think). The Fluency tab charts words per minute against the typical band 7 range, the Language tab lists mistakes by type and
              vocabulary upgrades, the Pronunciation tab shows the sound you mix up most, and the Improve tab rewrites the whole answer.
            </p>
            <div className="lp-card lp-sxr-card" aria-hidden="true">
              <div className="lp-sxr-tabs">
                {TABS.map((t, i) => (
                  <span key={t} data-on={step === i || undefined}>
                    {t}
                  </span>
                ))}
              </div>
              <Timeline step={step} />
              <div className="lp-sxr-bodies">
                {/* Overview */}
                <div className="lp-sxr-body lp-sxr-ov" data-on={step === 0 || undefined}>
                  <p className="lp-sxr-h">Overview</p>
                  <div className="lp-sxr-score">
                    <span className="lp-sxr-muted">
                      Speaking band <span className="lp-sxr-sample">Sample</span>
                    </span>
                    <span className="lp-sxr-bandnum">6.5</span>
                    <ul className="lp-sxr-crit">
                      {[
                        ['Fluency & Coherence', '6.0'],
                        ['Lexical Resource', '6.5'],
                        ['Grammar', '6.0'],
                        ['Pronunciation', '7.0'],
                      ].map(([l, b]) => (
                        <li key={l} style={vars({ '--f': (Number(b) / 9).toFixed(3) })}>
                          <span>{l}</span>
                          <b>{b}</b>
                          <i />
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div className="lp-sxr-first">
                    <p className="lp-sxr-sub">What to work on first</p>
                    {(
                      [
                        ['g', 'Grammar', 'Present tense in a past story', 'go → went, have → had'],
                        ['f', 'Fluency', 'A 1.4s pause in the middle of a phrase', 'after “the, the, um”'],
                        ['p', 'Pronunciation', '/θ/ said as /t/ in 3 words', 'three, through, think'],
                      ] as const
                    ).map(([k, c, what, ex]) => (
                      <div key={k} className="lp-sxr-item">
                        <Shape k={k} size={12} />
                        <span>
                          <span className="lp-sxr-muted">{c}</span>
                          <b>{what}</b>
                          <span className="lp-sxr-muted">{ex}</span>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Transcript */}
                <div className="lp-sxr-body lp-sxr-tr" data-on={step === 1 || undefined}>
                  <p className="lp-sxr-h">Transcript</p>
                  <div>
                    <div className="lp-sxr-filters">
                      <b>All</b>
                      {(['g', 'v', 'p', 'f'] as const).map((k) => (
                        <span key={k}>
                          <Shape k={k} />
                          {KIND[k]} <i>{TRANSCRIPT.flat().filter((t) => typeof t === 'object' && 'm' in t && t.m === k).length}</i>
                        </span>
                      ))}
                      <span>
                        Pauses <i>2</i>
                      </span>
                    </div>
                    <Transcript />
                  </div>
                  <div className="lp-sxr-note">
                    <span className="lp-sxr-notehead">
                      <Shape k="f" size={12} /> Fluency <span className="lp-sxr-muted lp-mono">0:33</span>
                    </span>
                    <b>Pause of 1.4 seconds, mid-clause</b>
                    <span className="lp-sxr-muted">After a repeat (“the, the”) and a filler (“um”). A pause inside a phrase breaks the flow more than one between ideas.</span>
                    <span className="lp-sxr-pill">
                      <Play size={12} aria-hidden /> Hear it
                    </span>
                  </div>
                </div>

                {/* Fluency */}
                <div className="lp-sxr-body lp-sxr-fl" data-on={step === 2 || undefined}>
                  <p className="lp-sxr-h">Fluency</p>
                  <div>
                    <p className="lp-sxr-sub">
                      Pace <span className="lp-sxr-muted">words per minute, every 5 seconds</span>
                    </p>
                    <PaceChart />
                  </div>
                  <div className="lp-sxr-measures">
                    <p className="lp-sxr-sub">Key measures</p>
                    {MEASURES.map(([l, v, s]) => (
                      <div key={l} className="lp-sxr-row">
                        <span>{l}</span>
                        <b className="lp-mono">{v}</b>
                        <span className="lp-sxr-status" data-s={s}>
                          {s}
                        </span>
                      </div>
                    ))}
                    <p className="lp-sxr-sub lp-sxr-gap">Fillers, repeats and restarts</p>
                    <div className="lp-sxr-chips">
                      <span>
                        um <i>×3</i>
                      </span>
                      <span>
                        Repeats <i>×2</i>
                      </span>
                      <span>
                        Restarts <i>×1</i>
                      </span>
                    </div>
                  </div>
                </div>

                {/* Language */}
                <div className="lp-sxr-body lp-sxr-la" data-on={step === 3 || undefined}>
                  <p className="lp-sxr-h">Language</p>
                  <div>
                    <p className="lp-sxr-sub">
                      Mistakes by type <span className="lp-sxr-muted">3 mistakes in 2 types</span>
                    </p>
                    {(
                      [
                        ['g', 'Grammar: tense', 2, 'Last winter I go → went · if I have → had'],
                        ['v', 'Vocabulary: repetition', 1, 'very very beautiful'],
                      ] as const
                    ).map(([k, t, n, ex]) => (
                      <div key={t} className="lp-sxr-type" style={vars({ '--n': n / 3 })}>
                        <span>
                          <Shape k={k} /> {t}
                        </span>
                        <b>{n}</b>
                        <i />
                        <span className="lp-sxr-muted">{ex}</span>
                      </div>
                    ))}
                    <p className="lp-sxr-sub lp-sxr-gap">Did you answer the question?</p>
                    <p className="lp-sxr-dev">
                      <span>
                        <b>Part 2: On topic</b> · Partly developed
                      </span>
                      <span className="lp-sxr-muted">Good start. Extend your reason with an example, a comparison or a consequence.</span>
                    </p>
                  </div>
                  <div>
                    <p className="lp-sxr-sub">Vocabulary upgrades</p>
                    {[
                      ['very very beautiful', 'breathtaking'],
                      ['going through', 'winding through'],
                      ['really early', 'at first light'],
                    ].map(([a, b]) => (
                      <p key={a} className="lp-sxr-up">
                        <del>{a}</del>
                        <ArrowRight size={14} aria-hidden />
                        <ins>{b}</ins>
                      </p>
                    ))}
                  </div>
                </div>

                {/* Pronunciation */}
                <div className="lp-sxr-body lp-sxr-pr" data-on={step === 4 || undefined}>
                  <p className="lp-sxr-h">Pronunciation</p>
                  <div>
                    <div className="lp-sxr-stats">
                      <span>
                        <b className="lp-mono">86</b>
                        <span className="lp-sxr-muted">/ 100 sounds said as expected</span>
                      </span>
                      <span>
                        <b className="lp-mono">3</b>
                        <span className="lp-sxr-muted">words to practise</span>
                      </span>
                    </div>
                    <p className="lp-sxr-sub lp-sxr-gap">Sounds you mix up most</p>
                    <div className="lp-sxr-swap">
                      <span className="lp-sxr-ipa">/θ/</span>
                      <span className="lp-sxr-muted">said as</span>
                      <span className="lp-sxr-ipa">/t/</span>
                      <b>×3</b>
                    </div>
                    <p className="lp-sxr-muted lp-sxr-small">Only sounds you swapped more than once. A single slip is not a pattern, and an accent is not an error.</p>
                  </div>
                  <div>
                    <p className="lp-sxr-sub">Words to practise</p>
                    {[
                      ['think', '/tɪŋk/', '/θɪŋk/'],
                      ['three', '/triː/', '/θriː/'],
                      ['through', '/truː/', '/θruː/'],
                    ].map(([w, you, ok]) => (
                      <div key={w} className="lp-sxr-word">
                        <b>{w}</b>
                        <span className="lp-sxr-pill">
                          <Play size={11} aria-hidden /> You <span className="lp-mono">{you}</span>
                        </span>
                        <span className="lp-sxr-pill lp-sxr-pill--ok">
                          <Play size={11} aria-hidden /> Correct <span className="lp-mono">{ok}</span>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Improve */}
                <div className="lp-sxr-body lp-sxr-im" data-on={step === 5 || undefined}>
                  <p className="lp-sxr-h">Improve</p>
                  <Better
                    marks={7}
                    className="lp-sxr-better"
                    better={{ lead: 'Your answer with the slips fixed and the ideas extended. Study what changed and why. Don’t memorise it.', paras: BETTER }}
                  />
                  <div className="lp-sxr-changed">
                    <p className="lp-sxr-sub">What changed</p>
                    {(
                      [
                        ['g', 'Grammar', '2 slips fixed', 'went, had'],
                        ['v', 'Vocabulary', '5 phrases upgraded', 'breathtaking, at first light'],
                        ['f', 'Fluency', 'Repeat, filler and pause gone', 'with a river winding through it'],
                        ['p', 'Pronunciation', 'Say it with a clear /θ/', 'think, three, through'],
                      ] as const
                    ).map(([k, c, what, ex]) => (
                      <div key={k} className="lp-sxr-item">
                        <Shape k={k} size={12} />
                        <span>
                          <span className="lp-sxr-muted">{c}</span>
                          <b>{what}</b>
                          <span className="lp-sxr-muted">{ex}</span>
                        </span>
                      </div>
                    ))}
                    <span className="lp-sxr-pill lp-sxr-pill--ok">
                      <BookmarkPlus size={13} aria-hidden /> Add top fixes to review deck
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </Stage>
      <div className="lp-container lp-act-cta lp-act-cta--centre lp-sxr-cta">
        <Link to="/speaking/live" className="lp-btn lp-btn--primary">
          Take a Speaking test free
        </Link>
      </div>
    </section>
  );
}
