import { Link } from '@tanstack/react-router';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { BandQ, Pen, vars, WireCard, WirePill } from '../wire';
import './02-mock.css';

// The run order and timings, from lib/mock.ts SECTION_TIME and the Speaking choice (SpeakingChoice.tsx).
const NODES = [
  ['L', 'Listening', '4 parts, about 30 min'],
  ['R', 'Reading', '3 passages, 60 min'],
  ['W', 'Writing', 'Task 1 and 2, 60 min'],
  ['S', 'Speaking', 'Recorded, live or later'],
] as const;

// Wording from lib/mock.ts transitionCopy + SECTION_TIME; pills from Transition.tsx and SpeakingChoice.tsx.
const CARDS = [
  { title: 'Ready for Listening?', lines: ['Next: Listening, about 30 minutes.', 'The recording plays once, like the real test.'], pills: ['Start Listening'], ink: 0 },
  { title: 'Listening finished.', lines: ['Next: Reading, 60 minutes.', 'Three passages. Your answers save as you go.'], pills: ['Start Reading'], ink: 0 },
  { title: 'Reading finished.', lines: ['Next: Writing, 60 minutes.', 'Task 1 and Task 2 on one 60-minute clock.'], pills: ['Start Writing'], ink: 0 },
  { title: 'Writing finished. Choose how to do Speaking.', lines: ['Speaking has no countdown, so you can take it now or later this week.'], pills: ['Recorded test', 'Live examiner', 'Do it later'], ink: 1 },
];

const node = (k: number, s: number) => (s === 4 || k < s ? 'done' : k === s ? 'active' : 'todo');
const card = (k: number, s: number) => {
  const a = Math.min(s, 3);
  return k < a ? 'past' : k === a ? 'active' : k === a + 1 ? 'next' : 'later';
};

// A sample result (illustration, labelled as one): each section's band shows as it ends, the overall once all four are
// in. Overall = the mean of the four rounded to the nearest half band, as IELTS does: (7 + 6.5 + 6 + 6.5) / 4 = 6.5.
const BANDS = ['7.0', '6.5', '6.0', '6.5'] as const;
const OVERALL = '6.5';

/** One sitting, four skills: ink runs L to S through the pin (a timed play on phones), then the four bands land and the Overall "?.?" turns from grey to ink. */
export function Mock() {
  return (
    <section id="mock" aria-labelledby="mock-title" className="lp-mock">
      <Stage steps={5} track="175svh" className="lp-track--mobile-flat" play>
        {(s) => (
                    <div className="lp-container lp-mock-inner">
            <SectionHead id="mock-title" layout="centred" title={<>Sit the whole test in <Pen>one go</Pen>.</>} sub="Listening, Reading, Writing and Speaking, with a band for each and one overall." />
            <p className="sr-only">
              Illustration: a Listening card ends and the next card offers Start Reading. A sample result fills in as each section ends: Listening 7.0, Reading 6.5, Writing 6.0, Speaking 6.5, overall 6.5. In the mock, Speaking can be live or recorded.
            </p>
            <div className="lp-mock-grid" aria-hidden="true">
              <ol className="lp-mock-track">
                {NODES.map(([l, name, time], k) => (
                  <li key={l} className="lp-mock-col" style={vars({ '--order': k })}>
                    {k > 0 && (
                      <span className="lp-mock-seg" data-k={k} data-lp={s >= k ? 'on' : 'off'}>
                        <i />
                      </span>
                    )}
                    <span className="lp-mock-node" data-lp={node(k, s)}>
                      <span className="lp-mock-letter">{l}</span>
                    </span>
                    <span className="lp-mock-cap" data-lp={node(k, s)}>
                      {name}
                    </span>
                    <span className="lp-mock-time">{time}</span>
                    <span className="lp-mock-npill" style={vars({ '--i': k })} data-lp={s === 4 ? 'merged' : node(k, s) === 'done' ? 'on' : 'off'} />
                  </li>
                ))}
              </ol>
              <div className="lp-mock-deck">
                {CARDS.map((c, k) => (
                  // WireCard does not forward data-*, so the deck state lives on this wrapper.
                  <div key={k} className="lp-mock-card" data-k={k} data-lp={card(k, s)} style={vars({ '--k': k })}>
                    <WireCard className="lp-mock-cardbody">
                      <p className="type-heading">{c.title}</p>
                      {c.lines.map((l, i) => (
                        <p key={l} className={i ? 'lp-mock-line lp-mock-line--2' : 'lp-mock-line'}>
                          {l}
                        </p>
                      ))}
                      <span className="lp-mock-pills">
                        {c.pills.map((p, i) => (
                          <WirePill key={p} ink={i === c.ink} className="lp-mock-pill">
                            {p}
                          </WirePill>
                        ))}
                      </span>
                    </WireCard>
                  </div>
                ))}
              </div>
              <div className="lp-mock-slot" data-lp={s === 4 ? 'focus' : 'wait'}>
                <span className="lp-label lp-mock-slotcap">
                  Overall band <span className="lp-mock-sample">Sample</span>
                </span>
                {s === 4 ? <span className="lp-mock-glyph lp-mock-score">{OVERALL}</span> : <BandQ className="lp-mock-glyph" />}
                {/* MockResult in miniature: a band for each section, "?.?" until that section ends, then its sample band and bar. */}
                <span className="lp-mock-mini">
                  {NODES.map(([l, name], i) => {
                    const on = s === 4 || i < s;
                    return (
                      <span key={l} className="lp-mock-band" data-lp={on ? 'on' : 'off'} style={vars({ '--i': i, '--f': Number(BANDS[i]) / 9 })}>
                        <span className="lp-mock-bname">{name}</span>
                        {on ? <span className="lp-mock-bq lp-mock-bnum">{BANDS[i]}</span> : <BandQ className="lp-mock-bq" />}
                        <i className="lp-mock-bar" />
                      </span>
                    );
                  })}
                </span>
              </div>
            </div>
            <div className="lp-act-cta lp-mock-cta">
              <Link to="/mock" className="lp-btn lp-btn--secondary">
                Start a full mock
              </Link>
              <p className="lp-label">Needs a free account, so your four bands stay together.</p>
            </div>
          </div>
        )}
      </Stage>
    </section>
  );
}
