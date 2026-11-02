import { Link } from '@tanstack/react-router';
import { SectionHead } from '../SectionHead';
import { Stage } from '../Stage';
import { Pen, vars } from '../wire';
import './03b-practice.css';

/*
 * The Reading act, built like the Listening act above it and drawn from the real screens (components/lr). It borrows
 * that act's desk, card, result and detail classes (lp-lx-*, 03-address.css) and their step rules; what is Reading's
 * own is lp-rx-*. Pinned, 4 steps:
 *   0  the exam: passage beside the questions, a highlight on the passage, answers chosen one by one, the navigator.
 *   1  the result slides over it: a sample band 8.0 (35 of 40), marks by question type, why marks were lost.
 *   2  the passage review rises on top: question 3, True/False/Not Given, missed (chose TRUE, the answer is NOT GIVEN).
 *   3  question 5, sentence completion, a distractor (wrote "daylight", the answer is "dawn"): the answer and the
 *      distractor marked apart in the passage, how the question rewords it.
 * Base CSS is step 3. All passage and question text is written for this page (Calder is our fictional river).
 */

const TFNG = [
  { n: 1, text: 'Boat traffic changed where the otters lived.', chose: 'TRUE' },
  { n: 2, text: 'Otters disappeared from the river completely.', chose: 'FALSE' },
  { n: 3, text: 'The boat lanes were moved by the town council.', chose: 'TRUE', wrong: true },
];
const GAPS = [
  { n: 4, before: 'Fishing nets and boat', typed: 'traffic', after: 'drove otters into side channels.' },
  { n: 5, before: 'Otter families were first seen near the town at', typed: 'daylight', after: '.', wrong: true },
];
// 35 of 40: wrong are 3 and 5 here, then 22 (summary) and 27, 33 (multiple choice) in later passages.
const TYPES = [
  { label: 'True / False / Not Given', right: 12, total: 13 },
  { label: 'Sentence completion', right: 6, total: 7 },
  { label: 'Summary completion', right: 9, total: 10 },
  { label: 'Multiple choice', right: 8, total: 10 },
];
const WRONG = new Set([3, 5]);

function ExamSheet() {
  return (
    <div className="lp-card lp-lx-exam lp-rx-exam">
      <div className="lp-lx-bar">
        <span className="lp-lx-label">Reading</span>
        <span className="lp-lx-where">Passage 1 of 3</span>
        <span className="lp-rx-aa">Aa</span>
        <span className="lp-lx-clock">
          <span className="lp-lx-cap">Time left</span> 41:20
        </span>
      </div>
      <div className="lp-rx-split">
        <div className="lp-rx-passage">
          <p className="lp-rx-ptitle">Otters return to the Calder</p>
          <p>
            <b>A</b> For most of the last century, otters were <mark className="lp-rx-hl">rarely seen</mark> on the lower Calder. Fishing nets and busy boat
            traffic pushed them into quieter side channels.
          </p>
          <p>
            <b>B</b> When the boat lanes were moved in 2009, families began to appear near the town again, first at dawn and later in daylight.
          </p>
          <p className="lp-rx-more">
            <b>C</b> Volunteers now count them each spring, walking the banks between the weir and the harbour.
          </p>
        </div>
        <div className="lp-rx-questions">
          <p className="lp-rx-qhead">Questions 1 to 3</p>
          <p className="lp-lx-cap">TRUE, FALSE or NOT GIVEN</p>
          <ol>
            {TFNG.map((q, i) => (
              <li key={q.n} className="lp-rx-q" style={vars({ '--i': i })}>
                <b>{q.n}</b>
                <span>
                  {q.text}
                  <span className="lp-rx-tf" data-wrong={q.wrong || undefined}>
                    {q.chose}
                  </span>
                </span>
              </li>
            ))}
          </ol>
          <p className="lp-rx-qhead lp-rx-qhead--2">Questions 4 and 5</p>
          <p className="lp-lx-cap">Complete the sentences. Choose ONE WORD ONLY from the passage.</p>
          <ol>
            {GAPS.map((q, i) => (
              <li key={q.n} className="lp-rx-q" style={vars({ '--i': i + 3 })}>
                <b>{q.n}</b>
                <span className="lp-rx-sent">
                  {q.before}{' '}
                  <span className="lp-rx-gap">
                    <span className="lp-rx-tf lp-rx-typed" data-wrong={q.wrong || undefined}>
                      {q.typed}
                    </span>
                  </span>
                  {q.after}
                </span>
              </li>
            ))}
          </ol>
        </div>
      </div>
      <div className="lp-lx-nav">
        <span className="lp-lx-nums">
          {Array.from({ length: 13 }, (_, i) => (
            <i key={i} data-on={i < 5 || undefined} data-cur={i === 4 || undefined} data-flag={i === 6 || undefined}>
              {i + 1}
            </i>
          ))}
        </span>
        <span className="lp-lx-part">
          <b>Passage 2</b> 0/13
        </span>
        <span className="lp-lx-part">
          <b>Passage 3</b> 0/14
        </span>
      </div>
    </div>
  );
}

function ResultCard() {
  return (
    <div className="lp-card lp-lx-result">
      <span className="lp-lx-cap">Academic Reading · 3 passages</span>
      {/* A sample band, labelled as one: 35 of 40 is band 8.0 on the Academic Reading scale (35 to 36). */}
      <div className="lp-lx-band">
        <span className="lp-lx-cap">
          Band <span className="lp-lx-sample">Sample</span>
        </span>
        <span className="lp-lx-bandnum">8.0</span>
        <span className="lp-lx-raw">35 of 40 correct</span>
      </div>
      <ul className="lp-rx-types">
        {TYPES.map((t) => (
          <li key={t.label} style={vars({ '--f': (t.right / t.total).toFixed(3) })}>
            <span>{t.label}</span>
            <span className="lp-rx-tnum">
              {t.right}/{t.total}
            </span>
            <i />
          </li>
        ))}
      </ul>
      <span className="lp-lx-btn">Review your 5 mistakes</span>
      <ul className="lp-lx-causes">
        <li>
          <span>Missed the answer</span>
          <span className="lp-rx-chips">
            <i className="lp-lx-qchip">3</i>
            <i className="lp-lx-qchip">33</i>
          </span>
        </li>
        <li>
          <span>Picked a distractor</span>
          <span className="lp-rx-chips">
            <i className="lp-lx-qchip">5</i>
            <i className="lp-lx-qchip">27</i>
          </span>
        </li>
        <li>
          <span>Right idea, lost the mark</span>
          <i className="lp-lx-qchip">22</i>
        </li>
      </ul>
    </div>
  );
}

function Review() {
  return (
    <div className="lp-card lp-lx-review lp-rx-review">
      <div className="lp-lx-tabs">
        <span>Summary</span>
        <span>
          Answers <i className="lp-lx-count">5</i>
        </span>
        <span className="lp-lx-tab-on">Passage</span>
      </div>
      {/* Answer marks along Passage 1, as the Answers list shows them: right round, wrong square in the pen colour. */}
      <div className="lp-rx-keys">
        <span className="lp-lx-cap">Passage 1</span>
        {Array.from({ length: 13 }, (_, i) => (
          <i key={i} className={`lp-rx-key lp-rx-key--${i + 1}`} data-wrong={WRONG.has(i + 1) || undefined}>
            {i + 1}
          </i>
        ))}
      </div>
      <div className="lp-lx-body">
        <div className="lp-rx-text">
          <p>
            <b>A</b> For most of the last century, otters were rarely seen on the lower Calder. Fishing nets and busy boat traffic pushed them into
            quieter side channels.
          </p>
          <p>
            <b>B</b> <mark className="lp-rx-ev lp-rx-ev--3">When the boat lanes were moved in 2009</mark>, families began to appear near the town again,{' '}
            <mark className="lp-rx-ev lp-rx-ev--5">first at dawn</mark> and <span className="lp-rx-trap">later in daylight</span>.
          </p>
          <p className="lp-lx-cap lp-rx-legend">
            <span className="lp-rx-ev lp-rx-ev--5">the answer</span> <span className="lp-rx-trap">the distractor</span>
          </p>
        </div>
        <div className="lp-lx-details">
          <div className="lp-lx-detail lp-rx-detail--3">
            <p className="lp-lx-dq">Question 3 · Missed the answer</p>
            <p className="lp-lx-cap">True / False / Not Given</p>
            <p>
              You chose <s>TRUE</s>. Answer <b>NOT GIVEN</b>.
            </p>
            <p className="lp-rx-bh">Why TRUE is not right</p>
            <p className="lp-lx-dwhy">The lanes were moved, but no sentence says who moved them. The town council is a guess, not the passage.</p>
            <p className="lp-rx-rule">
              <b>NOT GIVEN</b> The passage never settles it, whatever you know yourself.
            </p>
          </div>
          <div className="lp-lx-detail lp-rx-detail--5">
            <p className="lp-lx-dq">Question 5 · Picked a distractor</p>
            <p className="lp-lx-cap">Sentence completion</p>
            <p>
              You wrote <s>daylight</s>. Answer <b>dawn</b>.
            </p>
            <p className="lp-rx-bh">Why "daylight" is not right</p>
            <p className="lp-lx-dwhy">It is in the same sentence, but it is when they were seen later. The question asks when they were first seen.</p>
            <p className="lp-rx-bh">How the question is reworded</p>
            <p className="lp-rx-para">
              <span>first seen near the town</span>
              <span className="lp-lx-cap">=</span>
              <span>began to appear near the town, first</span>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export function Practice() {
  return (
    <section id="practice" aria-labelledby="practice-title" className="lp-practice">
      <Stage steps={4} track="190svh" className="lp-track--mobile-flat" play>
        <div className="lp-container lp-lx-frame">
          <SectionHead
            id="practice-title"
            title={
              <>
                Read against the clock. Find <Pen>the line</Pen> you missed.
              </>
            }
            sub="Reading runs like the real test: three passages in sixty minutes, the questions beside the text. Your result shows the sentence that held each answer, and how the question reworded it."
          />
          <p className="sr-only">
            Illustration: a Reading exam with Passage 1 beside True, False or Not Given questions 1 to 4. The result slides over it: a sample band
            of 8.0, 35 of 40 correct, with marks by question type. The passage review then marks the sentence behind each mistake: question 3 was
            NOT GIVEN because the passage never says who moved the boat lanes, and question 5 was "dawn", not the distractor "daylight", because the
            question asks when the otters were first seen.
          </p>
          <div className="lp-lx-desk" aria-hidden="true" data-lp-play>
            <ExamSheet />
            <ResultCard />
            <Review />
          </div>
        </div>
      </Stage>
      <div className="lp-container lp-act-cta lp-practice-cta">
        <Link to="/reading" className="lp-btn lp-btn--primary">
          Take a Reading test free
        </Link>
        <Link to="/listening" className="lp-link">
          or a Listening test
        </Link>
        <p className="lp-label">10 Reading and 10 Listening tests with no account.</p>
      </div>
    </section>
  );
}
